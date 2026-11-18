package tn.supcom.smartparking.core.modele;

import java.time.Instant;

/**
 * Paiement simulé d'une réservation (contrat §2.8). Aucune donnée bancaire :
 * {@code moyen} vaut toujours « simule ».
 */
public record Paiement(String moyen, Instant payeLe, String recu) {
    public Paiement {
        if (moyen == null || moyen.isBlank()) throw new IllegalArgumentException("paiement : moyen manquant");
        if (payeLe == null) throw new IllegalArgumentException("paiement : paye_le manquant");
    }
}
