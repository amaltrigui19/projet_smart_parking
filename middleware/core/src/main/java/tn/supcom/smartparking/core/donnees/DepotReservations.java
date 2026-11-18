package tn.supcom.smartparking.core.donnees;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import tn.supcom.smartparking.core.modele.EtatReservation;
import tn.supcom.smartparking.core.modele.Reservation;

/**
 * Accès à la collection {@code reservations} (contrat §2.14).
 *
 * <p>Index du contrat : {@code utilisateur + creee_le} (historique) et {@code place + etat}.
 * Deux index uniques <b>partiels</b> en plus, limités aux états ouverts (active, en_cours) : c'est la base
 * qui garantit « une seule réservation ouverte par conducteur » et « par place », même si deux requêtes
 * arrivent en même temps. Les mêmes index sont créés par {@code server/mongo/index.js}.
 */
@ApplicationScoped
public class DepotReservations {

    static final String COLLECTION = "reservations";
    static final String INDEX_UTILISATEUR_OUVERTE = "utilisateur_ouverte_unique";
    static final String INDEX_PLACE_OUVERTE = "place_ouverte_unique";
    /** Limite maximale d'une liste (contrat §2.8 : 100 au plus). */
    public static final int LIMITE_MAX = 100;

    @Inject
    ConnexionMongo connexion;

    private volatile boolean indexCree;

    private MongoCollection<Document> collection() {
        MongoCollection<Document> c = connexion.base().getCollection(COLLECTION);
        if (!indexCree) {
            c.createIndex(Indexes.compoundIndex(Indexes.ascending("utilisateur"), Indexes.descending("creee_le")),
                    new IndexOptions().name("utilisateur_creee_le"));
            c.createIndex(Indexes.ascending("place", "etat"), new IndexOptions().name("place_etat"));
            c.createIndex(Indexes.ascending("utilisateur"), new IndexOptions().name(INDEX_UTILISATEUR_OUVERTE)
                    .unique(true).partialFilterExpression(filtreOuvertes()));
            c.createIndex(Indexes.ascending("place"), new IndexOptions().name(INDEX_PLACE_OUVERTE)
                    .unique(true).partialFilterExpression(filtreOuvertes()));
            indexCree = true;
        }
        return c;
    }

    /** {etat: {$in: ["active", "en_cours"]}} : identique au filtre de index.js. */
    static Bson filtreOuvertes() {
        List<String> ouverts = new ArrayList<>();
        for (EtatReservation e : EtatReservation.values()) {
            if (e.ouvert()) ouverts.add(e.valeur());
        }
        return Filters.in("etat", ouverts);
    }

    /**
     * Enregistre une nouvelle réservation et la renvoie avec son identifiant (« r_… »).
     *
     * @throws ReservationEnConflit si le conducteur ou la place a déjà une réservation ouverte
     */
    public Reservation creer(Reservation r) {
        ObjectId id = new ObjectId();
        try {
            collection().insertOne(ConversionActivite.versDocument(r, id));
        } catch (MongoWriteException e) {
            throw traduire(e);
        }
        return r.avecId(Identifiants.versApi(Identifiants.RESERVATION, id));
    }

    public Optional<Reservation> parId(String id) {
        return Identifiants.depuisApi(Identifiants.RESERVATION, id)
                .map(oid -> collection().find(Filters.eq("_id", oid)).first())
                .map(ConversionActivite::versReservation);
    }

    /**
     * Liste du plus récent au plus ancien (GET /api/reservations).
     *
     * @param utilisateur null = tous (exploitant)
     * @param etat        null = tous les états
     * @param limite      ramenée entre 1 et {@link #LIMITE_MAX}
     */
    public List<Reservation> lister(String utilisateur, EtatReservation etat, int limite) {
        List<Bson> filtres = new ArrayList<>();
        if (utilisateur != null) {
            Optional<ObjectId> u = Identifiants.depuisApi(Identifiants.UTILISATEUR, utilisateur);
            if (u.isEmpty()) return List.of();
            filtres.add(Filters.eq("utilisateur", u.get()));
        }
        if (etat != null) {
            filtres.add(Filters.eq("etat", etat.valeur()));
        }
        Bson filtre = filtres.isEmpty() ? new Document() : Filters.and(filtres);
        List<Reservation> liste = new ArrayList<>();
        for (Document d : collection().find(filtre).sort(Sorts.descending("creee_le"))
                .limit(Math.max(1, Math.min(LIMITE_MAX, limite)))) {
            liste.add(ConversionActivite.versReservation(d));
        }
        return liste;
    }

    /** La réservation active ou en_cours du conducteur, s'il en a une. */
    public Optional<Reservation> ouverteDuUtilisateur(String utilisateur) {
        return Identifiants.depuisApi(Identifiants.UTILISATEUR, utilisateur)
                .map(u -> collection().find(Filters.and(Filters.eq("utilisateur", u), filtreOuvertes())).first())
                .map(ConversionActivite::versReservation);
    }

    /** La réservation active ou en_cours sur la place, s'il y en a une (plan : place « réservée »). */
    public Optional<Reservation> ouverteSurPlace(String place) {
        return Optional.ofNullable(collection().find(Filters.and(Filters.eq("place", place), filtreOuvertes())).first())
                .map(ConversionActivite::versReservation);
    }

    /** Toutes les réservations ouvertes : pour le plan et l'expiration des réservations actives. */
    public List<Reservation> ouvertes() {
        List<Reservation> liste = new ArrayList<>();
        for (Document d : collection().find(filtreOuvertes())) {
            liste.add(ConversionActivite.versReservation(d));
        }
        return liste;
    }

    /**
     * Remplace la réservation, seulement si son état en base est encore {@code etatAttendu}.
     * Évite qu'une annulation et une arrivée simultanées s'écrasent.
     *
     * @return faux si la réservation n'existe pas ou si son état a changé entre-temps
     * @throws ReservationEnConflit si le nouvel état est ouvert et entre en conflit
     */
    public boolean mettreAJour(Reservation r, EtatReservation etatAttendu) {
        ObjectId id = Identifiants.exiger(Identifiants.RESERVATION, r.id());
        try {
            return collection().replaceOne(
                    Filters.and(Filters.eq("_id", id), Filters.eq("etat", etatAttendu.valeur())),
                    ConversionActivite.versDocument(r, id)).getMatchedCount() == 1;
        } catch (MongoWriteException e) {
            throw traduire(e);
        }
    }

    private static RuntimeException traduire(MongoWriteException e) {
        if (e.getError().getCategory() != ErrorCategory.DUPLICATE_KEY) {
            return e;
        }
        String message = e.getError().getMessage();
        if (message.contains(INDEX_UTILISATEUR_OUVERTE)) return new ReservationEnConflit(ReservationEnConflit.Motif.UTILISATEUR);
        if (message.contains(INDEX_PLACE_OUVERTE)) return new ReservationEnConflit(ReservationEnConflit.Motif.PLACE);
        return e;
    }
}
