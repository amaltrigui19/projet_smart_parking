package tn.supcom.smartparking;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import java.io.StringReader;
import tn.supcom.smartparking.iam.ErreurIam;

/** Lecture du corps JSON d'une requête : 400 requete_invalide s'il est vide, mal formé ou pas un objet. */
public final class CorpsJson {

    private CorpsJson() {
    }

    public static JsonObject lireObjet(String corps) {
        if (corps == null || corps.isBlank()) {
            throw ErreurIam.requeteInvalide("Le corps de la requête est vide.");
        }
        try (JsonReader lecteur = Json.createReader(new StringReader(corps))) {
            JsonValue v = lecteur.readValue();
            if (v.getValueType() != JsonValue.ValueType.OBJECT) {
                throw ErreurIam.requeteInvalide("Le corps doit être un objet JSON.");
            }
            return v.asJsonObject();
        } catch (JsonException e) {
            throw ErreurIam.requeteInvalide("JSON mal formé.");
        }
    }
}
