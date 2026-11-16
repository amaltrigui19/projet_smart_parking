package tn.supcom.smartparking.iam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ValidationInscriptionTest {

    @Test
    void inscriptionValide() {
        assertNull(ValidationInscription.verifier("Sara Ben Ali", "sara@exemple.tn", "motdepasse8"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sara", "sara@", "@exemple.tn", "sara@exemple", "sa ra@exemple.tn", "sara@@exemple.tn"})
    @NullAndEmptySource
    void emailInvalide(String email) {
        assertNotNull(ValidationInscription.verifierEmail(email));
    }

    @Test
    void emailNormalise() {
        assertEquals("sara@exemple.tn", ValidationInscription.normaliserEmail("  Sara@Exemple.TN "));
        assertNull(ValidationInscription.verifierEmail("  Sara@Exemple.TN "));
    }

    @Test
    void motDePasseTropCourt() {
        assertEquals("Le mot de passe doit contenir au moins 8 caractères.",
                ValidationInscription.verifierMotDePasse("1234567"));
        assertNull(ValidationInscription.verifierMotDePasse("12345678"));
        assertNotNull(ValidationInscription.verifierMotDePasse(null));
    }

    @Test
    void motDePasseTropLong() {
        assertNotNull(ValidationInscription.verifierMotDePasse("x".repeat(129)));
        assertNull(ValidationInscription.verifierMotDePasse("x".repeat(128)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"   "})
    @NullAndEmptySource
    void nomObligatoire(String nom) {
        assertNotNull(ValidationInscription.verifierNom(nom));
    }

    @Test
    void nomTropLong() {
        assertNotNull(ValidationInscription.verifierNom("x".repeat(101)));
    }

    @Test
    void premiereErreurRenvoyee() {
        assertEquals("Le nom est obligatoire.", ValidationInscription.verifier("", "pas-un-email", "court"));
        assertEquals("L'email n'est pas valide.", ValidationInscription.verifier("Sara", "pas-un-email", "court"));
    }
}
