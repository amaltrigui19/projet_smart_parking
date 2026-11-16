package tn.supcom.smartparking.core.donnees;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import tn.supcom.smartparking.core.modele.EtatPlace;
import tn.supcom.smartparking.core.modele.Place;

/** Accès à la collection {@code places} (contrat §2.14), index sur {@code parking}. */
@ApplicationScoped
public class DepotPlaces {

    static final String COLLECTION = "places";

    @Inject
    ConnexionMongo connexion;

    private volatile boolean indexCree;

    private MongoCollection<Document> collection() {
        MongoCollection<Document> c = connexion.base().getCollection(COLLECTION);
        if (!indexCree) {
            // Sans effet si l'index existe déjà
            c.createIndex(Indexes.ascending("parking"), new IndexOptions().name("parking"));
            indexCree = true;
        }
        return c;
    }

    /** Toutes les places d'un parking, triées par identifiant (A1, A2, ..., B4). */
    public List<Place> duParking(String parking) {
        List<Place> places = new ArrayList<>();
        for (Document d : collection().find(Filters.eq("parking", parking)).sort(Sorts.ascending("_id"))) {
            places.add(ConversionMongo.versPlace(d));
        }
        return places;
    }

    /** Une place par son identifiant ; vide si elle n'existe pas. */
    public Optional<Place> parId(String id) {
        return Optional.ofNullable(collection().find(Filters.eq("_id", id)).first())
                .map(ConversionMongo::versPlace);
    }

    /** Crée la place, ou la remplace entièrement si elle existe déjà. */
    public void enregistrer(Place place) {
        collection().replaceOne(Filters.eq("_id", place.id()), ConversionMongo.versDocument(place),
                new ReplaceOptions().upsert(true));
    }

    /**
     * Calibration : enregistre les polygones de places.json (contrat 5.2).
     * Met à jour type, durée et polygone, mais GARDE l'état venant du Pi et les données du plan.
     * Les places du parking absentes de la liste sont supprimées.
     */
    public void remplacerPolygones(String parking, List<Place> places) {
        List<String> ids = new ArrayList<>();
        for (Place p : places) {
            ids.add(p.id());
            collection().updateOne(Filters.eq("_id", p.id()),
                    Updates.combine(
                            Updates.set("parking", parking),
                            Updates.set("type", p.type().valeur()),
                            Updates.set("duree_max_min", p.dureeMaxMin()),
                            Updates.set("poly", ConversionMongo.polyVersListe(p.poly())),
                            // seulement si la place est nouvelle :
                            Updates.setOnInsert("etat", EtatPlace.LIBRE.valeur()),
                            Updates.setOnInsert("depuis", null),
                            Updates.setOnInsert("rect_cm", null),
                            Updates.setOnInsert("noeud", null)),
                    new UpdateOptions().upsert(true));
        }
        collection().deleteMany(Filters.and(Filters.eq("parking", parking), Filters.nin("_id", ids)));
    }
}