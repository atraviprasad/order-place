package com.orderplace.dealer.mapper;

import com.orderplace.dealer.api.dto.DealerResponse;
import com.orderplace.dealer.api.dto.DealerSummary;
import com.orderplace.dealer.model.Dealer;

public final class DealerMapper {
    private DealerMapper() {}

    public static DealerResponse toResponse(Dealer d) {
        return new DealerResponse(d.getId(), d.getDealerCode(), d.getName(), d.getContactPerson(), d.getEmail(),
                d.getPhone(), d.getAddress(), d.getTaxRegistrationNo(), d.isActive(), d.getVersion(),
                d.getCreatedAt(), d.getUpdatedAt());
    }

    public static DealerSummary toSummary(Dealer d) {
        return new DealerSummary(d.getId(), d.getDealerCode(), d.getName(),d.getEmail());
    }
}
