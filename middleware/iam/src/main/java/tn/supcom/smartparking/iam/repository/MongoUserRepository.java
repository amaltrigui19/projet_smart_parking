package tn.supcom.smartparking.iam.repository;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.ObjectId;
import tn.supcom.smartparking.core.donnees.ConnexionMongo;
import tn.supcom.smartparking.iam.exceptions.IamException;
import tn.supcom.smartparking.iam.model.Role;
import tn.supcom.smartparking.iam.model.User;

/**
 * Collection {@code users} : {@code _id, name, email, password_hash, role, active, created_at},
 * index unique sur {@code email} (« un email = un compte »).
 */
@ApplicationScoped
public class MongoUserRepository implements UserRepository {

    static final String COLLECTION = "users";

    @Inject
    ConnexionMongo connection;

    private volatile boolean indexCreated;

    MongoCollection<Document> collection() {
        MongoCollection<Document> c = connection.base().getCollection(COLLECTION);
        if (!indexCreated) {
            // Sans effet si l'index existe déjà.
            c.createIndex(Indexes.ascending("email"), new IndexOptions().unique(true).name("email_unique"));
            indexCreated = true;
        }
        return c;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(collection().find(Filters.eq("email", email)).first()).map(MongoUserRepository::toUser);
    }

    @Override
    public Optional<User> findById(String id) {
        return objectId(id).flatMap(oid ->
                Optional.ofNullable(collection().find(Filters.eq("_id", oid)).first()).map(MongoUserRepository::toUser));
    }

    @Override
    public User create(String name, String email, String passwordHash, Role role) {
        Document doc = toDocument(new ObjectId(), name, email, passwordHash, role, Instant.now());
        try {
            collection().insertOne(doc);
        } catch (MongoWriteException e) {
            if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                throw IamException.emailAlreadyExists();
            }
            throw e;
        }
        return toUser(doc);
    }

    @Override
    public void updatePasswordHash(String id, String passwordHash) {
        objectId(id).ifPresent(oid -> collection().updateOne(Filters.eq("_id", oid), Updates.set("password_hash", passwordHash)));
    }

    static Optional<ObjectId> objectId(String id) {
        if (id == null || !id.startsWith(User.ID_PREFIX)) {
            return Optional.empty();
        }
        String hex = id.substring(User.ID_PREFIX.length());
        return ObjectId.isValid(hex) ? Optional.of(new ObjectId(hex)) : Optional.empty();
    }

    static Document toDocument(ObjectId id, String name, String email, String passwordHash, Role role, Instant createdAt) {
        return new Document("_id", id)
                .append("name", name)
                .append("email", email)
                .append("password_hash", passwordHash)
                .append("role", role.name())
                .append("active", true)
                .append("created_at", Date.from(createdAt));
    }

    static User toUser(Document d) {
        Date created = d.getDate("created_at");
        return new User(
                User.ID_PREFIX + d.getObjectId("_id").toHexString(),
                d.getString("name"),
                d.getString("email"),
                d.getString("password_hash"),
                Role.valueOf(d.getString("role")),
                Boolean.TRUE.equals(d.getBoolean("active")),
                created == null ? null : created.toInstant());
    }
}
