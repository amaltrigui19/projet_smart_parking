package tn.supcom.smartparking.core.donnees;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.ObjectId;
import tn.supcom.smartparking.core.modele.Autorisation;
import tn.supcom.smartparking.core.modele.TypeAutorisation;

/**
 * Accès à la collection {@code autorisations} (contrat §2.14), index {@code utilisateur + type}.
 * Les mêmes index sont créés par {@code server/mongo/index.js} (mêmes noms, sinon MongoDB refuse).
 */
@ApplicationScoped
public class DepotAutorisations {

    static final String COLLECTION = "autorisations";

    @Inject
    ConnexionMongo connexion;

    private volatile boolean indexCree;

    private MongoCollection<Document> collection() {
        MongoCollection<Document> c = connexion.base().getCollection(COLLECTION);
        if (!indexCree) {
            c.createIndex(Indexes.ascending("utilisateur", "type"), new IndexOptions().name("utilisateur_type"));
            indexCree = true;
        }
        return c;
    }

    /** Enregistre une nouvelle autorisation et la renvoie avec son identifiant (« aut_… »). */
    public Autorisation creer(Autorisation a) {
        ObjectId id = new ObjectId();
        collection().insertOne(ConversionActivite.versDocument(a, id));
        return a.avecId(Identifiants.versApi(Identifiants.AUTORISATION, id));
    }

    /** Toutes les autorisations d'un utilisateur (même expirées), de la plus récente à la plus ancienne. */
    public List<Autorisation> duUtilisateur(String utilisateur) {
        List<Autorisation> liste = new ArrayList<>();
        Optional<ObjectId> u = Identifiants.depuisApi(Identifiants.UTILISATEUR, utilisateur);
        if (u.isEmpty()) {
            return liste;
        }
        for (Document d : collection().find(Filters.eq("utilisateur", u.get())).sort(Sorts.descending("debut"))) {
            liste.add(ConversionActivite.versAutorisation(d));
        }
        return liste;
    }

    /** Vrai si l'utilisateur a une autorisation de ce type en vigueur à cet instant (réservation, place_speciale). */
    public boolean aUneValide(String utilisateur, TypeAutorisation type, Instant instant) {
        Optional<ObjectId> u = Identifiants.depuisApi(Identifiants.UTILISATEUR, utilisateur);
        if (u.isEmpty()) {
            return false;
        }
        Date t = Date.from(instant);
        return collection().find(Filters.and(
                Filters.eq("utilisateur", u.get()),
                Filters.eq("type", type.valeur()),
                Filters.lte("debut", t),
                Filters.or(Filters.eq("fin", null), Filters.gt("fin", t)))).first() != null;
    }

    /** Retire l'autorisation {@code id} de l'utilisateur ; faux si elle n'existe pas ou n'est pas à lui. */
    public boolean supprimer(String utilisateur, String id) {
        Optional<ObjectId> u = Identifiants.depuisApi(Identifiants.UTILISATEUR, utilisateur);
        Optional<ObjectId> a = Identifiants.depuisApi(Identifiants.AUTORISATION, id);
        if (u.isEmpty() || a.isEmpty()) {
            return false;
        }
        return collection().deleteOne(Filters.and(Filters.eq("_id", a.get()), Filters.eq("utilisateur", u.get())))
                .getDeletedCount() == 1;
    }
}
