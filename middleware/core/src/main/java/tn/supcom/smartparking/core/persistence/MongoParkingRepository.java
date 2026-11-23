package tn.supcom.smartparking.core.persistence;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Date;
import java.util.Optional;
import org.bson.Document;
import tn.supcom.smartparking.core.model.Parking;

/** {@link ParkingRepository} backed by the {@code parkings} collection (contract §2.14). */
@ApplicationScoped
public class MongoParkingRepository implements ParkingRepository {

    static final String COLLECTION = "parkings";

    @Inject
    MongoConnection connection;

    private MongoCollection<Document> collection() {
        return connection.database().getCollection(COLLECTION);
    }

    @Override
    public Optional<Parking> findById(String id) {
        return Optional.ofNullable(collection().find(Filters.eq("_id", id)).first()).map(MongoMapper::toParking);
    }

    @Override
    public void save(Parking parking) {
        Document fields = MongoMapper.parkingFields(parking).append("updated_at", new Date());
        collection().updateOne(Filters.eq("_id", parking.id()), new Document("$set", fields),
                new UpdateOptions().upsert(true));
    }
}
