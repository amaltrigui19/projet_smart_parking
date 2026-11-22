package tn.supcom.smartparking.iam.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Set;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import tn.supcom.smartparking.iam.model.Role;
import tn.supcom.smartparking.iam.model.User;

/** Conversion document MongoDB ↔ User (le test réel avec MongoDB est fait dans Docker). */
class MongoUserRepositoryTest {

    @Test
    void documentEnAnglaisEtAllerRetour() {
        ObjectId id = new ObjectId();
        Instant cree = Instant.parse("2026-11-22T10:00:00Z");
        Document d = MongoUserRepository.toDocument(id, "Sara", "sara@exemple.tn", "$argon2id$h", Role.OPERATOR, cree);

        assertEquals(Set.of("_id", "name", "email", "password_hash", "role", "active", "created_at"), d.keySet());
        assertEquals("OPERATOR", d.getString("role"));
        User u = MongoUserRepository.toUser(d);
        assertEquals("u_" + id.toHexString(), u.id());
        assertEquals("$argon2id$h", u.passwordHash());
        assertEquals(Role.OPERATOR, u.role());
        assertTrue(u.active());
        assertEquals(cree, u.createdAt());
        assertFalse(u.toString().contains("argon2"), "l'empreinte n'apparaît pas dans toString");
    }

    @Test
    void identifiantsDeLApi() {
        ObjectId id = new ObjectId();
        assertEquals(id, MongoUserRepository.objectId("u_" + id.toHexString()).orElseThrow());
        assertTrue(MongoUserRepository.objectId(id.toHexString()).isEmpty());
        assertTrue(MongoUserRepository.objectId("u_pas-hexa").isEmpty());
        assertTrue(MongoUserRepository.objectId(null).isEmpty());
    }
}
