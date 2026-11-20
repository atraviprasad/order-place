package com.orderplace.notification.admin;

import com.orderplace.common.web.PageParams;
import com.orderplace.common.web.PagedResponse;
import com.orderplace.notification.model.NotificationStatus;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Served under /api/v1. The only HTTP API of this service. */
@RestController
@RequestMapping("/admin/notifications")
@PreAuthorize("hasRole('ADMIN')")
public class AdminNotificationController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "eventType", "status", "recipient");

    private final AdminNotificationService service;

    public AdminNotificationController(AdminNotificationService service) {
        this.service = service;
    }

    @GetMapping
    public PagedResponse<NotificationResponse> list(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(required = false) String recipient,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return service.list(eventType, status, recipient, from, to, PageParams.of(page, size, sort, SORTABLE));
    }

    @PostMapping("/{id}/retry")
    public NotificationResponse retry(@PathVariable String id) {
        return service.retry(id);
    }
}
