package tn.supcom.smartparking.core.modele;

import java.time.Instant;

/**
 * Une réservation, telle que stockée dans la collection {@code reservations} (contrat §2.14).
 *
 * @param id          identifiant de l'API (« r_ » + ObjectId) ; null avant l'enregistrement
 * @param utilisateur identifiant de l'API du conducteur (« u_… »)
 * @param place       identifiant de la place (« B2 »)
 * @param expireLe    au-delà, une réservation encore active passe à « expiree »
 * @param debut       arrivée de la voiture ; null tant qu'elle n'est pas là
 * @param fin         départ de la voiture ; null tant qu'elle est là
 * @param montant     null tant que la réservation n'est pas payée
 * @param paiement    null tant que la réservation n'est pas payée
 */
public record Reservation(String id, String utilisateur, String place, EtatReservation etat,
                          Instant creeeLe, Instant expireLe, Instant debut, Instant fin,
                          Double montant, String devise, Paiement paiement) {

    public Reservation {
        if (utilisateur == null || utilisateur.isBlank()) throw new IllegalArgumentException("réservation : utilisateur manquant");
        if (place == null || place.isBlank()) throw new IllegalArgumentException("réservation : place manquante");
        if (etat == null) throw new IllegalArgumentException("réservation : etat manquant");
        if (creeeLe == null || expireLe == null) throw new IllegalArgumentException("réservation : creee_le et expire_le obligatoires");
        if (expireLe.isBefore(creeeLe)) throw new IllegalArgumentException("réservation : expire_le avant creee_le");
        if (debut != null && fin != null && fin.isBefore(debut)) throw new IllegalArgumentException("réservation : fin avant debut");
        if (montant != null && montant < 0) throw new IllegalArgumentException("réservation : montant négatif");
        if (etat == EtatReservation.PAYEE && paiement == null) throw new IllegalArgumentException("réservation payée sans paiement");
    }

    /** Nouvelle réservation à l'état « active » (POST /api/reservations), valable {@code delaiMin} minutes. */
    public static Reservation nouvelle(String utilisateur, String place, Instant maintenant, int delaiMin) {
        return new Reservation(null, utilisateur, place, EtatReservation.ACTIVE, maintenant,
                maintenant.plusSeconds(60L * delaiMin), null, null, null, null, null);
    }

    public Reservation avecId(String nouvelId) {
        return new Reservation(nouvelId, utilisateur, place, etat, creeeLe, expireLe, debut, fin, montant, devise, paiement);
    }

    public Reservation avecEtat(EtatReservation nouvelEtat) {
        return new Reservation(id, utilisateur, place, nouvelEtat, creeeLe, expireLe, debut, fin, montant, devise, paiement);
    }
}
