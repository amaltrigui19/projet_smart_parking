package tn.supcom.smartparking.iam.repository;

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
 * Connexion MongoDB propre à l'IAM (aucune dépendance vers le module core), ouverte au premier usage.
 * Réglages par MicroProfile Config, donc par les variables d'environnement de WildFly :
 * {@code MONGO_HOST} (mongo), {@code MONGO_PORT} (27017), {@code MONGO_USER}, {@code MONGO_PASSWORD},
 * {@code MONGO_DATABASE} (smartparking), {@code MONGO_AUTH_DATABASE} (admin, celle du compte créé par l'image mongo).
 */
@ApplicationScoped
public class IamMongoConnection {

    @Inject
    @ConfigProperty(name = "MONGO_HOST", defaultValue = "mongo")
    String host;

    @Inject
    @ConfigProperty(name = "MONGO_PORT", defaultValue = "27017")
    int port;

    @Inject
    @ConfigProperty(name = "MONGO_USER")
    Optional<String> user;

    @Inject
    @ConfigProperty(name = "MONGO_PASSWORD")
    Optional<String> password;

    @Inject
    @ConfigProperty(name = "MONGO_DATABASE", defaultValue = "smartparking")
    String databaseName;

    @Inject
    @ConfigProperty(name = "MONGO_AUTH_DATABASE", defaultValue = "admin")
    String authDatabase;

    private MongoClient client;

    public synchronized MongoDatabase database() {
        if (client == null) {
            client = MongoClients.create(settings());
        }
        return client.getDatabase(databaseName);
    }

    MongoClientSettings settings() {
        MongoClientSettings.Builder settings = MongoClientSettings.builder()
                .applicationName("smartparking-iam")
                .applyToClusterSettings(c -> c
                        .hosts(List.of(new ServerAddress(host, port)))
                        .serverSelectionTimeout(5, TimeUnit.SECONDS));
        user.filter(u -> !u.isBlank()).ifPresent(u ->
                settings.credential(MongoCredential.createCredential(u, authDatabase, password.orElse("").toCharArray())));
        return settings.build();
    }

    @PreDestroy
    synchronized void close() {
        if (client != null) {
            client.close();
            client = null;
        }
    }
}
