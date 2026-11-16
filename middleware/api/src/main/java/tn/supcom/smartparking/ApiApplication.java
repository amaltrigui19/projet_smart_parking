package tn.supcom.smartparking;

import jakarta.annotation.security.DeclareRoles;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.auth.LoginConfig;

/**
 * Point d'entrée JAX-RS. Le WAR s'appelle api.war, donc toutes les routes
 * sont sous /api (contrat : docs/contrats.md, section 2).
 *
 * <p>Sécurité : les jetons « Authorization: Bearer &lt;jwt&gt; » sont vérifiés par MicroProfile JWT
 * (clé publique et émetteur dans META-INF/microprofile-config.properties). Les routes protégées
 * portent @RolesAllowed ; le rôle vient de la revendication « groups » du jeton (contrat §2.2).
 */
@ApplicationPath("/")
@LoginConfig(authMethod = "MP-JWT", realmName = "smartparking")
@DeclareRoles({"CONDUCTEUR", "EXPLOITANT"})
public class ApiApplication extends Application {
}
