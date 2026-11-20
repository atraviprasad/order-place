package com.orderplace.dealer.api.dto;

import java.time.Instant;
import java.util.UUID;

public record DealerResponse(UUID id,
                             String dealerCode,
                             String name,
                             String contactPerson,
                             String email,
                             String phone,
                             String address,
                             String taxRegistrationNo,
                             boolean active,
                             Long version,
                             Instant createdAt,
                             Instant updatedAt) {
}
