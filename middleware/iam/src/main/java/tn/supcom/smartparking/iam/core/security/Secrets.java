package tn.supcom.smartparking.iam.core.security;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Manipulation des secrets en mémoire : conversion sans créer de String, et effacement. */
public final class Secrets {

    private Secrets() {
    }

    /** Efface un mot de passe de la mémoire dès qu'il n'est plus utile. */
    public static void wipe(char[] secret) {
        if (secret != null) {
            Arrays.fill(secret, '\0');
        }
    }

    public static void wipe(byte[] secret) {
        if (secret != null) {
            Arrays.fill(secret, (byte) 0);
        }
    }

    /** Octets UTF-8 d'un mot de passe, sans passer par une String (non effaçable). À effacer après usage. */
    public static byte[] utf8(char[] secret) {
        ByteBuffer buffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(secret));
        byte[] bytes = Arrays.copyOfRange(buffer.array(), buffer.position(), buffer.limit());
        if (buffer.hasArray()) {
            Arrays.fill(buffer.array(), (byte) 0);
        }
        return bytes;
    }
}
