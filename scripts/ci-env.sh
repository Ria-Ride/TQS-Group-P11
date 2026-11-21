#!/usr/bin/env bash
# Creates a throwaway .env for a CI run that starts the whole stack (docker compose up).
# Every secret is random and lives only for that run. The admin account it creates is passed on to
# the next steps as E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD (masked in the log).
# Outside GitHub Actions it only writes .env. Refuses to overwrite an existing .env.
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ -e .env ]]; then
  echo ".env already exists, not overwritten" >&2
  exit 1
fi

random() { openssl rand -hex 24; }

db_password="$(random)"
admin_email="admin@ci.example"
admin_password="$(random)"

if [[ -n "${GITHUB_ENV:-}" ]]; then
  echo "::add-mask::$db_password"
  echo "::add-mask::$admin_password"
  {
    echo "E2E_ADMIN_EMAIL=$admin_email"
    echo "E2E_ADMIN_PASSWORD=$admin_password"
  } >>"$GITHUB_ENV"
fi

# Starts from .env.example so a variable added there is never missing here.
sed -e "s|^POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=$db_password|" \
  -e "s|^APP_JWT_SECRET=.*|APP_JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')|" \
  -e "s|^ADMIN_EMAIL=.*|ADMIN_EMAIL=$admin_email|" \
  -e "s|^ADMIN_PASSWORD=.*|ADMIN_PASSWORD=$admin_password|" \
  .env.example >.env
