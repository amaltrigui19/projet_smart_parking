package tn.supcom.smartparking;

import jakarta.annotation.security.DeclareRoles;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.auth.LoginConfig;

/**
 * JAX-RS entry point. The WAR is named api.war, so every route is under /api
 * (contract: docs/contrats.md, section 2).
 *
 * <p>Security: "Authorization: Bearer &lt;jwt&gt;" tokens are checked by MicroProfile JWT, with the public keys
 * published by the IAM module on its JWKS endpoint (META-INF/microprofile-config.properties).
 * Protected routes carry @RolesAllowed; the role comes from the "groups" claim of the token.
 */
@ApplicationPath("/")
@LoginConfig(authMethod = "MP-JWT", realmName = "smartparking")
@DeclareRoles({"DRIVER", "OPERATOR"})
public class ApiApplication extends Application {
}
