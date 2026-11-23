package tn.supcom.smartparking.iam.core.security;

/**
 * Paramètres d'Argon2id.
 *
 * @param memoryKib   mémoire en Kio ({@code m}) ; OWASP : 19 456 (19 Mio)
 * @param iterations  nombre de passes ({@code t}) ; OWASP : 2
 * @param parallelism nombre de voies ({@code p}) ; OWASP : 1
 * @param saltLength  taille du sel en octets
 * @param hashLength  taille de l'empreinte en octets
 */
public record Argon2Settings(int memoryKib, int iterations, int parallelism, int saltLength, int hashLength) {

    /** Bornes acceptées, aussi à la lecture d'une empreinte stockée (une valeur énorme bloquerait le serveur). */
    public static final int MAX_MEMORY_KIB = 1 << 20; // 1 Gio
    public static final int MAX_ITERATIONS = 10;
    public static final int MAX_PARALLELISM = 16;

    public Argon2Settings {
        if (parallelism < 1 || parallelism > MAX_PARALLELISM) {
            throw new IllegalArgumentException("Argon2 : parallélisme entre 1 et " + MAX_PARALLELISM);
        }
        if (memoryKib < 8 * parallelism || memoryKib > MAX_MEMORY_KIB) {
            throw new IllegalArgumentException("Argon2 : mémoire entre 8 × p et " + MAX_MEMORY_KIB + " Kio");
        }
        if (iterations < 1 || iterations > MAX_ITERATIONS) {
            throw new IllegalArgumentException("Argon2 : itérations entre 1 et " + MAX_ITERATIONS);
        }
        if (saltLength < 8 || saltLength > 64 || hashLength < 16 || hashLength > 64) {
            throw new IllegalArgumentException("Argon2 : sel de 8 à 64 octets, empreinte de 16 à 64 octets");
        }
    }

    /** Recommandation OWASP (Password Storage Cheat Sheet) : m = 19 Mio, t = 2, p = 1. */
    public static Argon2Settings owasp() {
        return new Argon2Settings(19_456, 2, 1, 16, 32);
    }
}
