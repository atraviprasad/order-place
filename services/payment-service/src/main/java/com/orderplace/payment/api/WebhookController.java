package com.orderplace.payment.api;

import com.orderplace.payment.model.ProcessingResult;
import com.orderplace.payment.service.WebhookService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoint called by Razorpay (no token). The body is read as raw bytes because the signature is computed over
 * exactly those bytes. Returns 200 whenever the signature is valid, including for duplicates and events we ignore,
 * so the gateway does not keep retrying them.
 */
@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private final WebhookService service;

    public WebhookController(WebhookService service) {
        this.service = service;
    }

    @PostMapping("/razorpay")
    public ResponseEntity<Map<String, String>> razorpay(
            @RequestBody byte[] body,
            @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(name = "X-Razorpay-Event-Id", required = false) String eventId) {
        ProcessingResult result = service.process(body, signature, eventId);
        return ResponseEntity.ok(Map.of("result", result.name()));
    }
}
