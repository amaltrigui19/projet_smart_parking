package tn.supcom.smartparking.core.persistence;

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
import tn.supcom.smartparking.core.model.Reservation;
import tn.supcom.smartparking.core.model.ReservationState;

/**
 * {@link ReservationRepository} backed by the {@code reservations} collection (contract §2.14).
 *
 * <p>Contract indexes: {@code user + created_at} (history) and {@code spot + state}.
 * Two extra <b>partial</b> unique indexes, limited to open states (active, in progress): the database itself
 * guarantees "one open reservation per driver" and "per spot", even when two requests arrive at the same time.
 * The same indexes are created by {@code server/mongo/index.js} (same names).
 */
@ApplicationScoped
public class MongoReservationRepository implements ReservationRepository {

    static final String COLLECTION = "reservations";
    static final String INDEX_USER_OPEN = "user_open_unique";
    static final String INDEX_SPOT_OPEN = "spot_open_unique";

    @Inject
    MongoConnection connection;

    private volatile boolean indexCreated;

    private MongoCollection<Document> collection() {
        MongoCollection<Document> c = connection.database().getCollection(COLLECTION);
        if (!indexCreated) {
            c.createIndex(Indexes.compoundIndex(Indexes.ascending("user"), Indexes.descending("created_at")),
                    new IndexOptions().name("user_created_at"));
            c.createIndex(Indexes.ascending("spot", "state"), new IndexOptions().name("spot_state"));
            c.createIndex(Indexes.ascending("user"), new IndexOptions().name(INDEX_USER_OPEN)
                    .unique(true).partialFilterExpression(openFilter()));
            c.createIndex(Indexes.ascending("spot"), new IndexOptions().name(INDEX_SPOT_OPEN)
                    .unique(true).partialFilterExpression(openFilter()));
            indexCreated = true;
        }
        return c;
    }

    /** {state: {$in: ["active", "in_progress"]}}: identical to the filter in index.js. */
    static Bson openFilter() {
        List<String> open = new ArrayList<>();
        for (ReservationState s : ReservationState.values()) {
            if (s.isOpen()) open.add(s.value());
        }
        return Filters.in("state", open);
    }

    @Override
    public Reservation create(Reservation r) {
        ObjectId id = new ObjectId();
        try {
            collection().insertOne(ActivityMapper.toDocument(r, id));
        } catch (MongoWriteException e) {
            throw translate(e);
        }
        return r.withId(ApiIds.toApi(ApiIds.RESERVATION, id));
    }

    @Override
    public Optional<Reservation> findById(String id) {
        return ApiIds.fromApi(ApiIds.RESERVATION, id)
                .map(oid -> collection().find(Filters.eq("_id", oid)).first())
                .map(ActivityMapper::toReservation);
    }

    @Override
    public List<Reservation> list(String userId, ReservationState state, int limit) {
        List<Bson> filters = new ArrayList<>();
        if (userId != null) {
            Optional<ObjectId> u = ApiIds.fromApi(ApiIds.USER, userId);
            if (u.isEmpty()) return List.of();
            filters.add(Filters.eq("user", u.get()));
        }
        if (state != null) {
            filters.add(Filters.eq("state", state.value()));
        }
        Bson filter = filters.isEmpty() ? new Document() : Filters.and(filters);
        List<Reservation> list = new ArrayList<>();
        for (Document d : collection().find(filter).sort(Sorts.descending("created_at"))
                .limit(Math.max(1, Math.min(MAX_LIMIT, limit)))) {
            list.add(ActivityMapper.toReservation(d));
        }
        return list;
    }

    @Override
    public Optional<Reservation> findOpenByUser(String userId) {
        return ApiIds.fromApi(ApiIds.USER, userId)
                .map(u -> collection().find(Filters.and(Filters.eq("user", u), openFilter())).first())
                .map(ActivityMapper::toReservation);
    }

    @Override
    public Optional<Reservation> findOpenBySpot(String spotId) {
        return Optional.ofNullable(collection().find(Filters.and(Filters.eq("spot", spotId), openFilter())).first())
                .map(ActivityMapper::toReservation);
    }

    @Override
    public List<Reservation> findOpen() {
        List<Reservation> list = new ArrayList<>();
        for (Document d : collection().find(openFilter())) {
            list.add(ActivityMapper.toReservation(d));
        }
        return list;
    }

    @Override
    public boolean update(Reservation r, ReservationState expectedState) {
        ObjectId id = ApiIds.require(ApiIds.RESERVATION, r.id());
        try {
            return collection().replaceOne(
                    Filters.and(Filters.eq("_id", id), Filters.eq("state", expectedState.value())),
                    ActivityMapper.toDocument(r, id)).getMatchedCount() == 1;
        } catch (MongoWriteException e) {
            throw translate(e);
        }
    }

    private static RuntimeException translate(MongoWriteException e) {
        if (e.getError().getCategory() != ErrorCategory.DUPLICATE_KEY) {
            return e;
        }
        String message = e.getError().getMessage();
        if (message.contains(INDEX_USER_OPEN)) return new ReservationConflictException(ReservationConflictException.Reason.USER);
        if (message.contains(INDEX_SPOT_OPEN)) return new ReservationConflictException(ReservationConflictException.Reason.SPOT);
        return e;
    }
}
