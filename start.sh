#!/usr/bin/env bash
set -euo pipefail

# ==========================================================
# ONLINE STORE - START SCRIPT SICURO PER LOCALE / RAILWAY
# ==========================================================
# Non inserire secret reali in questo file.
# Tutte le credenziali vanno configurate come variabili ambiente
# su Railway o nel terminale locale prima dell'avvio.
# ==========================================================

export TZ="${TZ:-Europe/Rome}"
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:--Duser.timezone=Europe/Rome}"

APP_PORT="${PORT:-${SERVER_PORT:-8081}}"
export SERVER_PORT="${APP_PORT}"

if [ -z "${APP_BASE_URL:-}" ]; then
  export APP_BASE_URL="http://localhost:${APP_PORT}"
fi

if [ -z "${APP_PAYMENT_SUCCESS_URL:-}" ]; then
  export APP_PAYMENT_SUCCESS_URL="${APP_BASE_URL}/checkout/stripe/success?session_id={CHECKOUT_SESSION_ID}"
fi

if [ -z "${APP_PAYMENT_CANCEL_URL:-}" ]; then
  export APP_PAYMENT_CANCEL_URL="${APP_BASE_URL}/checkout/stripe/cancel"
fi

if [ -z "${PAYPAL_RETURN_URL:-}" ]; then
  export PAYPAL_RETURN_URL="${APP_BASE_URL}/paypal/success"
fi

if [ -z "${PAYPAL_CANCEL_URL:-}" ]; then
  export PAYPAL_CANCEL_URL="${APP_BASE_URL}/paypal/cancel"
fi

export PAYPAL_API_URL="${PAYPAL_API_URL:-https://api-m.sandbox.paypal.com}"
export PAYPAL_CURRENCY="${PAYPAL_CURRENCY:-EUR}"

echo "=========================================================="
echo "Online Store startup"
echo "Timezone: ${TZ}"
echo "Porta applicazione: ${APP_PORT}"
echo "APP_BASE_URL: ${APP_BASE_URL}"
echo "Stripe secret presente? ${STRIPE_SECRET_KEY:+SI}"
echo "Stripe webhook secret presente? ${STRIPE_WEBHOOK_SECRET:+SI}"
echo "PayPal client id presente? ${PAYPAL_CLIENT_ID:+SI}"
echo "PayPal client secret presente? ${PAYPAL_CLIENT_SECRET:+SI}"
echo "Mail username presente? ${MAIL_USERNAME:+SI}"
echo "Datasource url presente? ${SPRING_DATASOURCE_URL:+SI}${DB_URL:+SI}"
echo "=========================================================="

chmod +x ./mvnw

# In produzione Railway imposta PORT automaticamente.
# Spring Boot userà server.port=${PORT:${SERVER_PORT:8081}} da application.properties.
exec ./mvnw spring-boot:run
