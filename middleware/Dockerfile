# Contexte de construction : le dossier middleware/ (parent + modules iam, core, api).
# Versions d'images fixées (pas de « latest ») : la même image est reconstruite à l'identique.

# Étape 1 : compiler api.war avec Maven et lancer les tests JUnit (rien à installer sur le PC).
# Si un test échoue, la construction s'arrête et aucune image n'est produite.
FROM maven:3.9.16-eclipse-temurin-21 AS construction
WORKDIR /src
COPY . .
RUN mvn -q -B -ntp clean package

# Base commune : WildFly avec le frontend (ROOT.war, contexte « / »).
# Le dossier frontend/ arrive par un contexte nommé, car le contexte principal est middleware/ :
#   docker build --build-context frontend=frontend middleware
# (docker-compose.yml : additional_contexts ; GitHub Actions : build-contexts).
#   frontend/front-office -> /         (conducteurs)
#   frontend/back-office  -> /admin/   (exploitants)
#   frontend/shared       -> /shared/  (code commun)
# La page d'accueil par défaut de WildFly occupe « / » : on la retire pour laisser la place à ROOT.war.
FROM quay.io/wildfly/wildfly:42.0.0.Final-1-jdk21 AS wildfly-frontend
ARG DEPLOYMENTS=/opt/jboss/wildfly/standalone/deployments
RUN /opt/jboss/wildfly/bin/jboss-cli.sh --commands="embed-server,/subsystem=undertow/server=default-server/host=default-host/location=\/:remove,stop-embedded-server" \
 && rm -rf /opt/jboss/wildfly/standalone/configuration/standalone_xml_history/current
COPY --chown=jboss:jboss --from=frontend front-office/ ${DEPLOYMENTS}/ROOT.war/
COPY --chown=jboss:jboss --from=frontend back-office/ ${DEPLOYMENTS}/ROOT.war/admin/
COPY --chown=jboss:jboss --from=frontend shared/ ${DEPLOYMENTS}/ROOT.war/shared/
COPY --chown=jboss:jboss web/WEB-INF/ ${DEPLOYMENTS}/ROOT.war/WEB-INF/
# Déploiement « éclaté » (dossier) : le fichier .dodeploy demande à WildFly de le déployer.
RUN touch ${DEPLOYMENTS}/ROOT.war.dodeploy

# Variante utilisée par GitHub Actions : le WAR est déjà compilé et testé sur le runner
# (cache Maven de actions/setup-java), ce qui évite de tout retélécharger depuis Maven Central
# à chaque build (erreurs 429 « Too Many Requests »). Le WAR arrive par un contexte nommé :
#   docker build --target war-precompile --build-context war=middleware/api/target --build-context frontend=frontend middleware
# Cette étape n'est pas construite par défaut (docker compose, build manuel).
FROM wildfly-frontend AS war-precompile
COPY --from=war api.war /opt/jboss/wildfly/standalone/deployments/api.war

# Étape 2 (cible par défaut) : WildFly avec le frontend sous / et l'API sous /api
FROM wildfly-frontend
COPY --from=construction /src/api/target/api.war /opt/jboss/wildfly/standalone/deployments/api.war
