package tn.supcom.smartparking.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

class ApiIdsTest {

    private static final ObjectId ID = new ObjectId("6543a1f0aabbccddeeff0011");

    @Test
    void roundTrip() {
        String api = ApiIds.toApi(ApiIds.RESERVATION, ID);
        assertEquals("r_6543a1f0aabbccddeeff0011", api);
        assertEquals(Optional.of(ID), ApiIds.fromApi(ApiIds.RESERVATION, api));
    }

    @Test
    void wrongPrefixOrMalformed() {
        assertTrue(ApiIds.fromApi(ApiIds.RESERVATION, "u_6543a1f0aabbccddeeff0011").isEmpty());
        assertTrue(ApiIds.fromApi(ApiIds.EVENT, "evt_8f3a").isEmpty());
        assertTrue(ApiIds.fromApi(ApiIds.EVENT, null).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ApiIds.require(ApiIds.USER, "sara"));
    }

    @Test
    void optionalReference() {
        assertNull(ApiIds.optional(ApiIds.USER, null));
        assertNull(ApiIds.toApi(ApiIds.USER, null));
    }

    @Test
    void openReservationsFilter() {
        // must stay identical to the partial filter in server/mongo/index.js
        assertEquals("{\"state\": {\"$in\": [\"active\", \"in_progress\"]}}",
                MongoReservationRepository.openFilter().toBsonDocument().toJson());
    }
}
