"""
Sprint 1 : prendre une photo avec la caméra du Raspberry Pi.

Deux sources de caméra sont gérées :
    - picamera2 : module caméra branché sur le connecteur CSI du Raspberry Pi
    - opencv    : webcam USB (ou caméra d'un ordinateur de repli)
Par défaut (--source auto), picamera2 est utilisée si une caméra CSI est détectée,
sinon OpenCV.

Utilisation :
    python capture.py                                  # une photo dans photos_test/
    python capture.py --largeur 1920 --hauteur 1080    # autre résolution
    python capture.py --nom maquette_01.jpg            # nom de fichier choisi
    python capture.py --nombre 10 --intervalle 3       # 10 photos, une toutes les 3 s
    python capture.py --source opencv --camera 0       # forcer une webcam USB

Les photos sont enregistrées dans photos_test/ (dossier ignoré par Git). Ce script sert à
régler la caméra et à constituer le dataset de la maquette : dans le fonctionnement normal
du système, les images ne sont jamais enregistrées (privacy by design).

Une mesure de netteté (variance du laplacien) est affichée pour chaque photo : plus elle est
élevée, plus l'image est nette. La valeur dépend de la scène ; elle sert surtout à comparer
deux réglages (mise au point, éclairage) et à repérer une photo floue.
"""

import argparse
import sys
import time
from datetime import datetime
from pathlib import Path

import cv2

DOSSIER_PAR_DEFAUT = Path(__file__).resolve().parent / "photos_test"
LARGEUR_PAR_DEFAUT = 1280
HAUTEUR_PAR_DEFAUT = 720
SEUIL_NETTETE_PAR_DEFAUT = 100.0


def nom_par_defaut(maintenant=None):
    """Nom de fichier horodaté, par exemple photo_20261103_141502.jpg."""
    maintenant = maintenant or datetime.now()
    return maintenant.strftime("photo_%Y%m%d_%H%M%S.jpg")


def mesurer_nettete(chemin):
    """Variance du laplacien de l'image : plus elle est grande, plus l'image est nette."""
    image = cv2.imread(str(chemin))
    if image is None:
        raise ValueError(f"Impossible de relire l'image : {chemin}")
    gris = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    return float(cv2.Laplacian(gris, cv2.CV_64F).var())


def camera_csi_disponible():
    """True si picamera2 est installée et qu'une caméra CSI est branchée."""
    try:
        from picamera2 import Picamera2
    except ImportError:
        return False
    try:
        return len(Picamera2.global_camera_info()) > 0
    except Exception:
        return False


def capturer_picamera2(chemins, largeur, hauteur, delai, intervalle):
    """Prend une photo par chemin donné avec le module caméra du Raspberry Pi."""
    from picamera2 import Picamera2

    camera = Picamera2()
    try:
        configuration = camera.create_still_configuration(main={"size": (largeur, hauteur)})
        camera.configure(configuration)
        camera.start()
        time.sleep(delai)  # laisse l'exposition et la mise au point se stabiliser
        for i, chemin in enumerate(chemins):
            if i > 0:
                time.sleep(intervalle)
            camera.capture_file(str(chemin))
            yield chemin
    finally:
        camera.stop()
        camera.close()


def capturer_opencv(chemins, largeur, hauteur, delai, intervalle, index_camera):
    """Prend une photo par chemin donné avec une webcam (OpenCV)."""
    flux = cv2.VideoCapture(index_camera)
    if not flux.isOpened():
        raise RuntimeError(f"Aucune caméra trouvée à l'index {index_camera} (essayez --camera 1).")
    try:
        flux.set(cv2.CAP_PROP_FRAME_WIDTH, largeur)
        flux.set(cv2.CAP_PROP_FRAME_HEIGHT, hauteur)
        fin_stabilisation = time.time() + delai
        while time.time() < fin_stabilisation:  # on jette les premières images
            flux.read()
        for i, chemin in enumerate(chemins):
            if i > 0:
                time.sleep(intervalle)
            ok, image = flux.read()
            if not ok or image is None:
                raise RuntimeError("La caméra n'a renvoyé aucune image.")
            cv2.imwrite(str(chemin), image)
            yield chemin
    finally:
        flux.release()


def construire_chemins(dossier, nom, nombre):
    """Chemins de sortie : le nom choisi, suffixé _01, _02... si plusieurs photos."""
    dossier.mkdir(parents=True, exist_ok=True)
    nom = nom or nom_par_defaut()
    base = Path(nom)
    if nombre == 1:
        return [dossier / base.name]
    suffixe = base.suffix or ".jpg"
    return [dossier / f"{base.stem}_{i:02d}{suffixe}" for i in range(1, nombre + 1)]


def lire_arguments(argv=None):
    parseur = argparse.ArgumentParser(description="Prend une photo avec la caméra du Raspberry Pi.")
    parseur.add_argument("--largeur", type=int, default=LARGEUR_PAR_DEFAUT, help="largeur en pixels")
    parseur.add_argument("--hauteur", type=int, default=HAUTEUR_PAR_DEFAUT, help="hauteur en pixels")
    parseur.add_argument("--sortie", type=Path, default=DOSSIER_PAR_DEFAUT, help="dossier de sortie")
    parseur.add_argument("--nom", default=None, help="nom du fichier (par défaut : date et heure)")
    parseur.add_argument("--nombre", type=int, default=1, help="nombre de photos à prendre")
    parseur.add_argument("--intervalle", type=float, default=2.0, help="secondes entre deux photos")
    parseur.add_argument("--delai", type=float, default=2.0, help="secondes de stabilisation avant la 1re photo")
    parseur.add_argument("--source", choices=["auto", "picamera2", "opencv"], default="auto")
    parseur.add_argument("--camera", type=int, default=0, help="index de la webcam (source opencv)")
    parseur.add_argument("--seuil-nettete", type=float, default=SEUIL_NETTETE_PAR_DEFAUT,
                         help="en dessous, la photo est signalée comme floue")
    arguments = parseur.parse_args(argv)
    if arguments.nombre < 1:
        parseur.error("--nombre doit être au moins 1")
    if arguments.largeur < 1 or arguments.hauteur < 1:
        parseur.error("--largeur et --hauteur doivent être positifs")
    return arguments


def main(argv=None):
    arguments = lire_arguments(argv)
    chemins = construire_chemins(arguments.sortie, arguments.nom, arguments.nombre)

    source = arguments.source
    if source == "auto":
        source = "picamera2" if camera_csi_disponible() else "opencv"
    print(f"Caméra utilisée : {source} ({arguments.largeur}x{arguments.hauteur})")

    try:
        if source == "picamera2":
            prises = capturer_picamera2(chemins, arguments.largeur, arguments.hauteur,
                                        arguments.delai, arguments.intervalle)
        else:
            prises = capturer_opencv(chemins, arguments.largeur, arguments.hauteur,
                                     arguments.delai, arguments.intervalle, arguments.camera)
        for chemin in prises:
            nettete = mesurer_nettete(chemin)
            etat = "nette" if nettete >= arguments.seuil_nettete else "FLOUE ? vérifier la mise au point"
            print(f"Photo enregistrée : {chemin}  (netteté {nettete:.0f}, {etat})")
    except (RuntimeError, ImportError) as erreur:
        print(f"Erreur : {erreur}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
