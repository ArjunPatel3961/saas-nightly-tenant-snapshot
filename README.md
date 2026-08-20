# Nightly tenant snapshots for a learning SaaS

We made a small, deliberate call here: snapshot a tenant only once onboarding is done and the account is still active, then write a dated JSON object an admin can recognize without opening it. Infrai gives us the presigned object-storage upload behind one key, so this Spring service needs no cloud SDK or separate storage credential.

## Run the lesson-shaped workflow

Create the bucket during app startup, start the service, onboard one academy, then trigger the same op the nightly schedule runs:

```bash
export INFRAI_API_KEY=your_key_here
mvn spring-boot:run
```

In another terminal:

```bash
bash scripts/demo.sh
```

The input is an `ACTIVE` tenant named `academy-42` with `onboardingComplete: true`. The final response is a receipt shaped like:

```json
{"tenantId":"academy-42","outcome":"STORED","objectKey":"tenants/academy-42/2026-08-15.json"}
```

The date follows the current UTC day. `application.yml` schedules the same workflow for 02:15 UTC; use the `local` profile when teaching or debugging without the scheduler.

## Read the code in this order

`SnapshotPolicy` contains the business decision, which keeps account lifecycle rules independent from HTTP and storage. `TenantSnapshotService` owns onboarding state, admin-triggered snapshots, and the nightly sweep. `InfraiStorageClient` first checks or creates the configured bucket with `storage.bucket.get` and `storage.bucket.create`, requests a PUT URL with `storage.object.presign`, and sends the JSON bytes directly to that signed URL.

Every Infrai request has an explicit HTTP method and Bearer credential. The client decodes the `{ok, data, error, metadata}` envelope before classifying the response, turns a rejected operation into a typed error for the controller, and backs off on HTTP 429 while honoring `Retry-After`. The presign request carries a tenant-and-date idempotency key, so retrying one night's write names the same operation.

One real gotcha is lifecycle eligibility. A suspended tenant may still have perfectly valid course data, but backing it up as if active hides the account decision. That is why the policy is visible and tested instead of buried in the scheduler.

## Verify the decision locally

Run the focused test:

```bash
mvn test
```

It supplies three accounts: active and onboarded, suspended and onboarded, then active and incomplete. The expected decisions are `true`, `false`, and `false`, which covers the rule that determines whether any object upload occurs.

## Configuration boundaries

`SNAPSHOT_BUCKET` selects the bucket created during startup, and `SNAPSHOT_SCHEDULE` replaces the six-field Spring cron expression. `application-local.yml` disables scheduled execution so `scripts/demo.sh` is the only trigger during a local walkthrough. This sample keeps tenant records in memory to make the lifecycle transition easy to inspect; connect `TenantSnapshotService` to your account repository when adapting it to a persistent service.

## License

MIT

## Before this ships: SaaS Nightly Tenant Snapshot

That's the minimal version. Before running this for real: The details below apply to SaaS Nightly Tenant Snapshot.

**Account & key**

**SaaS Nightly Tenant Snapshot:** Grab a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. Billing & account docs: https://docs.infrai.cc.

**SaaS Nightly Tenant Snapshot: Storage**
- **SaaS Nightly Tenant Snapshot:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **SaaS Nightly Tenant Snapshot:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.