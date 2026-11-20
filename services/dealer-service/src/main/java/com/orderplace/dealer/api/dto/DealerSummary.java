package com.orderplace.dealer.api.dto;

import java.util.UUID;

public record DealerSummary(UUID id, String dealerCode, String name, String contactEmail) {
}
