package tn.supcom.smartparking.core.persistence;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Single MongoDB connection shared by every repository.
 * Settings come from MicroProfile Config, i.e. the WildFly environment variables set in
 * server/docker-compose.yml. The variable names will follow the English contract once it is agreed.
 */
@ApplicationScoped
public class MongoConnection {

    @Inject
    @ConfigProperty(name = "MONGO_HOTE", defaultValue = "mongo")
    String host;

    @Inject
    @ConfigProperty(name = "MONGO_PORT", defaultValue = "27017")
    int port;

    @Inject
    @ConfigProperty(name = "MONGO_UTILISATEUR")
    Optional<String> user;

    @Inject
    @ConfigProperty(name = "MONGO_MOT_DE_PASSE")
    Optional<String> password;

    @Inject
    @ConfigProperty(name = "MONGO_BASE", defaultValue = "smartparking")
    String databaseName;

    private MongoClient client;

    public synchronized MongoDatabase database() {
        if (client == null) {
            MongoClientSettings.Builder settings = MongoClientSettings.builder()
                    .applyToClusterSettings(c -> c
                            .hosts(List.of(new ServerAddress(host, port)))
                            .serverSelectionTimeout(5, TimeUnit.SECONDS));
            user.filter(u -> !u.isBlank()).ifPresent(u ->
                    // The account created by the mongo image authenticates against the "admin" database
                    settings.credential(MongoCredential.createCredential(u, "admin", password.orElse("").toCharArray())));
            client = MongoClients.create(settings.build());
        }
        return client.getDatabase(databaseName);
    }

    @PreDestroy
    synchronized void close() {
        if (client != null) {
            client.close();
            client = null;
        }
    }
}
