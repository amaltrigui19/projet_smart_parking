package tn.supcom.smartparking.core.donnees;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

class IdentifiantsTest {

    private static final ObjectId ID = new ObjectId("6543a1f0aabbccddeeff0011");

    @Test
    void allerRetour() {
        String api = Identifiants.versApi(Identifiants.RESERVATION, ID);
        assertEquals("r_6543a1f0aabbccddeeff0011", api);
        assertEquals(Optional.of(ID), Identifiants.depuisApi(Identifiants.RESERVATION, api));
    }

    @Test
    void mauvaisPrefixeOuMalForme() {
        assertTrue(Identifiants.depuisApi(Identifiants.RESERVATION, "u_6543a1f0aabbccddeeff0011").isEmpty());
        assertTrue(Identifiants.depuisApi(Identifiants.EVENEMENT, "evt_8f3a").isEmpty());
        assertTrue(Identifiants.depuisApi(Identifiants.EVENEMENT, null).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> Identifiants.exiger(Identifiants.UTILISATEUR, "sara"));
    }

    @Test
    void referenceFacultative() {
        assertNull(Identifiants.facultatif(Identifiants.UTILISATEUR, null));
        assertNull(Identifiants.versApi(Identifiants.UTILISATEUR, null));
    }

    @Test
    void filtreDesReservationsOuvertes() {
        // doit rester identique au filtre partiel de server/mongo/index.js
        assertEquals("{\"etat\": {\"$in\": [\"active\", \"en_cours\"]}}",
                DepotReservations.filtreOuvertes().toBsonDocument().toJson());
    }
}
