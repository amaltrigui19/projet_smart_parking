package tn.supcom.smartparking.core.modele;

/** Type d'une place ; valeur() est le texte stocké en base et échangé en JSON. */
public enum TypePlace {
    NORMALE, PMR, ELECTRIQUE;

    public String valeur() {
        return name().toLowerCase();
    }

    public static TypePlace depuis(String valeur) {
        return valueOf(valeur.toUpperCase()); // lève IllegalArgumentException si inconnu
    }
}