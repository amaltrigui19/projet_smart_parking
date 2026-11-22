package tn.supcom.smartparking.iam.controllers;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.StringReader;
import tn.supcom.smartparking.iam.exceptions.IamException;

/** Lecture des corps JSON des requêtes : 400 {@code invalid_request} s'ils sont vides, mal formés ou pas des objets. */
public final class JsonBodies {

    private JsonBodies() {
    }

    public static JsonObject readObject(String body) {
        if (body == null || body.isBlank()) {
            throw IamException.invalidRequest("Le corps de la requête est vide.");
        }
        try (JsonReader reader = Json.createReader(new StringReader(body))) {
            JsonValue v = reader.readValue();
            if (v.getValueType() != JsonValue.ValueType.OBJECT) {
                throw IamException.invalidRequest("Le corps doit être un objet JSON.");
            }
            return v.asJsonObject();
        } catch (JsonException e) {
            throw IamException.invalidRequest("JSON mal formé.");
        }
    }

    /** Valeur texte d'un champ, null s'il est absent ; 400 s'il n'est pas un texte. */
    public static String text(JsonObject o, String key) {
        JsonValue v = o.get(key);
        if (v == null || v.getValueType() == JsonValue.ValueType.NULL) {
            return null;
        }
        if (v.getValueType() != JsonValue.ValueType.STRING) {
            throw IamException.invalidRequest("Le champ « " + key + " » doit être un texte.");
        }
        return ((JsonString) v).getString();
    }

    /** Mot de passe en char[] (effaçable), null s'il est absent. */
    public static char[] secret(JsonObject o, String key) {
        String s = text(o, key);
        return s == null ? null : s.toCharArray();
    }
}
