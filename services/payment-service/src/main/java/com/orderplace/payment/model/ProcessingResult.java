package com.orderplace.payment.model;

/** What happened to a webhook call after its signature was accepted. */
public enum ProcessingResult { PROCESSED, IGNORED, UNMATCHED, AMOUNT_MISMATCH, DUPLICATE_SUCCESS }
