# Contexte de construction : le dossier middleware/ (parent + modules iam, core, api).
# Étape 1 : compiler api.war avec Maven (rien à installer sur le PC)
FROM maven:3.9-eclipse-temurin-21 AS construction
WORKDIR /src
COPY . .
RUN mvn -q -B package -DskipTests

# Étape 2 : WildFly avec le WAR déployé sous /api
FROM quay.io/wildfly/wildfly:latest-jdk21
COPY --from=construction /src/api/target/api.war /opt/jboss/wildfly/standalone/deployments/api.war
