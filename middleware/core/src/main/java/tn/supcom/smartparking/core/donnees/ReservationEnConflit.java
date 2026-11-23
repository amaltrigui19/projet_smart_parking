package tn.supcom.smartparking.core.donnees;

/**
 * La base a refusé une réservation ouverte (active ou en_cours) en double, grâce à un index unique partiel.
 * L'API la traduit en {@code 409 reservation_deja_active} ou {@code 409 place_indisponible} (contrat §2.8).
 */
public class ReservationEnConflit extends RuntimeException {

    public enum Motif {
        /** Le conducteur a déjà une réservation ouverte. */
        UTILISATEUR,
        /** La place a déjà une réservation ouverte. */
        PLACE
    }

    private final Motif motif;

    public ReservationEnConflit(Motif motif) {
        super(motif == Motif.UTILISATEUR ? "le conducteur a déjà une réservation ouverte"
                : "la place a déjà une réservation ouverte");
        this.motif = motif;
    }

    public Motif motif() {
        return motif;
    }
}
