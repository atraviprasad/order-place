package com.orderplace.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private CustomerOrder order;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "sku_snapshot", nullable = false, length = 40)
    private String sku;

    @Column(name = "product_name_snapshot", nullable = false, length = 200)
    private String productName;

    @Column(name = "unit_price_snapshot", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "submitted_qty", nullable = false)
    private int submittedQty;

    /** Set by the dealer. Null until the dealer acts; 0 means the line was removed. */
    @Column(name = "approved_qty")
    private Integer approvedQty;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;

    /** Dealer decision for this line (0 removes it). The line total now reflects the approved quantity. */
    void approve(int qty) {
        this.approvedQty = qty;
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));
    }

    static OrderLine create(CustomerOrder order, int lineNo, LineSpec spec) {
        OrderLine l = new OrderLine();
        l.order = order;
        l.productId = spec.productId();
        l.apply(lineNo, spec);
        return l;
    }

    void apply(int lineNo, LineSpec spec) {
        this.lineNo = lineNo;
        this.sku = spec.sku();
        this.productName = spec.productName();
        this.unitPrice = spec.unitPrice();
        this.submittedQty = spec.quantity();
        this.lineTotal = spec.unitPrice().multiply(BigDecimal.valueOf(spec.quantity()));
    }
}
