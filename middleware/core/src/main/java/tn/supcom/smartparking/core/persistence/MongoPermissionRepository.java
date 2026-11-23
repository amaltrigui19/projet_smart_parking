package tn.supcom.smartparking.core.persistence;

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
import tn.supcom.smartparking.core.model.Permission;
import tn.supcom.smartparking.core.model.PermissionType;

/**
 * {@link PermissionRepository} backed by the {@code autorisations} collection (contract §2.14),
 * index {@code utilisateur + type}. The same index is created by {@code server/mongo/index.js}
 * (same name, otherwise MongoDB refuses).
 */
@ApplicationScoped
public class MongoPermissionRepository implements PermissionRepository {

    static final String COLLECTION = "autorisations";

    @Inject
    MongoConnection connection;

    private volatile boolean indexCreated;

    private MongoCollection<Document> collection() {
        MongoCollection<Document> c = connection.database().getCollection(COLLECTION);
        if (!indexCreated) {
            c.createIndex(Indexes.ascending("utilisateur", "type"), new IndexOptions().name("utilisateur_type"));
            indexCreated = true;
        }
        return c;
    }

    @Override
    public Permission create(Permission p) {
        ObjectId id = new ObjectId();
        collection().insertOne(ActivityMapper.toDocument(p, id));
        return p.withId(ApiIds.toApi(ApiIds.PERMISSION, id));
    }

    @Override
    public List<Permission> findByUser(String userId) {
        List<Permission> list = new ArrayList<>();
        Optional<ObjectId> u = ApiIds.fromApi(ApiIds.USER, userId);
        if (u.isEmpty()) {
            return list;
        }
        for (Document d : collection().find(Filters.eq("utilisateur", u.get())).sort(Sorts.descending("debut"))) {
            list.add(ActivityMapper.toPermission(d));
        }
        return list;
    }

    @Override
    public boolean hasValid(String userId, PermissionType type, Instant instant) {
        Optional<ObjectId> u = ApiIds.fromApi(ApiIds.USER, userId);
        if (u.isEmpty()) {
            return false;
        }
        Date t = Date.from(instant);
        return collection().find(Filters.and(
                Filters.eq("utilisateur", u.get()),
                Filters.eq("type", type.value()),
                Filters.lte("debut", t),
                Filters.or(Filters.eq("fin", null), Filters.gt("fin", t)))).first() != null;
    }

    @Override
    public boolean delete(String userId, String id) {
        Optional<ObjectId> u = ApiIds.fromApi(ApiIds.USER, userId);
        Optional<ObjectId> p = ApiIds.fromApi(ApiIds.PERMISSION, id);
        if (u.isEmpty() || p.isEmpty()) {
            return false;
        }
        return collection().deleteOne(Filters.and(Filters.eq("_id", p.get()), Filters.eq("utilisateur", u.get())))
                .getDeletedCount() == 1;
    }
}
