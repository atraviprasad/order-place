package com.orderplace.user.mapper;

import com.orderplace.user.api.dto.CustomerResponse;
import com.orderplace.user.model.Customer;

public final class CustomerMapper {
    private CustomerMapper() {}

    public static CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(c.getId(), c.getCustomerCode(), c.getName(), c.getContactPerson(), c.getEmail(),
                c.getPhone(), c.getBillingAddress(), c.getDeliveryAddress(), c.isActive(), c.getVersion(),
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
