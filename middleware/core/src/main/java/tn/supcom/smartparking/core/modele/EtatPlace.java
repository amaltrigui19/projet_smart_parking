package tn.supcom.smartparking.core.modele;

/** État d'une place, envoyé par le Pi (contrat 5.1). */
public enum EtatPlace {
    LIBRE, OCCUPEE;

    public String valeur() {
        return name().toLowerCase();
    }

    public static EtatPlace depuis(String valeur) {
        return valueOf(valeur.toUpperCase());
    }
}