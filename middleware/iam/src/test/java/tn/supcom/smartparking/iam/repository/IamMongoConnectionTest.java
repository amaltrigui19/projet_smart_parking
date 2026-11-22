package tn.supcom.smartparking.iam.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IamMongoConnectionTest {

    private static IamMongoConnection connexion(String user, String password) {
        IamMongoConnection c = new IamMongoConnection();
        c.host = "mongo";
        c.port = 27017;
        c.user = Optional.ofNullable(user);
        c.password = Optional.ofNullable(password);
        c.databaseName = "smartparking";
        c.authDatabase = "admin";
        return c;
    }

    @Test
    void avecCompteAuthentificationSurAdmin() {
        MongoClientSettings s = connexion("smartparking", "secret-de-test").settings();
        assertEquals(List.of(new ServerAddress("mongo", 27017)), s.getClusterSettings().getHosts());
        assertEquals("smartparking", s.getCredential().getUserName());
        assertEquals("admin", s.getCredential().getSource());
        assertEquals("smartparking-iam", s.getApplicationName());
    }

    @Test
    void sansCompteOuCompteVidePasDAuthentification() {
        assertNull(connexion(null, null).settings().getCredential());
        assertNull(connexion("  ", null).settings().getCredential());
    }
}
