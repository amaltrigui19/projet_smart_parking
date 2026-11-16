package tn.supcom.smartparking.iam;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.ObjectId;

/**
 * Accès à la collection {@code utilisateurs} (contrat §2.14) :
 * {@code _id, nom, email, mot_de_passe_hash, role, actif, cree_le}, index unique sur {@code email}.
 */
@ApplicationScoped
public class DepotUtilisateurs {

    static final String COLLECTION = "utilisateurs";

    @Inject
    ConnexionMongo connexion;

    private volatile boolean indexCree;

    private MongoCollection<Document> collection() {
        MongoCollection<Document> c = connexion.base().getCollection(COLLECTION);
        if (!indexCree) {
            // Sans effet si l'index existe déjà. C'est lui qui garantit « un email = un compte ».
            c.createIndex(Indexes.ascending("email"), new IndexOptions().unique(true).name("email_unique"));
            indexCree = true;
        }
        return c;
    }

    /** L'email doit déjà être normalisé (voir ValidationInscription.normaliserEmail). */
    public Optional<Utilisateur> parEmail(String email) {
        return Optional.ofNullable(collection().find(Filters.eq("email", email)).first()).map(DepotUtilisateurs::versUtilisateur);
    }

    /** Identifiant de l'API (« u_ » + ObjectId) ; vide s'il est mal formé ou inconnu. */
    public Optional<Utilisateur> parId(String id) {
        if (id == null || !id.startsWith(Utilisateur.PREFIXE_ID)) {
            return Optional.empty();
        }
        String hex = id.substring(Utilisateur.PREFIXE_ID.length());
        if (!ObjectId.isValid(hex)) {
            return Optional.empty();
        }
        return Optional.ofNullable(collection().find(Filters.eq("_id", new ObjectId(hex))).first())
                .map(DepotUtilisateurs::versUtilisateur);
    }

    /** Crée le compte ; lève ErreurIam 409 email_existant si l'email est déjà pris. */
    public Utilisateur creer(String nom, String email, String motDePasseHash, Role role) {
        ObjectId id = new ObjectId();
        Instant maintenant = Instant.now();
        Document doc = new Document("_id", id)
                .append("nom", nom)
                .append("email", email)
                .append("mot_de_passe_hash", motDePasseHash)
                .append("role", role.name())
                .append("actif", true)
                .append("cree_le", Date.from(maintenant));
        try {
            collection().insertOne(doc);
        } catch (MongoWriteException e) {
            if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                throw ErreurIam.emailExistant();
            }
            throw e;
        }
        return versUtilisateur(doc);
    }

    static Utilisateur versUtilisateur(Document d) {
        Date cree = d.getDate("cree_le");
        return new Utilisateur(
                Utilisateur.PREFIXE_ID + d.getObjectId("_id").toHexString(),
                d.getString("nom"),
                d.getString("email"),
                d.getString("mot_de_passe_hash"),
                Role.valueOf(d.getString("role")),
                Boolean.TRUE.equals(d.getBoolean("actif")),
                cree == null ? null : cree.toInstant());
    }
}
