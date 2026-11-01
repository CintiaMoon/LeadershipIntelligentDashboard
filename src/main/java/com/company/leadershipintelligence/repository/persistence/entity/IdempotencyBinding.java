package com.company.leadershipintelligence.repository.persistence.entity;

/** An idempotency key bound to the request that first used it and the run that request produced. */
public record IdempotencyBinding(String requestHash, StoredRunRow run) {
}
