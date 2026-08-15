#!/usr/bin/env bash
set -euo pipefail

base="${SNAPSHOT_SERVICE_URL:-http://localhost:8080}"
curl --fail-with-body -X POST "${base}/admin/tenants" \
  -H 'Content-Type: application/json' \
  -d '{"tenantId":"academy-42","courseCatalog":"teacher-certificates","status":"ACTIVE","onboardingComplete":true,"updatedAt":"2026-08-15T00:00:00Z"}'
curl --fail-with-body -X POST "${base}/admin/tenants/academy-42/snapshots"
