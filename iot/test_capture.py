"""
Tests de capture.py (sans caméra).

Lancer depuis le dossier iot/ :
    python -m unittest test_capture -v
"""

import tempfile
import unittest
from datetime import datetime
from pathlib import Path

import cv2
import numpy as np

import capture


class TestNomsDeFichiers(unittest.TestCase):
    def test_nom_par_defaut_horodate(self):
        nom = capture.nom_par_defaut(datetime(2026, 11, 3, 14, 15, 2))
        self.assertEqual(nom, "photo_20261103_141502.jpg")

    def test_une_photo_garde_le_nom_choisi(self):
        with tempfile.TemporaryDirectory() as dossier:
            chemins = capture.construire_chemins(Path(dossier), "maquette.jpg", 1)
            self.assertEqual([c.name for c in chemins], ["maquette.jpg"])

    def test_plusieurs_photos_sont_numerotees(self):
        with tempfile.TemporaryDirectory() as dossier:
            chemins = capture.construire_chemins(Path(dossier), "maquette.jpg", 3)
            self.assertEqual([c.name for c in chemins],
                             ["maquette_01.jpg", "maquette_02.jpg", "maquette_03.jpg"])

    def test_le_dossier_de_sortie_est_cree(self):
        with tempfile.TemporaryDirectory() as dossier:
            sortie = Path(dossier) / "nouveau" / "photos"
            capture.construire_chemins(sortie, None, 1)
            self.assertTrue(sortie.is_dir())


class TestNettete(unittest.TestCase):
    def _enregistrer(self, image, dossier, nom):
        chemin = Path(dossier) / nom
        cv2.imwrite(str(chemin), image)
        return chemin

    def test_une_image_floue_est_moins_nette_qu_une_image_detaillee(self):
        rng = np.random.default_rng(0)
        detaillee = rng.integers(0, 256, (240, 320, 3), dtype=np.uint8)
        floue = cv2.GaussianBlur(detaillee, (31, 31), 0)
        with tempfile.TemporaryDirectory() as dossier:
            nette = capture.mesurer_nettete(self._enregistrer(detaillee, dossier, "nette.png"))
            pas_nette = capture.mesurer_nettete(self._enregistrer(floue, dossier, "floue.png"))
        self.assertGreater(nette, pas_nette)

    def test_image_unie_a_une_nettete_nulle(self):
        unie = np.full((120, 160, 3), 128, dtype=np.uint8)
        with tempfile.TemporaryDirectory() as dossier:
            valeur = capture.mesurer_nettete(self._enregistrer(unie, dossier, "unie.png"))
        self.assertAlmostEqual(valeur, 0.0)

    def test_fichier_introuvable(self):
        with self.assertRaises(ValueError):
            capture.mesurer_nettete("n_existe_pas.jpg")


class TestArguments(unittest.TestCase):
    def test_valeurs_par_defaut(self):
        arguments = capture.lire_arguments([])
        self.assertEqual((arguments.largeur, arguments.hauteur), (1280, 720))
        self.assertEqual(arguments.nombre, 1)
        self.assertEqual(arguments.source, "auto")

    def test_nombre_invalide_refuse(self):
        with self.assertRaises(SystemExit):
            capture.lire_arguments(["--nombre", "0"])


if __name__ == "__main__":
    unittest.main()
