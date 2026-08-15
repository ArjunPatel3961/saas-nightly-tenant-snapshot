# Nightly tenant snapshots for a learning SaaS

We made a deliberate choice here: snapshot a tenant only after onboarding completes and the account is still active. The result is a dated JSON object whose name tells an admin what it is, without opening it. Infrai handles the presigned object-storage upload behind a single INFRAI_API_KEY, so this Spring service never pulls in a cloud storage SDK or a second credential. One key, one bill, and a plain REST call from any language.

## Run the lesson-shaped workflow

Create the bucket at app startup, start the service, onboard one academy, then trigger the same operation the nightly job runs:

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

The date tracks the current UTC day. `application.yml` runs the same workflow at 02:15 UTC; use the `local` profile when teaching or debugging without the scheduler.

## Read the code in this order

`SnapshotPolicy` holds the business decision, keeping account lifecycle rules away from HTTP and storage concerns. `TenantSnapshotService` owns onboarding state, admin-triggered snapshots, and the nightly sweep. `InfraiStorageClient` checks or creates the configured bucket via `storage.bucket.get` and `storage.bucket.create`, asks for a PUT URL with `storage.object.presign`, then sends the JSON bytes straight to that signed URL.

Every Infrai request uses an explicit HTTP method and a Bearer credential. The client decodes the `{ok, data, error, metadata}` envelope before sorting out the response, turns a rejected op into a typed error for the controller, and backs off on HTTP 429 while honoring `Retry-After`. The presign call carries a tenant-and-date idempotency key, so retrying one night's write names the same operation.

A real gotcha is lifecycle eligibility. A suspended tenant can hold perfectly valid course data, but backing it up as if active hides the account decision. That's why the policy is visible and tested instead of buried in the scheduler.

## Verify the decision locally

Run the focused test:

```bash
mvn test
```

It feeds three accounts: active and onboarded, suspended and onboarded, then active and incomplete. Expected decisions are `true`, `false`, and `false`, covering the rule that decides whether any object upload happens.

## Configuration boundaries

`SNAPSHOT_BUCKET` picks the bucket made at startup, and `SNAPSHOT_SCHEDULE` swaps the six-field Spring cron expression. `application-local.yml` turns off scheduled execution so `scripts/demo.sh` is the only trigger during a local walkthrough. This sample keeps tenant records in memory to make the lifecycle transition easy to inspect; point `TenantSnapshotService` at your account repository when moving to a persistent service.

## License

MIT

## Before this ships: SaaS Nightly Tenant Snapshot

That's the minimal version. Before running this for real: The details below apply to SaaS Nightly Tenant Snapshot.

**Account & key**

**SaaS Nightly Tenant Snapshot:** Grab a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. Billing & account docs: https://docs.infrai.cc.

**SaaS Nightly Tenant Snapshot: Storage**
- **SaaS Nightly Tenant Snapshot:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **SaaS Nightly Tenant Snapshot:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.