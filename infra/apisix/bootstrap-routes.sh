#!/usr/bin/env bash
# Register upstreams + routes against APISIX Admin API.
# Run once after `docker compose up -d` finishes. Idempotent — safe to re-run.
set -euo pipefail

ADMIN="${APISIX_ADMIN:-http://localhost:9180}"
KEY="${APISIX_ADMIN_KEY:-edd1c9f034335f136f87ad84b625c8f1}"
# host.docker.internal works on Docker Desktop (macOS/Windows).
# On Linux, set HOST_GATEWAY=172.17.0.1 (or use --add-host).
HOST="${SERVICE_HOST:-host.docker.internal}"

put() {
  local path="$1" body="$2"
  curl -sS -o /dev/null -w "%{http_code}  %s\n" \
    -X PUT "$ADMIN$path" \
    -H "X-API-KEY: $KEY" \
    -H 'Content-Type: application/json' \
    -d "$body"
}

# Upstreams — services run on the host via IDE for fast iteration
put /apisix/admin/upstreams/order "$(cat <<JSON
{"type":"roundrobin","nodes":{"${HOST}:8081":1}}
JSON
)"

put /apisix/admin/upstreams/payment "$(cat <<JSON
{"type":"roundrobin","nodes":{"${HOST}:8082":1}}
JSON
)"

put /apisix/admin/upstreams/inventory "$(cat <<JSON
{"type":"roundrobin","nodes":{"${HOST}:8083":1}}
JSON
)"

# OIDC plugin block — reused on each route
OIDC='{
  "client_id": "apisix",
  "client_secret": "apisix-secret",
  "discovery": "http://commerce-keycloak:8080/realms/commerce/.well-known/openid-configuration",
  "bearer_only": true,
  "use_jwks": true,
  "introspection_endpoint_auth_method": "client_secret_post"
}'

put /apisix/admin/routes/orders "$(cat <<JSON
{
  "uri": "/api/orders/*",
  "upstream_id": "order",
  "plugins": {
    "openid-connect": ${OIDC},
    "proxy-rewrite": {"regex_uri": ["^/api/orders/(.*)", "/\$1"]},
    "cors": {},
    "request-id": {}
  }
}
JSON
)"

put /apisix/admin/routes/payments "$(cat <<JSON
{
  "uri": "/api/payments/*",
  "upstream_id": "payment",
  "plugins": {
    "openid-connect": ${OIDC},
    "proxy-rewrite": {"regex_uri": ["^/api/payments/(.*)", "/\$1"]},
    "cors": {},
    "request-id": {}
  }
}
JSON
)"

put /apisix/admin/routes/inventory "$(cat <<JSON
{
  "uri": "/api/inventory/*",
  "upstream_id": "inventory",
  "plugins": {
    "openid-connect": ${OIDC},
    "proxy-rewrite": {"regex_uri": ["^/api/inventory/(.*)", "/\$1"]},
    "cors": {},
    "request-id": {}
  }
}
JSON
)"

echo "Routes registered."
