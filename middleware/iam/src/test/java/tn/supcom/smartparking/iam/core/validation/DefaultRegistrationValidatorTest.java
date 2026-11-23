package tn.supcom.smartparking.iam.core.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DefaultRegistrationValidatorTest {

    final DefaultRegistrationValidator v = new DefaultRegistrationValidator();
    static final char[] OK = "Motdepasse1".toCharArray();

    private String erreur(String nom, String email, char[] mdp) {
        return v.validate(nom, email, mdp).orElse(null);
    }

    @Test
    void entreesValides() {
        assertTrue(v.validate("Sara", "sara@exemple.tn", OK).isEmpty());
        assertTrue(v.validate("Sara", "  Sara@Exemple.TN ", "12345678".toCharArray()).isEmpty());
        assertTrue(v.validate("S".repeat(100), "sara@exemple.tn", new char[128]).isEmpty());
    }

    @Test
    void nom() {
        assertEquals("Le nom est obligatoire.", erreur(null, "sara@exemple.tn", OK));
        assertEquals("Le nom est obligatoire.", erreur("   ", "sara@exemple.tn", OK));
        assertTrue(erreur("S".repeat(101), "sara@exemple.tn", OK).contains("100"));
    }

    @Test
    void email() {
        assertEquals("L'email est obligatoire.", erreur("Sara", null, OK));
        for (String e : new String[] {"sara", "sara@", "sara@exemple", "sa ra@exemple.tn", "@exemple.tn", "a".repeat(250) + "@x.tn"}) {
            assertEquals("L'email n'est pas valide.", erreur("Sara", e, OK), e);
        }
    }

    @Test
    void motDePasse() {
        assertTrue(erreur("Sara", "sara@exemple.tn", null).contains("au moins 8"));
        assertTrue(erreur("Sara", "sara@exemple.tn", "1234567".toCharArray()).contains("au moins 8"));
        assertTrue(erreur("Sara", "sara@exemple.tn", new char[129]).contains("128"));
    }

    @Test
    void normalisation() {
        assertEquals("sara@exemple.tn", v.normalizeEmail("  Sara@Exemple.TN "));
        assertEquals(null, v.normalizeEmail(null));
    }
}
