package tn.supcom.smartparking;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import java.io.StringReader;
import tn.supcom.smartparking.erreurs.ErreurApi;

/** Lecture du corps JSON d'une requête : 400 requete_invalide s'il est vide, mal formé ou pas un objet. */
public final class CorpsJson {

    private CorpsJson() {
    }

    public static JsonObject lireObjet(String corps) {
        if (corps == null || corps.isBlank()) {
            throw ErreurApi.requeteInvalide("Le corps de la requête est vide.");
        }
        try (JsonReader lecteur = Json.createReader(new StringReader(corps))) {
            JsonValue v = lecteur.readValue();
            if (v.getValueType() != JsonValue.ValueType.OBJECT) {
                throw ErreurApi.requeteInvalide("Le corps doit être un objet JSON.");
            }
            return v.asJsonObject();
        } catch (JsonException e) {
            throw ErreurApi.requeteInvalide("JSON mal formé.");
        }
    }
}
