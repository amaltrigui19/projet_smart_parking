package tn.supcom.smartparking.auth;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import tn.supcom.smartparking.JsonBody;
import tn.supcom.smartparking.iam.EmetteurJwt;
import tn.supcom.smartparking.iam.ErreurIam;
import tn.supcom.smartparking.iam.ServiceAuthentification;
import tn.supcom.smartparking.iam.Utilisateur;

/** Routes d'authentification, exactement comme le contrat §2.5. */
@Path("/auth")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    ServiceAuthentification service;

    @Inject
    EmetteurJwt emetteur;

    @Inject
    JsonWebToken jeton;

    /** POST /api/auth/inscription · public → 201 ; 400 requete_invalide ; 409 email_existant. */
    @POST
    @Path("/inscription")
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    public Response inscription(String corps) {
        JsonObject o = JsonBody.readObject(corps);
        // Un champ « role » éventuellement envoyé est ignoré : l'inscription crée toujours un CONDUCTEUR.
        Utilisateur u = service.inscrire(texte(o, "nom"), texte(o, "email"), texte(o, "mot_de_passe"));
        return Response.status(Response.Status.CREATED)
                .entity(Json.createObjectBuilder()
                        .add("id", u.id())
                        .add("nom", u.nom())
                        .add("email", u.email())
                        .add("role", u.role().name())
                        .add("actif", u.actif())
                        .build())
                .build();
    }

    /** POST /api/auth/connexion · public → 200 avec le jeton ; 401 identifiants_invalides ; 403 compte_desactive. */
    @POST
    @Path("/connexion")
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    public Response connexion(String corps) {
        JsonObject o = JsonBody.readObject(corps);
        Utilisateur u = service.connecter(texte(o, "email"), texte(o, "mot_de_passe"));
        return Response.ok(Json.createObjectBuilder()
                        .add("jeton", emetteur.emettre(u))
                        .add("type", "Bearer")
                        .add("expire_dans", emetteur.dureeVieSecondes())
                        .add("utilisateur", Json.createObjectBuilder()
                                .add("id", u.id())
                                .add("nom", u.nom())
                                .add("email", u.email())
                                .add("role", u.role().name()))
                        .build())
                .header("Cache-Control", "no-store")
                .build();
    }

    /** GET /api/auth/moi · connecté → 200 ; 401 non_authentifie sans jeton valide. */
    @GET
    @Path("/moi")
    @RolesAllowed({"CONDUCTEUR", "EXPLOITANT"})
    public JsonObject moi() {
        if (jeton == null || jeton.getSubject() == null) {
            // Ceinture et bretelles : @RolesAllowed refuse déjà les appels sans jeton valide.
            throw new ErreurIam(401, "non_authentifie", "JWT absent, invalide ou expiré : reconnectez-vous.");
        }
        Utilisateur u = service.parId(jeton.getSubject())
                .orElseThrow(() -> new ErreurIam(401, "non_authentifie", "Ce compte n'existe plus : reconnectez-vous."));
        if (!u.actif()) {
            throw ErreurIam.compteDesactive(); // contrat §2.11 : les jetons d'un compte désactivé sont refusés
        }
        return Json.createObjectBuilder()
                .add("id", u.id())
                .add("nom", u.nom())
                .add("email", u.email())
                .add("role", u.role().name())
                // Collection « autorisations » prévue au sprint 6 : liste vide pour l'instant.
                .add("autorisations", Json.createArrayBuilder())
                .build();
    }

    /** Valeur texte d'un champ, null s'il est absent ; 400 s'il n'est pas un texte. */
    private static String texte(JsonObject o, String cle) {
        JsonValue v = o.get(cle);
        if (v == null || v.getValueType() == JsonValue.ValueType.NULL) {
            return null;
        }
        if (v.getValueType() != JsonValue.ValueType.STRING) {
            throw ErreurIam.requeteInvalide("Le champ « " + cle + " » doit être un texte.");
        }
        return ((JsonString) v).getString();
    }
}
