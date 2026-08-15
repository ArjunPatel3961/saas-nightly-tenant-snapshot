package dev.learningops.snapshot;

import org.springframework.stereotype.Component;

@Component
public class SnapshotPolicy {
    public boolean shouldSnapshot(TenantAccount account) {
        return account.onboardingComplete() && account.status() == TenantAccount.Status.ACTIVE;
    }
}
