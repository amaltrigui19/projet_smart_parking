package tn.supcom.smartparking.core.donnees;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import tn.supcom.smartparking.core.modele.Autorisation;
import tn.supcom.smartparking.core.modele.EtatReservation;
import tn.supcom.smartparking.core.modele.Evenement;
import tn.supcom.smartparking.core.modele.Paiement;
import tn.supcom.smartparking.core.modele.Reservation;
import tn.supcom.smartparking.core.modele.SourceEvenement;
import tn.supcom.smartparking.core.modele.TypeAutorisation;
import tn.supcom.smartparking.core.modele.TypeEvenement;

class ConversionActiviteTest {

    private static final ObjectId ID = new ObjectId("64e0000000000000000b0001");
    private static final String SARA = "u_6543a1f0aabbccddeeff0011";
    private static final String EXPLOITANT = "u_6543a1f0aabbccddeeff0099";
    private static final Instant T = Instant.parse("2026-11-20T09:58:40Z");

    private static Reservation payee() {
        return new Reservation("r_" + ID.toHexString(), SARA, "B2", EtatReservation.PAYEE,
                T, T.plusSeconds(900), T.plusSeconds(145), T.plusSeconds(4910), 2.25, "TND",
                new Paiement("simule", T.plusSeconds(4940), "RECU-2026-000042"));
    }

    // ---------- Réservations ----------

    @Test
    void reservationAllerRetourSansPerte() {
        Reservation r = payee();
        assertEquals(r, ConversionActivite.versReservation(ConversionActivite.versDocument(r, ID)));
    }

    @Test
    void reservationChampsNommesCommeLeContrat() {
        Document d = ConversionActivite.versDocument(payee(), ID);
        assertEquals(ID, d.get("_id"));
        assertEquals(new ObjectId("6543a1f0aabbccddeeff0011"), d.get("utilisateur")); // ObjectId, comme utilisateurs._id
        assertEquals("payee", d.get("etat"));
        assertInstanceOf(Date.class, d.get("creee_le"));
        assertInstanceOf(Date.class, d.get("expire_le"));
        Document p = d.get("paiement", Document.class);
        assertEquals("simule", p.get("moyen"));
        assertEquals("RECU-2026-000042", p.get("recu"));
        assertInstanceOf(Date.class, p.get("paye_le"));
    }

    @Test
    void reservationActiveSansPaiement() {
        Reservation r = Reservation.nouvelle(SARA, "B3", T, 15).avecId("r_" + ID.toHexString());
        Document d = ConversionActivite.versDocument(r, ID);
        assertEquals("active", d.get("etat"));
        assertEquals(Date.from(T.plusSeconds(900)), d.get("expire_le"));
        assertTrue(d.containsKey("paiement"));   // présent et null, comme dans la réponse de l'API
        assertNull(d.get("paiement"));
        assertEquals(r, ConversionActivite.versReservation(d));
    }

    @Test
    void reservationEnCoursEcritAvecUnTiretBas() {
        Document d = ConversionActivite.versDocument(payee().avecEtat(EtatReservation.EN_COURS), ID);
        assertEquals("en_cours", d.get("etat"));
    }

    @Test
    void montantEntierEcritParMongoshAccepte() {
        Document d = ConversionActivite.versDocument(payee(), ID).append("montant", 3);
        assertEquals(3.0, ConversionActivite.versReservation(d).montant());
    }

    @Test
    void utilisateurMalFormeRefuseAvantLaBase() {
        Reservation r = new Reservation(null, "sara", "B2", EtatReservation.ACTIVE, T, T.plusSeconds(900),
                null, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> ConversionActivite.versDocument(r, ID));
    }

    // ---------- Autorisations ----------

    @Test
    void autorisationAllerRetourSansPerte() {
        Autorisation a = new Autorisation("aut_" + ID.toHexString(), SARA, TypeAutorisation.PMR,
                T, null, EXPLOITANT);
        Document d = ConversionActivite.versDocument(a, ID);
        assertEquals("pmr", d.get("type"));
        assertTrue(d.containsKey("fin"));
        assertNull(d.get("fin"));
        assertInstanceOf(ObjectId.class, d.get("accordee_par"));
        assertEquals(a, ConversionActivite.versAutorisation(d));
    }

    @Test
    void autorisationSansExploitant() {
        Autorisation a = new Autorisation("aut_" + ID.toHexString(), SARA, TypeAutorisation.ELECTRIQUE,
                T, T.plusSeconds(86400), null);
        Document d = ConversionActivite.versDocument(a, ID);
        assertNull(d.get("accordee_par"));
        assertEquals(a, ConversionActivite.versAutorisation(d));
    }

    // ---------- Événements ----------

    @Test
    void evenementAllerRetourAvecDetails() {
        Evenement e = new Evenement("evt_" + ID.toHexString(), "P1", TypeEvenement.VENTOUSE, List.of("A2"), null,
                T, null, SourceEvenement.SERVEUR, Map.of("duree_min", 121, "duree_max_min", 120),
                true, EXPLOITANT, T.plusSeconds(60), "Conducteur prévenu");
        Document d = ConversionActivite.versDocument(e, ID);
        assertEquals("ventouse", d.get("type"));
        assertEquals("serveur", d.get("source"));
        assertEquals(121, d.get("details", Document.class).get("duree_min"));
        assertEquals(e, ConversionActivite.versEvenement(d));
    }

    @Test
    void evenementZoneInterditeSansPlaceNiDetails() {
        Evenement e = Evenement.nouveau("P1", TypeEvenement.ZONE_INTERDITE, null, "allee1", T, SourceEvenement.PI, null)
                .avecId("evt_" + ID.toHexString());
        Document d = ConversionActivite.versDocument(e, ID);
        assertEquals("zone_interdite", d.get("type"));
        assertEquals(List.of(), d.get("places"));
        assertNull(d.get("details"));
        assertEquals(false, d.get("traite"));
        assertEquals(e, ConversionActivite.versEvenement(d));
    }

    @Test
    void evenementEcritParMongoshSansChampsFacultatifs() {
        // Document minimal, comme si un autre outil l'avait écrit : les champs absents sont lus comme vides
        Document d = new Document("_id", ID).append("parking", "P1").append("type", "cheval")
                .append("places", List.of("A3", "A4")).append("debut", Date.from(T)).append("source", "pi");
        Evenement e = ConversionActivite.versEvenement(d);
        assertEquals(List.of("A3", "A4"), e.places());
        assertEquals(Map.of(), e.details());
        assertEquals(false, e.traite());
        assertNull(e.traitePar());
    }
}
