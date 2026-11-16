package tn.supcom.smartparking.core.donnees;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Connexion unique à MongoDB, partagée par tous les DAO.
 * Configurée par les variables d'environnement de WildFly (server/docker-compose.yml) :
 * MONGO_HOTE, MONGO_PORT, MONGO_UTILISATEUR, MONGO_MOT_DE_PASSE, MONGO_BASE.
 */
@ApplicationScoped
public class ConnexionMongo {

    private MongoClient client;

    public synchronized MongoDatabase base() {
        if (client == null) {
            MongoClientSettings.Builder reglages = MongoClientSettings.builder()
                    .applyToClusterSettings(c -> c
                            .hosts(List.of(new ServerAddress(env("MONGO_HOTE", "mongo"),
                                    Integer.parseInt(env("MONGO_PORT", "27017")))))
                            .serverSelectionTimeout(5, TimeUnit.SECONDS));
            String utilisateur = env("MONGO_UTILISATEUR", "");
            if (!utilisateur.isEmpty()) {
                // Le compte créé par l'image mongo s'authentifie sur la base « admin »
                reglages.credential(MongoCredential.createCredential(
                        utilisateur, "admin", env("MONGO_MOT_DE_PASSE", "").toCharArray()));
            }
            client = MongoClients.create(reglages.build());
        }
        return client.getDatabase(env("MONGO_BASE", "smartparking"));
    }

    @PreDestroy
    synchronized void fermer() {
        if (client != null) { client.close(); client = null; }
    }

    private static String env(String nom, String defaut) {
        String v = System.getenv(nom);
        return v == null || v.isBlank() ? defaut : v;
    }
}