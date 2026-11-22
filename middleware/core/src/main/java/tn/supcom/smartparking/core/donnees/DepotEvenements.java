package tn.supcom.smartparking.core.donnees;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import tn.supcom.smartparking.core.modele.Evenement;
import tn.supcom.smartparking.core.modele.TypeEvenement;

/**
 * Accès à la collection {@code evenements} (contrat §2.14), index {@code traite + debut} et {@code type + debut}.
 * Les mêmes index sont créés par {@code server/mongo/index.js}.
 */
@ApplicationScoped
public class DepotEvenements {

    static final String COLLECTION = "evenements";
    /** Limite maximale d'une liste. */
    public static final int LIMITE_MAX = 100;

    @Inject
    ConnexionMongo connexion;

    private volatile boolean indexCree;

    private MongoCollection<Document> collection() {
        MongoCollection<Document> c = connexion.base().getCollection(COLLECTION);
        if (!indexCree) {
            c.createIndex(Indexes.compoundIndex(Indexes.ascending("traite"), Indexes.descending("debut")),
                    new IndexOptions().name("traite_debut"));
            c.createIndex(Indexes.compoundIndex(Indexes.ascending("type"), Indexes.descending("debut")),
                    new IndexOptions().name("type_debut"));
            indexCree = true;
        }
        return c;
    }

    /** Enregistre une nouvelle anomalie et la renvoie avec son identifiant (« evt_… »). */
    public Evenement creer(Evenement e) {
        ObjectId id = new ObjectId();
        collection().insertOne(ConversionActivite.versDocument(e, id));
        return e.avecId(Identifiants.versApi(Identifiants.EVENEMENT, id));
    }

    public Optional<Evenement> parId(String id) {
        return Identifiants.depuisApi(Identifiants.EVENEMENT, id)
                .map(oid -> collection().find(Filters.eq("_id", oid)).first())
                .map(ConversionActivite::versEvenement);
    }

    /**
     * Liste du plus récent au plus ancien (GET /api/evenements). Chaque filtre null est ignoré.
     *
     * @param du     début inclus
     * @param au     fin exclue
     * @param limite ramenée entre 1 et {@link #LIMITE_MAX}
     */
    public List<Evenement> lister(Boolean traite, TypeEvenement type, Instant du, Instant au, int limite) {
        List<Bson> filtres = new ArrayList<>();
        if (traite != null) filtres.add(Filters.eq("traite", traite));
        if (type != null) filtres.add(Filters.eq("type", type.valeur()));
        if (du != null) filtres.add(Filters.gte("debut", Date.from(du)));
        if (au != null) filtres.add(Filters.lt("debut", Date.from(au)));
        Bson filtre = filtres.isEmpty() ? new Document() : Filters.and(filtres);
        List<Evenement> liste = new ArrayList<>();
        for (Document d : collection().find(filtre).sort(Sorts.descending("debut"))
                .limit(Math.max(1, Math.min(LIMITE_MAX, limite)))) {
            liste.add(ConversionActivite.versEvenement(d));
        }
        return liste;
    }

    /** Nombre d'anomalies non traitées (tableau de bord : alertes_non_traitees). */
    public long nombreNonTraites() {
        return collection().countDocuments(Filters.eq("traite", false));
    }

    /**
     * PATCH /api/evenements/{id} : marque l'anomalie traitée (ou non) par l'exploitant.
     *
     * @return l'événement mis à jour ; vide s'il n'existe pas
     */
    public Optional<Evenement> marquerTraite(String id, boolean traite, String exploitant, String commentaire, Instant maintenant) {
        Optional<ObjectId> oid = Identifiants.depuisApi(Identifiants.EVENEMENT, id);
        if (oid.isEmpty()) {
            return Optional.empty();
        }
        Bson maj = Updates.combine(
                Updates.set("traite", traite),
                Updates.set("traite_par", traite ? Identifiants.facultatif(Identifiants.UTILISATEUR, exploitant) : null),
                Updates.set("traite_le", traite ? Date.from(maintenant) : null),
                Updates.set("commentaire", commentaire));
        return Optional.ofNullable(collection().findOneAndUpdate(Filters.eq("_id", oid.get()), maj,
                        new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)))
                .map(ConversionActivite::versEvenement);
    }

    /** Termine l'anomalie (la place est redevenue libre) ; faux si elle n'existe pas ou est déjà finie. */
    public boolean terminer(String id, Instant fin) {
        Optional<ObjectId> oid = Identifiants.depuisApi(Identifiants.EVENEMENT, id);
        return oid.isPresent() && collection().updateOne(
                Filters.and(Filters.eq("_id", oid.get()), Filters.eq("fin", null)),
                Updates.set("fin", Date.from(fin))).getModifiedCount() == 1;
    }
}
