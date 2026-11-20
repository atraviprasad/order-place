package com.orderplace.order.service;

import com.orderplace.common.events.EventTypes;
import com.orderplace.common.security.TenantContext;
import com.orderplace.common.web.ApiException;
import com.orderplace.common.web.PagedResponse;
import com.orderplace.order.api.dto.CancelRequest;
import com.orderplace.order.api.dto.CreateOrderRequest;
import com.orderplace.order.api.dto.HistoryEntry;
import com.orderplace.order.api.dto.OrderLineRequest;
import com.orderplace.order.api.dto.OrderResponse;
import com.orderplace.order.api.dto.OrderSummary;
import com.orderplace.order.api.dto.SaveOrderRequest;
import com.orderplace.order.client.CatalogClient;
import com.orderplace.order.client.CustomerClient;
import com.orderplace.order.client.CustomerInfo;
import com.orderplace.order.client.DealerClient;
import com.orderplace.order.client.DealerInfo;
import com.orderplace.order.client.ProductInfo;
import com.orderplace.order.model.CustomerOrder;
import com.orderplace.order.model.LineSpec;
import com.orderplace.order.model.OrderLine;
import com.orderplace.order.model.OrderStatus;
import com.orderplace.order.model.OrderStatusHistory;
import com.orderplace.order.mapper.OrderMapper;
import com.orderplace.order.outbox.OutboxWriter;
import com.orderplace.order.repository.OrderRepository;
import com.orderplace.order.repository.OrderStatusHistoryRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer-side order use cases. Every method receives the caller's TenantContext (from the JWT); the customer
 * is always taken from there, never from the request, and another customer's order is reported as "not found".
 */
@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final CatalogClient catalog;
    private final DealerClient dealers;
    private final CustomerClient customers;
    private final OutboxWriter outbox;

    @Value("${orders.max-lines:50}")
    private int maxLines = 50;

    public OrderService(OrderRepository orders, OrderStatusHistoryRepository history, CatalogClient catalog,
                        DealerClient dealers, CustomerClient customers, OutboxWriter outbox) {
        this.orders = orders;
        this.history = history;
        this.catalog = catalog;
        this.dealers = dealers;
        this.customers = customers;
        this.outbox = outbox;
    }

    @Transactional
    public OrderResponse createDraft(TenantContext ctx, CreateOrderRequest r) {
        requireCustomer(ctx);
        CustomerInfo customer = customers.me();
        if (!customer.active()) {
            throw ApiException.unprocessable("ORGANISATION_INACTIVE", "Your organisation is inactive");
        }
        CustomerOrder order = CustomerOrder.newDraft(ctx.orgId(), customer.name(), customer.email(), userId(ctx),
                blankToNull(r.deliveryAddress()) != null ? r.deliveryAddress().strip() : customer.deliveryAddress());
        applyDraft(order, r.dealerId(), order.getDeliveryAddress(), r.customerNotes(), r.lines());
        orders.saveAndFlush(order);
        history.save(OrderStatusHistory.of(order.getId(), null, OrderStatus.DRAFT, userId(ctx), "CUSTOMER", null));
        return OrderMapper.toResponse(order);
    }

    @Transactional
    public OrderResponse updateDraft(TenantContext ctx, UUID id, SaveOrderRequest r) {
        CustomerOrder order = find(ctx, id);
        order.requireDraft();
        checkVersion(order, r.version());
        applyDraft(order, r.dealerId(), r.deliveryAddress(), r.customerNotes(), r.lines());
        return OrderMapper.toResponse(orders.saveAndFlush(order));
    }

    @Transactional
    public void deleteDraft(TenantContext ctx, UUID id) {
        CustomerOrder order = find(ctx, id);
        order.requireDraft();
        orders.delete(order);
    }

    public OrderResponse get(TenantContext ctx, UUID id) {
        return OrderMapper.toResponse(find(ctx, id));
    }

    public PagedResponse<OrderSummary> list(TenantContext ctx, String q, OrderStatus status, UUID dealerId,
                                            LocalDate from, LocalDate to, Pageable pageable) {
        requireCustomer(ctx);
        var filter = new OrderSpecs.Filter(ctx.orgId(), dealerId, status, q, from, to, false);
        Page<CustomerOrder> page = orders.findAll(OrderSpecs.of(filter), pageable);
        return new PagedResponse<>(page.getContent().stream().map(OrderMapper::toSummary).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public List<HistoryEntry> history(TenantContext ctx, UUID id) {
        CustomerOrder order = find(ctx, id);
        return history.findByOrderIdOrderByChangedAtAsc(order.getId()).stream().map(OrderMapper::toHistory).toList();
    }

    /**
     * Validates everything again and freezes prices. Repeating the call with the same Idempotency-Key (for example
     * after a network timeout) returns the already submitted order instead of an error.
     */
    @Transactional
    public OrderResponse submit(TenantContext ctx, UUID id, String idempotencyKey, Long version) {
        CustomerOrder order = find(ctx, id);
        if (order.getStatus() != OrderStatus.DRAFT) {
            if (idempotencyKey.equals(order.getSubmitIdempotencyKey())) {
                return OrderMapper.toResponse(order);
            }
            throw ApiException.conflict("INVALID_STATE_TRANSITION", "This order is already " + order.getStatus());
        }
        checkVersion(order, version);
        if (order.getDealerId() == null) throw ApiException.badRequest("Choose a dealer before submitting");
        if (blankToNull(order.getDeliveryAddress()) == null) throw ApiException.badRequest("A delivery address is required");
        if (order.getLines().isEmpty()) throw ApiException.badRequest("Add at least one product before submitting");

        DealerInfo dealer = dealers.requireActive(order.getDealerId());
        order.updateDealerSnapshot(dealer.name(), dealer.email());

        List<LineSpec> fresh = new ArrayList<>();
        for (OrderLine line : order.getLines()) {
            ProductInfo p = catalog.get(line.getProductId());
            if (!p.active() || !p.available()) {
                throw ApiException.unprocessable("PRODUCT_UNAVAILABLE", p.name() + " (" + p.sku() + ") is not available");
            }
            if (line.getSubmittedQty() < p.minOrderQty()) {
                throw ApiException.unprocessable("BELOW_MIN_QTY",
                        p.name() + " (" + p.sku() + ") needs a minimum quantity of " + p.minOrderQty());
            }
            fresh.add(new LineSpec(p.id(), p.sku(), p.name(), p.unitPrice(), line.getSubmittedQty()));
        }
        order.syncLines(fresh);

        String number = "OP-%d-%06d".formatted(Instant.now().atZone(ZoneOffset.UTC).getYear(), orders.nextOrderSequence());
        order.submit(number, idempotencyKey, Instant.now());
        orders.saveAndFlush(order);
        history.save(OrderStatusHistory.of(order.getId(), OrderStatus.DRAFT, OrderStatus.SUBMITTED, userId(ctx), "CUSTOMER", null));
        outbox.orderEvent(EventTypes.ORDER_SUBMITTED, order, null);
        return OrderMapper.toResponse(order);
    }

    @Transactional
    public OrderResponse cancel(TenantContext ctx, UUID id, CancelRequest r) {
        CustomerOrder order = find(ctx, id);
        checkVersion(order, r == null ? null : r.version());
        String reason = r == null ? null : blankToNull(r.reason());
        OrderStatus from = order.getStatus();
        order.cancel();
        orders.saveAndFlush(order);
        history.save(OrderStatusHistory.of(order.getId(), from, OrderStatus.CANCELLED, userId(ctx), "CUSTOMER", reason));
        outbox.orderEvent(EventTypes.ORDER_CANCELLED, order, reason);
        return OrderMapper.toResponse(order);
    }

    // ---- helpers

    private void applyDraft(CustomerOrder order, UUID dealerId, String address, String notes, List<OrderLineRequest> lines) {
        DealerInfo dealer = dealerId == null ? null : dealers.requireActive(dealerId);
        List<LineSpec> specs = buildSpecs(lines);
        order.saveDraft(dealerId, dealer == null ? null : dealer.name(), dealer == null ? null : dealer.email(),
                blankToNull(address), blankToNull(notes), specs);
    }

    private List<LineSpec> buildSpecs(List<OrderLineRequest> lines) {
        if (lines == null || lines.isEmpty()) return List.of();
        if (lines.size() > maxLines) throw ApiException.badRequest("An order can have at most " + maxLines + " lines");
        Set<UUID> seen = new HashSet<>();
        List<LineSpec> specs = new ArrayList<>();
        for (OrderLineRequest l : lines) {
            if (!seen.add(l.productId())) {
                throw ApiException.badRequest("The same product appears twice: " + l.productId());
            }
            ProductInfo p = catalog.get(l.productId());
            if (!p.active()) throw ApiException.unprocessable("PRODUCT_UNAVAILABLE", "Product is not available: " + p.name());
            specs.add(new LineSpec(p.id(), p.sku(), p.name(), p.unitPrice(), l.quantity()));
        }
        return specs;
    }

    private CustomerOrder find(TenantContext ctx, UUID id) {
        requireCustomer(ctx);
        return orders.findByIdAndCustomerId(id, ctx.orgId())
                .orElseThrow(() -> ApiException.notFound("Order not found: " + id));
    }

    private static void requireCustomer(TenantContext ctx) {
        if (!ctx.isCustomer() || ctx.orgId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only users of a customer organisation can do this");
        }
    }

    private static void checkVersion(CustomerOrder order, Long version) {
        if (version != null && !version.equals(order.getVersion())) {
            throw ApiException.conflict("VERSION_CONFLICT", "This order was changed by someone else. Reload and try again.");
        }
    }

    private static UUID userId(TenantContext ctx) {
        return UUID.fromString(ctx.userId());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
