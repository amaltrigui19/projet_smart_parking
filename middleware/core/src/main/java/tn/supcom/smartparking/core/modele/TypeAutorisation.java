package tn.supcom.smartparking.core.modele;

/** Autorisation donnée par l'exploitant pour utiliser une place spéciale (contrat §2.11). */
public enum TypeAutorisation {
    PMR, ELECTRIQUE;

    public String valeur() {
        return name().toLowerCase();
    }

    public static TypeAutorisation depuis(String valeur) {
        return valueOf(valeur.toUpperCase()); // lève IllegalArgumentException si inconnu
    }
}
