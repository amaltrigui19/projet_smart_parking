package tn.supcom.smartparking.iam;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Émetteur de JWT de l'application. La clé privée est lue une fois, au premier jeton :
 * <ul>
 *   <li>dans la variable JWT_CLE_PRIVEE (contenu PEM), si elle existe ;</li>
 *   <li>sinon dans le fichier indiqué par JWT_CLE_PRIVEE_FICHIER (monté depuis server/jwt/).</li>
 * </ul>
 * Elle n'est jamais dans Git ni dans les journaux.
 */
@ApplicationScoped
public class EmetteurJwt {

    private volatile GenerateurJwt generateur;

    public String emettre(Utilisateur u) {
        return generateur().emettre(u);
    }

    public long dureeVieSecondes() {
        return GenerateurJwt.DUREE_VIE.toSeconds();
    }

    private GenerateurJwt generateur() {
        if (generateur == null) {
            synchronized (this) {
                if (generateur == null) {
                    generateur = new GenerateurJwt(GenerateurJwt.lireClePrivee(lirePem()));
                }
            }
        }
        return generateur;
    }

    private static String lirePem() {
        String pem = System.getenv("JWT_CLE_PRIVEE");
        if (pem != null && !pem.isBlank()) {
            return pem;
        }
        String fichier = System.getenv("JWT_CLE_PRIVEE_FICHIER");
        if (fichier == null || fichier.isBlank()) {
            throw new IllegalStateException("Clé privée JWT absente : définir JWT_CLE_PRIVEE ou JWT_CLE_PRIVEE_FICHIER "
                    + "(voir middleware/README.md, server/scripts/generer-cles-jwt.sh)");
        }
        try {
            return Files.readString(Path.of(fichier), StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new IllegalStateException("Clé privée JWT illisible : " + fichier, e);
        }
    }
}
