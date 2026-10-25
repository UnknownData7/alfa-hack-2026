#!/usr/bin/env bash
# Установка и обновление стенда на чистом сервере Ubuntu 22.04 или 24.04. Запуск от root:
#   curl -fsSL https://raw.githubusercontent.com/UnknownData7/alfa-hack-2026/main/deploy/install.sh | bash
# Домен по умолчанию: <IP>.sslip.io, свой домен можно передать: ... | SITE_DOMAIN=demo.example.ru bash
set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/UnknownData7/alfa-hack-2026.git}"
BRANCH="${BRANCH:-main}"
APP_DIR="${APP_DIR:-/opt/credit-cabinet}"

log() { printf '\n==> %s\n' "$1"; }

if [ "$(id -u)" -ne 0 ]; then
  echo "Запустите от root (sudo -i)" >&2
  exit 1
fi

log "Пакеты: git, curl, Docker"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq git curl ca-certificates openssl >/dev/null
if ! command -v docker >/dev/null 2>&1; then
  # Официальный установщик, при недоступности пакеты из репозитория Ubuntu
  curl -fsSL https://get.docker.com | sh || apt-get install -y -qq docker.io docker-compose-v2 >/dev/null
fi
systemctl enable --now docker >/dev/null

log "Проверка доступа к Docker Hub"
if ! docker pull -q hello-world >/dev/null 2>&1; then
  echo "Docker Hub недоступен, подключаю зеркало mirror.gcr.io"
  mkdir -p /etc/docker
  echo '{"registry-mirrors": ["https://mirror.gcr.io"]}' > /etc/docker/daemon.json
  systemctl restart docker
  docker pull -q hello-world >/dev/null
fi

log "Код: $REPO_URL ($BRANCH) в $APP_DIR"
if [ -d "$APP_DIR/.git" ]; then
  git -C "$APP_DIR" fetch -q origin "$BRANCH"
  git -C "$APP_DIR" checkout -q "$BRANCH"
  git -C "$APP_DIR" reset -q --hard "origin/$BRANCH"
else
  git clone -q --branch "$BRANCH" "$REPO_URL" "$APP_DIR"
fi
cd "$APP_DIR"

if [ ! -f .env ]; then
  log "Настройки .env с новыми паролями"
  if [ -z "${SITE_DOMAIN:-}" ]; then
    ip="$(curl -fsS4 https://api.ipify.org || curl -fsS4 https://ifconfig.me)"
    SITE_DOMAIN="${ip//./-}.sslip.io"
  fi
  cp .env.example .env
  set_env() { sed -i "s|^$1=.*|$1=$2|" .env; grep -q "^$1=" .env || echo "$1=$2" >> .env; }
  set_env POSTGRES_PASSWORD "$(openssl rand -hex 24)"
  set_env JWT_SECRET "$(openssl rand -hex 32)"
  set_env FRONTEND_PORT "127.0.0.1:3000"
  set_env BACKEND_PORT "127.0.0.1:8081"
  set_env CORS_ALLOWED_ORIGINS "https://$SITE_DOMAIN"
  set_env SITE_DOMAIN "$SITE_DOMAIN"
  chmod 600 .env
fi
SITE_DOMAIN="$(grep '^SITE_DOMAIN=' .env | cut -d= -f2)"

log "Сборка и запуск (первый раз 5-10 минут)"
compose() { docker compose -f docker-compose.yml -f deploy/docker-compose.server.yml "$@"; }
compose up -d --build

log "Ожидание готовности"
for _ in $(seq 1 60); do
  if curl -fsS http://127.0.0.1:3000/actuator/health 2>/dev/null | grep -q UP; then
    break
  fi
  sleep 5
done
compose ps

cat <<EOF

Готово: https://$SITE_DOMAIN
Вход: romashka, kim и другие демо-учётки, пароль Demo#2026
Сертификат выпускается при первом открытии, это может занять до минуты.
Обновить стенд: запустите этот скрипт ещё раз. Сброс демо-данных: $APP_DIR/deploy/reset-demo.sh
EOF
