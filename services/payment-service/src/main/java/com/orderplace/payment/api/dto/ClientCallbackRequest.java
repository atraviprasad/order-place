package com.orderplace.payment.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The three values Razorpay Checkout hands to the browser after a payment attempt. */
public record ClientCallbackRequest(
        @NotBlank @Size(max = 60) String razorpayPaymentId,
        @NotBlank @Size(max = 60) String razorpayOrderId,
        @NotBlank @Size(max = 200) String razorpaySignature) {}
