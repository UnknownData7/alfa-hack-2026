#!/usr/bin/env bash
# Сброс демо-данных на сервере перед показом. Снаружи этот адрес закрыт в Caddyfile.
set -euo pipefail
curl -fsS -X POST http://127.0.0.1:3000/api/dev/reset-demo
echo
