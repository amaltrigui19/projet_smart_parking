# Contexte de construction : le dossier middleware/ (parent + modules iam, core, api).
# Versions d'images fixées (pas de « latest ») : la même image est reconstruite à l'identique.

# Étape 1 : compiler api.war avec Maven et lancer les tests JUnit (rien à installer sur le PC).
# Si un test échoue, la construction s'arrête et aucune image n'est produite.
FROM maven:3.9.16-eclipse-temurin-21 AS construction
WORKDIR /src
COPY . .
RUN mvn -q -B -ntp clean package

# Variante utilisée par GitHub Actions : le WAR est déjà compilé et testé sur le runner
# (cache Maven de actions/setup-java), ce qui évite de tout retélécharger depuis Maven Central
# à chaque build (erreurs 429 « Too Many Requests »). Le WAR arrive par un contexte nommé :
#   docker build --target war-precompile --build-context war=middleware/api/target middleware
# Cette étape n'est pas construite par défaut (docker compose, build manuel).
FROM quay.io/wildfly/wildfly:42.0.0.Final-1-jdk21 AS war-precompile
COPY --from=war api.war /opt/jboss/wildfly/standalone/deployments/api.war

# Étape 2 (cible par défaut) : WildFly avec le WAR déployé sous /api
FROM quay.io/wildfly/wildfly:42.0.0.Final-1-jdk21
COPY --from=construction /src/api/target/api.war /opt/jboss/wildfly/standalone/deployments/api.war
