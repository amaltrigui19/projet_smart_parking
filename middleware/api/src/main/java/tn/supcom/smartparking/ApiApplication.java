package tn.supcom.smartparking;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Point d'entrée JAX-RS. Le WAR s'appelle api.war, donc toutes les routes
 * sont sous /api (contrat : docs/contrats.md, section 2).
 */
@ApplicationPath("/")
public class ApiApplication extends Application {
}
