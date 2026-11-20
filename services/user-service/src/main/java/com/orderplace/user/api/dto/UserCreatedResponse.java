package com.orderplace.user.api.dto;

/** If setPasswordEmailSent is false, the account exists: use POST /users/{id}/reset-password to send the email again. */
public record UserCreatedResponse(UserResponse user, boolean setPasswordEmailSent) {}
