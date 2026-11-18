package tn.supcom.smartparking.core.modele;

/** Qui a détecté l'anomalie : le Pi (cheval, zone_interdite) ou le serveur (ventouse, place_speciale). */
public enum SourceEvenement {
    PI, SERVEUR;

    public String valeur() {
        return name().toLowerCase();
    }

    public static SourceEvenement depuis(String valeur) {
        return valueOf(valeur.toUpperCase());
    }
}
