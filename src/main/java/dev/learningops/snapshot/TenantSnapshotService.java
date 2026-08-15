package dev.learningops.snapshot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TenantSnapshotService {
    private final Map<String, TenantAccount> accounts = new ConcurrentHashMap<>();
    private final SnapshotPolicy policy;
    private final InfraiStorageClient storage;
    private final ObjectMapper json;
    private final String bucket;
    private final Clock clock = Clock.systemUTC();

    public TenantSnapshotService(SnapshotPolicy policy, InfraiStorageClient storage, ObjectMapper json,
                                 @Value("${snapshot.bucket}") String bucket) {
        this.policy = policy;
        this.storage = storage;
        this.json = json;
        this.bucket = bucket;
    }

    @PostConstruct
    void prepareStorage() {
        storage.ensureBucket(bucket);
    }

    public TenantAccount onboard(TenantAccount account) {
        accounts.put(account.tenantId(), account);
        return account;
    }

    public TenantAccount changeStatus(String tenantId, TenantAccount.Status status) {
        return accounts.compute(tenantId, (id, current) -> {
            if (current == null) throw new TenantNotFoundException(id);
            return new TenantAccount(id, current.courseCatalog(), status,
                current.onboardingComplete(), current.updatedAt());
        });
    }

    public SnapshotReceipt snapshot(String tenantId) {
        TenantAccount account = accounts.get(tenantId);
        if (account == null) throw new TenantNotFoundException(tenantId);
        if (!policy.shouldSnapshot(account)) return new SnapshotReceipt(tenantId, "SKIPPED", null);
        String date = LocalDate.now(clock).toString();
        String key = "tenants/" + tenantId + "/" + date + ".json";
        URI url = storage.presignSnapshotPut(bucket, key, tenantId + ":" + date);
        storage.upload(url, encode(account));
        return new SnapshotReceipt(tenantId, "STORED", key);
    }

    @Scheduled(cron = "${snapshot.schedule}", zone = "UTC")
    public void snapshotEligibleTenants() {
        accounts.keySet().forEach(this::snapshot);
    }

    public Collection<TenantAccount> accounts() {
        return accounts.values();
    }

    private byte[] encode(TenantAccount account) {
        try {
            return json.writeValueAsString(account).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not encode tenant snapshot", e);
        }
    }

    public record SnapshotReceipt(String tenantId, String outcome, String objectKey) {}
    public static class TenantNotFoundException extends RuntimeException {
        public TenantNotFoundException(String tenantId) { super("Unknown tenant: " + tenantId); }
    }
}
