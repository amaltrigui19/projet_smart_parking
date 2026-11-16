package tn.supcom.smartparking.iam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HachageMotDePasseTest {

    @Test
    void lEmpreinteNeContientPasLeMotDePasse() {
        String empreinte = HachageMotDePasse.hacher("motdepasse8".toCharArray());
        assertFalse(empreinte.contains("motdepasse8"));
        assertTrue(empreinte.startsWith("pbkdf2_sha256$" + HachageMotDePasse.ITERATIONS + "$"));
        assertEquals(4, empreinte.split("\\$").length);
    }

    @Test
    void leBonMotDePasseEstAccepte() {
        String empreinte = HachageMotDePasse.hacher("motdepasse8".toCharArray());
        assertTrue(HachageMotDePasse.verifier("motdepasse8".toCharArray(), empreinte));
    }

    @Test
    void unMauvaisMotDePasseEstRefuse() {
        String empreinte = HachageMotDePasse.hacher("motdepasse8".toCharArray());
        assertFalse(HachageMotDePasse.verifier("motdepasse9".toCharArray(), empreinte));
        assertFalse(HachageMotDePasse.verifier("".toCharArray(), empreinte));
    }

    @Test
    void deuxEmpreintesDuMemeMotDePasseSontDifferentes() {
        // Sel aléatoire : deux comptes avec le même mot de passe n'ont pas la même empreinte.
        assertNotEquals(HachageMotDePasse.hacher("motdepasse8".toCharArray()),
                HachageMotDePasse.hacher("motdepasse8".toCharArray()));
    }

    @Test
    void uneEmpreinteAbimeeOuInconnueEstRefusee() {
        String empreinte = HachageMotDePasse.hacher("motdepasse8".toCharArray());
        String[] p = empreinte.split("\\$");
        String abimee = p[0] + "$" + p[1] + "$" + p[2] + "$" + (p[3].charAt(0) == 'A' ? 'B' : 'A') + p[3].substring(1);
        assertFalse(HachageMotDePasse.verifier("motdepasse8".toCharArray(), abimee));
        assertFalse(HachageMotDePasse.verifier("motdepasse8".toCharArray(), null));
        assertFalse(HachageMotDePasse.verifier("motdepasse8".toCharArray(), "md5$abc"));
        assertFalse(HachageMotDePasse.verifier("motdepasse8".toCharArray(), "pbkdf2_sha256$x$y$z"));
    }

    @Test
    void effacerVideLeTableau() {
        char[] mdp = "motdepasse8".toCharArray();
        HachageMotDePasse.effacer(mdp);
        assertEquals("\0".repeat(11), new String(mdp));
    }
}
