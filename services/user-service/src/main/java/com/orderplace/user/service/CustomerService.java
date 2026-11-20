package com.orderplace.user.service;

import com.orderplace.common.web.ApiException;
import com.orderplace.common.web.PagedResponse;
import com.orderplace.user.api.dto.CreateCustomerRequest;
import com.orderplace.user.api.dto.CustomerResponse;
import com.orderplace.user.api.dto.UpdateCustomerRequest;
import com.orderplace.user.api.dto.UserResponse;
import com.orderplace.user.model.AppUser;
import com.orderplace.user.model.Customer;
import com.orderplace.user.keycloak.KeycloakAdminClient;
import com.orderplace.user.mapper.CustomerMapper;
import com.orderplace.user.mapper.UserMapper;
import com.orderplace.user.repository.AppUserRepository;
import com.orderplace.user.repository.CustomerRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);
    private static final String DUPLICATE = "DUPLICATE_RESOURCE";

    private final CustomerRepository customers;
    private final AppUserRepository users;
    private final KeycloakAdminClient keycloak;

    public CustomerService(CustomerRepository customers, AppUserRepository users, KeycloakAdminClient keycloak) {
        this.customers = customers;
        this.users = users;
        this.keycloak = keycloak;
    }

    @Transactional
    public CustomerResponse create(CreateCustomerRequest r) {
        String code = r.customerCode().strip().toUpperCase(Locale.ROOT);
        String name = r.name().strip();
        String email = r.email().strip().toLowerCase(Locale.ROOT);
        if (customers.existsByCustomerCodeIgnoreCase(code)) {
            throw ApiException.conflict(DUPLICATE, "Customer code already exists: " + code);
        }
        if (customers.existsByNameIgnoreCaseAndEmailIgnoreCase(name, email)) {
            throw ApiException.conflict(DUPLICATE, "A customer with this name and email already exists");
        }
        Customer customer = Customer.create(code, name, r.contactPerson().strip(), email, r.phone().strip(),
                r.billingAddress().strip(), r.deliveryAddress().strip());
        return CustomerMapper.toResponse(saveAndFlush(customer));
    }

    @Transactional
    public CustomerResponse update(UUID id, UpdateCustomerRequest r) {
        Customer customer = find(id);
        if (r.version() != null && !r.version().equals(customer.getVersion())) {
            throw ApiException.conflict("VERSION_CONFLICT", "This customer was changed by someone else. Reload and try again.");
        }
        String name = r.name().strip();
        String email = r.email().strip().toLowerCase(Locale.ROOT);
        if (customers.existsByNameIgnoreCaseAndEmailIgnoreCaseAndIdNot(name, email, id)) {
            throw ApiException.conflict(DUPLICATE, "A customer with this name and email already exists");
        }
        customer.updateDetails(name, r.contactPerson().strip(), email, r.phone().strip(),
                r.billingAddress().strip(), r.deliveryAddress().strip());
        return CustomerMapper.toResponse(saveAndFlush(customer));
    }

    /**
     * Blocks login for every active user of the customer. Keycloak is changed first: if something fails half way,
     * users stay locked out (safe) and the Admin can simply retry.
     */
    @Transactional
    public CustomerResponse deactivate(UUID id) {
        Customer customer = find(id);
        if (!customer.isActive()) {
            return CustomerMapper.toResponse(customer);
        }
        for (AppUser user : users.findByOrgIdAndActiveTrue(id)) {
            keycloak.setEnabled(user.getKeycloakUserId(), false);
        }
        customer.deactivate();
        return CustomerMapper.toResponse(saveAndFlush(customer));
    }

    /**
     * Re-enables login for users who are individually active. The database is updated first; if Keycloak fails
     * part way, the users already enabled are disabled again and the whole change is rolled back.
     */
    @Transactional
    public CustomerResponse activate(UUID id) {
        Customer customer = find(id);
        if (customer.isActive()) {
            return CustomerMapper.toResponse(customer);
        }
        customer.activate();
        saveAndFlush(customer);
        List<AppUser> enabled = new ArrayList<>();
        try {
            for (AppUser user : users.findByOrgIdAndActiveTrue(id)) {
                keycloak.setEnabled(user.getKeycloakUserId(), true);
                enabled.add(user);
            }
        } catch (RuntimeException e) {
            for (AppUser user : enabled) {
                try {
                    keycloak.setEnabled(user.getKeycloakUserId(), false);
                } catch (RuntimeException inner) {
                    log.error("CLEAN-UP NEEDED: Keycloak user {} is enabled but its customer is inactive",
                            user.getKeycloakUserId(), inner);
                }
            }
            throw e;
        }
        return CustomerMapper.toResponse(customer);
    }

    public CustomerResponse get(UUID id) {
        return CustomerMapper.toResponse(find(id));
    }

    public PagedResponse<CustomerResponse> list(String q, Boolean active, Pageable pageable) {
        String pattern = "%" + (q == null ? "" : q.strip().toLowerCase(Locale.ROOT)) + "%";
        Page<Customer> page = active == null
                ? customers.search(pattern, pageable)
                : customers.searchByActive(pattern, active, pageable);
        return new PagedResponse<>(page.getContent().stream().map(CustomerMapper::toResponse).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /** Users linked to a customer (for the customer detail screen). */
    public List<UserResponse> listUsers(UUID customerId) {
        Customer customer = find(customerId);
        return users.findByOrgIdOrderByLastNameAscFirstNameAsc(customerId).stream()
                .map(u -> UserMapper.toResponse(u, customer.getName())).toList();
    }

    private Customer find(UUID id) {
        return customers.findById(id).orElseThrow(() -> ApiException.notFound("Customer not found"));
    }

    private Customer saveAndFlush(Customer customer) {
        try {
            return customers.saveAndFlush(customer);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict(DUPLICATE, "A customer with this code or name and email already exists");
        }
    }
}
