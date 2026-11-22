package tn.supcom.smartparking.core.modele;

import java.util.Set;

/**
 * États d'une réservation (contrat §2.8) :
 * active → en_cours → terminee → payee ; active → annulee ou expiree ; en_cours → payee.
 */
public enum EtatReservation {
    ACTIVE, EN_COURS, TERMINEE, PAYEE, ANNULEE, EXPIREE;

    /** Un conducteur a au plus une réservation dans ces états, et une place aussi. */
    public static final Set<EtatReservation> OUVERTS = Set.of(ACTIVE, EN_COURS);

    public String valeur() {
        return name().toLowerCase();
    }

    public static EtatReservation depuis(String valeur) {
        return valueOf(valeur.toUpperCase());
    }

    public boolean ouvert() {
        return OUVERTS.contains(this);
    }
}
