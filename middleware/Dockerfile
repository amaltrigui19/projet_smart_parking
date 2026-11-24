# Contexte de construction : le dossier middleware/ (parent + modules iam, core, api).
# Versions d'images fixées (pas de « latest ») : la même image est reconstruite à l'identique.

# Étape 1 : compiler api.war avec Maven et lancer les tests JUnit (rien à installer sur le PC).
# Si un test échoue, la construction s'arrête et aucune image n'est produite.
FROM maven:3.9.16-eclipse-temurin-21 AS construction
WORKDIR /src
COPY . .
RUN mvn -q -B -ntp clean package

# Étape 2 : WildFly avec le WAR déployé sous /api
FROM quay.io/wildfly/wildfly:42.0.0.Final-1-jdk21
COPY --from=construction /src/api/target/api.war /opt/jboss/wildfly/standalone/deployments/api.war
