package tn.supcom.smartparking.core.modele;

/** Type d'une anomalie (contrat §2.9) : les deux premiers viennent du Pi, les deux autres du serveur. */
public enum TypeEvenement {
    CHEVAL, ZONE_INTERDITE, VENTOUSE, PLACE_SPECIALE;

    public String valeur() {
        return name().toLowerCase();
    }

    public static TypeEvenement depuis(String valeur) {
        return valueOf(valeur.toUpperCase());
    }
}
