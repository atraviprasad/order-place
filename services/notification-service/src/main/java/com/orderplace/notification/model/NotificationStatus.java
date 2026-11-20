package com.orderplace.notification.model;

/** PENDING = not sent yet (or being retried), SENT = delivered to the mail server, FAILED = gave up. */
public enum NotificationStatus { PENDING, SENT, FAILED }
