package tn.supcom.smartparking.core.persistence;

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
import tn.supcom.smartparking.core.model.Event;
import tn.supcom.smartparking.core.model.EventType;

/**
 * {@link EventRepository} backed by the {@code events} collection (contract §2.14),
 * indexes {@code handled + start} and {@code type + start}. The same indexes are created by
 * {@code server/mongo/index.js}.
 */
@ApplicationScoped
public class MongoEventRepository implements EventRepository {

    static final String COLLECTION = "events";

    @Inject
    MongoConnection connection;

    private volatile boolean indexCreated;

    private MongoCollection<Document> collection() {
        MongoCollection<Document> c = connection.database().getCollection(COLLECTION);
        if (!indexCreated) {
            c.createIndex(Indexes.compoundIndex(Indexes.ascending("handled"), Indexes.descending("start")),
                    new IndexOptions().name("handled_start"));
            c.createIndex(Indexes.compoundIndex(Indexes.ascending("type"), Indexes.descending("start")),
                    new IndexOptions().name("type_start"));
            indexCreated = true;
        }
        return c;
    }

    @Override
    public Event create(Event e) {
        ObjectId id = new ObjectId();
        collection().insertOne(ActivityMapper.toDocument(e, id));
        return e.withId(ApiIds.toApi(ApiIds.EVENT, id));
    }

    @Override
    public Optional<Event> findById(String id) {
        return ApiIds.fromApi(ApiIds.EVENT, id)
                .map(oid -> collection().find(Filters.eq("_id", oid)).first())
                .map(ActivityMapper::toEvent);
    }

    @Override
    public List<Event> list(Boolean handled, EventType type, Instant from, Instant to, int limit) {
        List<Bson> filters = new ArrayList<>();
        if (handled != null) filters.add(Filters.eq("handled", handled));
        if (type != null) filters.add(Filters.eq("type", type.value()));
        if (from != null) filters.add(Filters.gte("start", Date.from(from)));
        if (to != null) filters.add(Filters.lt("start", Date.from(to)));
        Bson filter = filters.isEmpty() ? new Document() : Filters.and(filters);
        List<Event> list = new ArrayList<>();
        for (Document d : collection().find(filter).sort(Sorts.descending("start"))
                .limit(Math.max(1, Math.min(MAX_LIMIT, limit)))) {
            list.add(ActivityMapper.toEvent(d));
        }
        return list;
    }

    @Override
    public long countUnhandled() {
        return collection().countDocuments(Filters.eq("handled", false));
    }

    @Override
    public Optional<Event> markHandled(String id, boolean handled, String operatorId, String comment, Instant now) {
        Optional<ObjectId> oid = ApiIds.fromApi(ApiIds.EVENT, id);
        if (oid.isEmpty()) {
            return Optional.empty();
        }
        Bson update = Updates.combine(
                Updates.set("handled", handled),
                Updates.set("handled_by", handled ? ApiIds.optional(ApiIds.USER, operatorId) : null),
                Updates.set("handled_at", handled ? Date.from(now) : null),
                Updates.set("comment", comment));
        return Optional.ofNullable(collection().findOneAndUpdate(Filters.eq("_id", oid.get()), update,
                        new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)))
                .map(ActivityMapper::toEvent);
    }

    @Override
    public boolean close(String id, Instant end) {
        Optional<ObjectId> oid = ApiIds.fromApi(ApiIds.EVENT, id);
        return oid.isPresent() && collection().updateOne(
                Filters.and(Filters.eq("_id", oid.get()), Filters.eq("end", null)),
                Updates.set("end", Date.from(end))).getModifiedCount() == 1;
    }
}
