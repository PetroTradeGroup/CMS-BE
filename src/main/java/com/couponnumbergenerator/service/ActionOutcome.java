package com.couponnumbergenerator.service;

import com.couponnumbergenerator.dto.response.ApprovalRequestResponse;

/**
 * Result of a lifecycle action that may either apply immediately or be deferred for
 * supervisor approval. Lets callers (controllers) branch cleanly between a 200-applied
 * and a 202-pending response without resorting to wildcard/Object return types.
 */
public sealed interface ActionOutcome<T> {

    record Applied<T>(T result) implements ActionOutcome<T> {}

    record Pending<T>(ApprovalRequestResponse request) implements ActionOutcome<T> {}
}
