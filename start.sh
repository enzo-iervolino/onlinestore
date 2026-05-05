 
#!/usr/bin/env bash
set -e

# ==========================================================
# TIMEZONE ITALIA
# ==========================================================

export TZ="Europe/Rome"
export JAVA_TOOL_OPTIONS="-Duser.timezone=Europe/Rome"

echo "Timezone Linux: $TZ"
echo "Ora sistema:"
date


# ==========================================================
# ONLINE STORE - START SCRIPT COMPLETO
# File: start.sh
# Posizione: /workspaces/onlinestore/start.sh
# ==========================================================


# ==========================================================
# APP BASE URL - GitHub Codespaces
# ==========================================================

export APP_BASE_URL="https://curly-robot-5ggp6jgw97j9f4gx6-8081.app.github.dev"


# ==========================================================
# PAYPAL SANDBOX
# ==========================================================

export PAYPAL_API_URL="https://api-m.sandbox.paypal.com"
export PAYPAL_CLIENT_ID="AX3WfPUqq2-QfgMyM8AeUDUDUEn9WUNlq4mba-tp45x3DMdl9EkcBHlu_vFeWNOJFM4slVpYzNsuA8GH"
export PAYPAL_CLIENT_SECRET="EKCqSbY0RHeklilPUSgf2nTZgKL1aKkApDHnv9HJZZDZ4XSkNcHeyeMjHiXcOhsWKasLd4gmWQLYT-vp"
export PAYPAL_CURRENCY="EUR"

export PAYPAL_RETURN_URL="${APP_BASE_URL}/paypal/success"
export PAYPAL_CANCEL_URL="${APP_BASE_URL}/paypal/cancel"


# ==========================================================
# EMAIL / SMTP GMAIL
# ==========================================================

export MAIL_HOST="smtp.gmail.com"
export MAIL_PORT="587"
export MAIL_USERNAME="ge.iervolino@gmail.com"
export MAIL_PASSWORD="rtcv upaa qncr ltez"

export MAIL_SMTP_AUTH="true"
export MAIL_STARTTLS_ENABLE="true"
export MAIL_STARTTLS_REQUIRED="true"


# ==========================================================
# STRIPE / CHECKOUT
# ==========================================================

export APP_PAYMENT_SUCCESS_URL="${APP_BASE_URL}/checkout/stripe/success?session_id={CHECKOUT_SESSION_ID}"
export APP_PAYMENT_CANCEL_URL="${APP_BASE_URL}/checkout/stripe/cancel"

export STRIPE_PUBLIC_KEY="pk_test_51OqwDCEzfFlea423x82qLr4UGst2IWtdznDH1kDfImSIaIB2ZC6UjaMrvo48T5Af0lI4vzouJ4vqbLH6A7TKA4gL00f9EWJ79r"
export STRIPE_SECRET_KEY="sk_test_51OqwDCEzfFlea423MQjAai1MTftfU7i7yAp4KP7p2t3vywAGPkT5KJPRDvKBanmdBmwqyWYhvHyvpKqMLMU025rq00oISsQN55"
export STRIPE_WEBHOOK_SECRET="whsec_3e5a2298cca924f195b70270a33c1f04f7396e8a9343e9bf2cbcb78cb74b46e9"


# ==========================================================
# GOOGLE LOGIN / OAUTH2
# ==========================================================

export GOOGLE_CLIENT_ID="942923652965-874dagpfdaicdcluk7uma6ad46umf6kn.apps.googleusercontent.com"
export GOOGLE_CLIENT_SECRET="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"


# ==========================================================
# DATABASE POSTGRESQL
# ==========================================================

export DB_HOST="localhost"
export DB_PORT="5433"
export DB_NAME="mystoredb"
export DB_USERNAME="postgres"
export DB_PASSWORD="stellina"

export SPRING_DATASOURCE_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}"
export SPRING_DATASOURCE_USERNAME="${DB_USERNAME}"
export SPRING_DATASOURCE_PASSWORD="${DB_PASSWORD}"


# ==========================================================
# VERIFICA VARIABILI - NON STAMPA I SECRET
# ==========================================================

echo "Verifica variabili principali..."
echo "APP_BASE_URL=${APP_BASE_URL}"
echo "PAYPAL_API_URL=${PAYPAL_API_URL}"
echo "PAYPAL_RETURN_URL=${PAYPAL_RETURN_URL}"
echo "PAYPAL_CANCEL_URL=${PAYPAL_CANCEL_URL}"
echo "PAYPAL_CLIENT_ID presente? ${PAYPAL_CLIENT_ID:+SI}"
echo "PAYPAL_CLIENT_SECRET presente? ${PAYPAL_CLIENT_SECRET:+SI}"
echo "MAIL_USERNAME=${MAIL_USERNAME}"
echo "MAIL_PASSWORD presente? ${MAIL_PASSWORD:+SI}"
echo "STRIPE_PUBLIC_KEY presente? ${STRIPE_PUBLIC_KEY:+SI}"
echo "STRIPE_SECRET_KEY presente? ${STRIPE_SECRET_KEY:+SI}"
echo "STRIPE_WEBHOOK_SECRET presente? ${STRIPE_WEBHOOK_SECRET:+SI}"
echo "GOOGLE_CLIENT_ID presente? ${GOOGLE_CLIENT_ID:+SI}"
echo "GOOGLE_CLIENT_SECRET presente? ${GOOGLE_CLIENT_SECRET:+SI}"
echo "SPRING_DATASOURCE_URL=${SPRING_DATASOURCE_URL}"
echo "SPRING_DATASOURCE_USERNAME=${SPRING_DATASOURCE_USERNAME}"
echo "SPRING_DATASOURCE_PASSWORD presente? ${SPRING_DATASOURCE_PASSWORD:+SI}"


# ==========================================================
# LIBERO PORTA 8081
# ==========================================================

echo "Libero la porta 8081..."
kill -9 $(lsof -t -i :8081) 2>/dev/null || true


# ==========================================================
# AVVIO POSTGRESQL
# ==========================================================

echo "Avvio PostgreSQL..."
/usr/lib/postgresql/16/bin/pg_ctl -D /workspaces/pgdata -l /workspaces/pgdata/postgresql.log start || true

echo "Controllo PostgreSQL su porta ${DB_PORT}..."
/usr/lib/postgresql/16/bin/pg_isready -h "${DB_HOST}" -p "${DB_PORT}"


# ==========================================================
# JAVA 17
# ==========================================================

echo "Imposto Java 17..."
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"

java -version


# ==========================================================
# AVVIO SPRING BOOT
# ==========================================================

echo "Avvio applicazione Spring Boot..."
chmod +x mvnw

./mvnw spring-boot:run \
  -Dspring-boot.run.arguments="--spring.datasource.url=${SPRING_DATASOURCE_URL} --spring.datasource.username=${SPRING_DATASOURCE_USERNAME} --spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}"