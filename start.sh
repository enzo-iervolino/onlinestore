#!/usr/bin/env bash
set -e

echo "Libero la porta 8081..."
kill -9 $(lsof -t -i :8081) 2>/dev/null || true

echo "Avvio PostgreSQL..."
/usr/lib/postgresql/16/bin/pg_ctl -D /workspaces/pgdata -l /workspaces/pgdata/postgresql.log start || true

echo "Controllo PostgreSQL su porta 5433..."
/usr/lib/postgresql/16/bin/pg_isready -h localhost -p 5433

echo "Imposto Java 17..."
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"

echo "Avvio applicazione Spring Boot..."
chmod +x mvnw
./mvnw spring-boot:run \
  -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:postgresql://localhost:5433/mystoredb --spring.datasource.username=postgres --spring.datasource.password=postgres"
