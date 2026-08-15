package dev.learningops.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SnapshotPolicyTest {
    private final SnapshotPolicy policy = new SnapshotPolicy();

    @Test
    void snapshotsOnlyActiveTenantsThatFinishedOnboarding() {
        TenantAccount ready = account(TenantAccount.Status.ACTIVE, true);
        TenantAccount suspended = account(TenantAccount.Status.SUSPENDED, true);
        TenantAccount stillOnboarding = account(TenantAccount.Status.ACTIVE, false);

        assertThat(policy.shouldSnapshot(ready)).isTrue();
        assertThat(policy.shouldSnapshot(suspended)).isFalse();
        assertThat(policy.shouldSnapshot(stillOnboarding)).isFalse();
    }

    private TenantAccount account(TenantAccount.Status status, boolean complete) {
        return new TenantAccount("academy-42", "teacher-certificates", status, complete, Instant.EPOCH);
    }
}
