package tn.supcom.smartparking.core.donnees;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Date;
import java.util.Optional;
import org.bson.Document;
import tn.supcom.smartparking.core.modele.Parking;

/** Accès à la collection {@code parkings} (contrat §2.14). */
@ApplicationScoped
public class DepotParkings {

    static final String COLLECTION = "parkings";

    @Inject
    ConnexionMongo connexion;

    private MongoCollection<Document> collection() {
        return connexion.base().getCollection(COLLECTION);
    }

    /** Le parking par son identifiant ("P1") ; vide s'il n'existe pas. */
    public Optional<Parking> parId(String id) {
        return Optional.ofNullable(collection().find(Filters.eq("_id", id)).first())
                .map(ConversionMongo::versParking);
    }

    /**
     * Crée ou met à jour le parking. Seuls les champs du modèle sont écrits ($set) :
     * le graphe, les paramètres et le statut du Pi (sprints suivants) ne sont pas effacés.
     */
    public void enregistrer(Parking parking) {
        Document champs = ConversionMongo.champsParking(parking).append("maj_le", new Date());
        collection().updateOne(Filters.eq("_id", parking.id()), new Document("$set", champs),
                new UpdateOptions().upsert(true));
    }
}