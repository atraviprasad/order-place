package com.orderplace.dealer.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateDealerRequest(@NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{2,30}$", message = "must be 2 to 30 characters: letters, digits, - or _")
                                  String dealerCode,
                                  @NotBlank @Size(max = 200) String name,
                                  @NotBlank @Size(max = 150) String contactPerson,
                                  @NotBlank @Email @Size(max = 254) String email,
                                  @NotBlank @Size(max = 30) String phone,
                                  @NotBlank @Size(max = 1000) String address,
                                  @Size(max = 50) String taxRegistrationNo) {

}
