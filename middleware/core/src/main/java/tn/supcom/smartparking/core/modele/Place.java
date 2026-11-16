package tn.supcom.smartparking.core.modele;

import java.time.Instant;
import java.util.List;

/**
 * Une place, telle que stockée dans la collection {@code places} (contrat §2.14).
 *
 * @param poly   polygone en pixels de la caméra (pour la détection)
 * @param rectCm rectangle en cm sur le plan (pour le SVG) ; peut être null tant que le plan n'est pas saisi
 * @param noeud  nœud du graphe de guidage ; peut être null au sprint 3
 * @param depuis date du dernier changement d'état ; null si le Pi n'a encore rien envoyé
 */
public record Place(String id, String parking, TypePlace type, int dureeMaxMin,
                    List<Point> poly, RectCm rectCm, String noeud,
                    EtatPlace etat, Instant depuis) {

    public Place {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("place : id manquant");
        if (parking == null || parking.isBlank()) throw new IllegalArgumentException("place " + id + " : parking manquant");
        if (type == null) throw new IllegalArgumentException("place " + id + " : type manquant");
        if (dureeMaxMin <= 0) throw new IllegalArgumentException("place " + id + " : duree_max_min doit être > 0");
        if (poly == null || poly.size() < 3) throw new IllegalArgumentException("place " + id + " : poly doit avoir au moins 3 points");
        poly = List.copyOf(poly);              // copie non modifiable
        if (etat == null) etat = EtatPlace.LIBRE;
    }
}