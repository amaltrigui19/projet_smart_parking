package tn.supcom.smartparking.iam.keyresolver;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Lecture des clés RSA au format PEM, celui produit par {@code openssl} :
 * clé privée PKCS#8 ({@code -----BEGIN PRIVATE KEY-----}) et clé publique X.509 ({@code -----BEGIN PUBLIC KEY-----}).
 */
public final class PemKeys {

    static final String PRIVATE_BEGIN = "-----BEGIN PRIVATE KEY-----";
    static final String PRIVATE_END = "-----END PRIVATE KEY-----";
    static final String PUBLIC_BEGIN = "-----BEGIN PUBLIC KEY-----";
    static final String PUBLIC_END = "-----END PUBLIC KEY-----";

    private PemKeys() {
    }

    public static boolean isPrivateKey(String pem) {
        return pem != null && pem.contains(PRIVATE_BEGIN);
    }

    public static boolean isPublicKey(String pem) {
        return pem != null && pem.contains(PUBLIC_BEGIN);
    }

    public static RSAPrivateKey readPrivateKey(String pem) {
        if (!isPrivateKey(pem)) {
            throw new IllegalArgumentException("Clé privée attendue au format PEM PKCS#8 (" + PRIVATE_BEGIN + ")");
        }
        try {
            byte[] der = decode(pem, PRIVATE_BEGIN, PRIVATE_END);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IllegalArgumentException | GeneralSecurityException | ClassCastException e) {
            throw new IllegalArgumentException("Clé privée RSA illisible", e);
        }
    }

    public static RSAPublicKey readPublicKey(String pem) {
        if (!isPublicKey(pem)) {
            throw new IllegalArgumentException("Clé publique attendue au format PEM (" + PUBLIC_BEGIN + ")");
        }
        try {
            byte[] der = decode(pem, PUBLIC_BEGIN, PUBLIC_END);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (IllegalArgumentException | GeneralSecurityException | ClassCastException e) {
            throw new IllegalArgumentException("Clé publique RSA illisible", e);
        }
    }

    /** Clé publique déduite de la clé privée (une clé PKCS#8 d'openssl contient le module et l'exposant public). */
    public static RSAPublicKey publicKeyOf(RSAPrivateKey privateKey) {
        if (!(privateKey instanceof RSAPrivateCrtKey crt)) {
            throw new IllegalArgumentException("Clé privée RSA sans exposant public (format CRT attendu)");
        }
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Clé publique RSA impossible à calculer", e);
        }
    }

    private static byte[] decode(String pem, String begin, String end) {
        int debut = pem.indexOf(begin) + begin.length();
        int fin = pem.indexOf(end, debut);
        if (fin < 0) {
            throw new IllegalArgumentException("PEM incomplet : " + end + " manquant");
        }
        return Base64.getMimeDecoder().decode(pem.substring(debut, fin).replaceAll("\\s", ""));
    }
}
