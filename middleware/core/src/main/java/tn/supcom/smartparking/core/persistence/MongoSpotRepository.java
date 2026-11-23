package tn.supcom.smartparking.core.persistence;

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
import tn.supcom.smartparking.core.model.Spot;
import tn.supcom.smartparking.core.model.SpotState;

/** {@link SpotRepository} backed by the {@code spots} collection (contract §2.14), indexed on {@code parking}. */
@ApplicationScoped
public class MongoSpotRepository implements SpotRepository {

    static final String COLLECTION = "spots";

    @Inject
    MongoConnection connection;

    private volatile boolean indexCreated;

    private MongoCollection<Document> collection() {
        MongoCollection<Document> c = connection.database().getCollection(COLLECTION);
        if (!indexCreated) {
            // No effect if the index already exists
            c.createIndex(Indexes.ascending("parking"), new IndexOptions().name("parking"));
            indexCreated = true;
        }
        return c;
    }

    @Override
    public List<Spot> findByParking(String parkingId) {
        List<Spot> spots = new ArrayList<>();
        for (Document d : collection().find(Filters.eq("parking", parkingId)).sort(Sorts.ascending("_id"))) {
            spots.add(MongoMapper.toSpot(d));
        }
        return spots;
    }

    @Override
    public Optional<Spot> findById(String id) {
        return Optional.ofNullable(collection().find(Filters.eq("_id", id)).first()).map(MongoMapper::toSpot);
    }

    @Override
    public void save(Spot spot) {
        collection().replaceOne(Filters.eq("_id", spot.id()), MongoMapper.toDocument(spot),
                new ReplaceOptions().upsert(true));
    }

    @Override
    public void replacePolygons(String parkingId, List<Spot> spots) {
        List<String> ids = new ArrayList<>();
        for (Spot s : spots) {
            ids.add(s.id());
            collection().updateOne(Filters.eq("_id", s.id()),
                    Updates.combine(
                            Updates.set("parking", parkingId),
                            Updates.set("type", s.type().value()),
                            Updates.set("max_duration_min", s.maxDurationMin()),
                            Updates.set("polygon", MongoMapper.polygonToList(s.polygon())),
                            // only when the spot is new:
                            Updates.setOnInsert("state", SpotState.FREE.value()),
                            Updates.setOnInsert("since", null),
                            Updates.setOnInsert("rect_cm", null),
                            Updates.setOnInsert("node", null)),
                    new UpdateOptions().upsert(true));
        }
        collection().deleteMany(Filters.and(Filters.eq("parking", parkingId), Filters.nin("_id", ids)));
    }
}
