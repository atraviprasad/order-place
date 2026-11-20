package com.orderplace.user.mapper;

import com.orderplace.user.api.dto.UserResponse;
import com.orderplace.user.model.AppUser;

public final class UserMapper {
    private UserMapper() {}

    public static UserResponse toResponse(AppUser u, String orgName) {
        return new UserResponse(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getPhone(), u.getRole(),
                u.getOrgId(), orgName, u.isActive(), u.getVersion(), u.getCreatedAt(), u.getUpdatedAt());
    }
}
