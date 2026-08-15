package dev.learningops.snapshot;

import java.time.Instant;

public record TenantAccount(String tenantId, String courseCatalog, Status status,
                            boolean onboardingComplete, Instant updatedAt) {
    public enum Status { ACTIVE, SUSPENDED, CLOSED }
}
