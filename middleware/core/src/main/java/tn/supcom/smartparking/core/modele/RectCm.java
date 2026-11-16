package tn.supcom.smartparking.core.modele;

/** Rectangle sur le plan de la maquette, en cm (contrat 4.3) : coin haut-gauche, largeur, hauteur. */
public record RectCm(double x, double y, double largeur, double hauteur) {
    public RectCm {
        if (largeur <= 0 || hauteur <= 0) {
            throw new IllegalArgumentException("rect_cm : largeur et hauteur doivent être > 0");
        }
    }
}