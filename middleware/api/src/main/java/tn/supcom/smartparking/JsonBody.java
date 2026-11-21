package tn.supcom.smartparking;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import java.io.StringReader;
import tn.supcom.smartparking.core.exception.InvalidRequestException;

/** Reads a JSON request body: 400 requete_invalide if it is empty, malformed or not an object. */
public final class JsonBody {

    private JsonBody() {
    }

    public static JsonObject readObject(String body) {
        if (body == null || body.isBlank()) {
            throw new InvalidRequestException("Le corps de la requête est vide.");
        }
        try (JsonReader reader = Json.createReader(new StringReader(body))) {
            JsonValue v = reader.readValue();
            if (v.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new InvalidRequestException("Le corps doit être un objet JSON.");
            }
            return v.asJsonObject();
        } catch (JsonException e) {
            throw new InvalidRequestException("JSON mal formé.");
        }
    }
}
