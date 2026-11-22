package tn.supcom.smartparking.core.modele;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelesActiviteTest {

    private static final String SARA = "u_6543a1f0aabbccddeeff0011";
    private static final Instant T = Instant.parse("2026-11-20T10:00:00Z");

    @Test
    void autorisationValideEntreDebutEtFin() {
        Autorisation a = new Autorisation(null, SARA, TypeAutorisation.PMR, T, T.plusSeconds(3600), null);
        assertFalse(a.valideLe(T.minusSeconds(1)));
        assertTrue(a.valideLe(T));
        assertTrue(a.valideLe(T.plusSeconds(3599)));
        assertFalse(a.valideLe(T.plusSeconds(3600)));   // fin exclue
        assertTrue(new Autorisation(null, SARA, TypeAutorisation.PMR, T, null, null).valideLe(T.plusSeconds(1_000_000)));
    }

    @Test
    void autorisationFinAvantDebutRefusee() {
        assertThrows(IllegalArgumentException.class,
                () -> new Autorisation(null, SARA, TypeAutorisation.PMR, T, T, null));
    }

    @Test
    void nouvelleReservationActiveQuinzeMinutes() {
        Reservation r = Reservation.nouvelle(SARA, "B2", T, 15);
        assertEquals(EtatReservation.ACTIVE, r.etat());
        assertEquals(T.plusSeconds(900), r.expireLe());
        assertTrue(r.etat().ouvert());
    }

    @Test
    void etatsOuverts() {
        assertTrue(EtatReservation.EN_COURS.ouvert());
        for (EtatReservation e : List.of(EtatReservation.TERMINEE, EtatReservation.PAYEE,
                EtatReservation.ANNULEE, EtatReservation.EXPIREE)) {
            assertFalse(e.ouvert(), e.name());
        }
        assertEquals(EtatReservation.EN_COURS, EtatReservation.depuis("en_cours"));
    }

    @Test
    void reservationPayeeSansPaiementRefusee() {
        assertThrows(IllegalArgumentException.class, () -> new Reservation(null, SARA, "B2", EtatReservation.PAYEE,
                T, T.plusSeconds(900), T, T.plusSeconds(3600), 1.5, "TND", null));
    }

    @Test
    void evenementSelonLeType() {
        assertThrows(IllegalArgumentException.class,   // cheval sans place
                () -> Evenement.nouveau("P1", TypeEvenement.CHEVAL, List.of(), null, T, SourceEvenement.PI, null));
        assertThrows(IllegalArgumentException.class,   // zone_interdite sans zone
                () -> Evenement.nouveau("P1", TypeEvenement.ZONE_INTERDITE, null, null, T, SourceEvenement.PI, null));
        Evenement e = Evenement.nouveau("P1", TypeEvenement.ZONE_INTERDITE, null, "allee1", T, SourceEvenement.PI, null);
        assertEquals(List.of(), e.places());
        assertFalse(e.traite());
    }

    @Test
    void detailsCopiesEtNonModifiables() {
        Map<String, Object> details = new HashMap<>();
        details.put("duree_min", 121);
        details.put("note", null);   // une valeur null est acceptée
        Evenement e = Evenement.nouveau("P1", TypeEvenement.VENTOUSE, List.of("A2"), null, T, SourceEvenement.SERVEUR, details);
        details.put("duree_min", 5);
        assertEquals(121, e.details().get("duree_min"));
        assertThrows(UnsupportedOperationException.class, () -> e.details().put("x", 1));
    }

    @Test
    void typesEnMinusculesCommeLeContrat() {
        assertEquals("zone_interdite", TypeEvenement.ZONE_INTERDITE.valeur());
        assertEquals("place_speciale", TypeEvenement.PLACE_SPECIALE.valeur());
        assertEquals("electrique", TypeAutorisation.ELECTRIQUE.valeur());
        assertEquals(SourceEvenement.PI, SourceEvenement.depuis("pi"));
    }
}
