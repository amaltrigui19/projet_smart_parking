package tn.supcom.smartparking.config;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import tn.supcom.smartparking.CorpsJson;
import tn.supcom.smartparking.core.donnees.DepotParkings;
import tn.supcom.smartparking.core.donnees.DepotPlaces;
import tn.supcom.smartparking.core.modele.Parking;
import tn.supcom.smartparking.core.modele.Place;
import tn.supcom.smartparking.erreurs.ErreurApi;

/**
 * GET et PUT /api/config/polygones (contrat §2.10) : le fichier places.json (§5.2) de la calibration.
 * Réservé à l'exploitant. Les places vont dans la collection places (DepotPlaces.remplacerPolygones,
 * qui garde l'état envoyé par le Pi) ; la résolution et les zones interdites vont dans le document du parking.
 */
@Path("/config/polygones")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("EXPLOITANT")
public class ConfigPolygonesResource {

    @Inject
    DepotPlaces depotPlaces;

    @Inject
    DepotParkings depotParkings;

    /** → 200 avec places.json ; 404 introuvable s'il n'y a encore rien. */
    @GET
    public JsonObject lire() {
        Parking parking = depotParkings.parId(ValidationPolygones.PARKING)
                .orElseThrow(() -> introuvable("Aucun fichier de polygones enregistré : le parking P1 n'existe pas encore."));
        List<Place> places = depotPlaces.duParking(parking.id());
        if (places.isEmpty() && parking.zonesInterdites().isEmpty()) {
            throw introuvable("Aucun fichier de polygones enregistré pour l'instant.");
        }
        return ConversionPolygones.fichier(parking, places);
    }

    /** Corps : places.json en entier → 200 avec le fichier enregistré ; 400 config_invalide ; 404 sans parking. */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public JsonObject enregistrer(String corps) {
        JsonObject fichier = CorpsJson.lireObjet(corps);
        List<String> erreurs = ValidationPolygones.valider(fichier);
        if (!erreurs.isEmpty()) {
            throw new ErreurApi(400, "config_invalide", String.join(ValidationPolygones.SEPARATEUR, erreurs));
        }
        Parking existant = depotParkings.parId(ValidationPolygones.PARKING)
                .orElseThrow(() -> introuvable("Le parking P1 n'existe pas encore : lancer les données d'exemple "
                        + "(server/mongo/seed.js, voir middleware/README.md)."));
        depotPlaces.remplacerPolygones(existant.id(), ConversionPolygones.places(fichier));
        depotParkings.enregistrer(ConversionPolygones.parking(fichier, existant));
        // Sprint suivant : publier le fichier au Pi sur parking/P1/config (contrat §5.3).
        return lire();
    }

    private static ErreurApi introuvable(String message) {
        return new ErreurApi(404, "introuvable", message);
    }
}
