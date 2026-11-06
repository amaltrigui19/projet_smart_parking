# Installation du Raspberry Pi 4 et de la caméra

Procédure suivie au sprint 1 pour préparer le Pi qui servira de « yeux » du parking.
Chaque étape indique comment vérifier qu'elle a fonctionné.

## Matériel

- Raspberry Pi 4 (4 Go de mémoire minimum)
- Carte microSD de 32 Go ou plus
- Alimentation USB-C 5 V / 3 A
- Caméra : module caméra (connecteur CSI) ou webcam USB
- Dissipateur ou ventilateur (l'inférence YOLO fait chauffer le Pi)
- Un ordinateur sur le même réseau pour se connecter en SSH

## 1. Installer le système

1. Sur l'ordinateur, installer **Raspberry Pi Imager**.
2. Choisir le modèle « Raspberry Pi 4 », puis **Raspberry Pi OS (64-bit)**, puis la carte microSD.
3. Ouvrir les réglages avancés (« Modifier les réglages ») et renseigner :
   - nom d'hôte : `parking-pi`
   - nom d'utilisateur et mot de passe (à noter, ne pas les mettre dans Git)
   - réseau Wi-Fi (nom et mot de passe) et pays du Wi-Fi
   - **activer SSH** (authentification par mot de passe)
   - fuseau horaire : `Africa/Tunis`
4. Écrire la carte, la mettre dans le Pi et brancher l'alimentation.

Vérification : après une à deux minutes, `ping parking-pi.local` répond depuis l'ordinateur.

## 2. Se connecter en SSH

```bash
ssh <utilisateur>@parking-pi.local
```

Si le nom `parking-pi.local` n'est pas trouvé, chercher l'adresse IP du Pi dans l'interface du routeur et utiliser `ssh <utilisateur>@<adresse-ip>`.

Mettre le système à jour :

```bash
sudo apt update && sudo apt full-upgrade -y
sudo reboot
```

Vérification : `cat /etc/os-release` affiche la version de Raspberry Pi OS.

## 3. Brancher et tester la caméra

**Module caméra (CSI)** : éteindre le Pi (`sudo shutdown now`, puis débrancher l'alimentation), insérer le câble ruban dans le connecteur marqué `CAMERA` (pas `DISPLAY`) en suivant le sens indiqué dans la notice de la caméra, puis rallumer.

**Webcam USB** : la brancher sur un port USB du Pi.

Test (module CSI). Sur Raspberry Pi OS « Bookworm » les commandes commencent par `rpicam-`, sur les versions plus anciennes par `libcamera-` :

```bash
rpicam-hello --list-cameras          # la caméra doit apparaître dans la liste
rpicam-still -n -o test.jpg          # prend une photo sans aperçu
```

Vérification : `test.jpg` existe et s'ouvre (la copier sur l'ordinateur avec `scp` pour la regarder).

## 4. Installer ce qu'il faut pour le script

```bash
sudo apt install -y python3-picamera2 python3-opencv git
```

`picamera2` s'installe avec `apt` et non avec `pip` : si on utilise un environnement virtuel, il faut le créer avec l'option `--system-site-packages`.

## 5. Récupérer le dépôt et prendre une photo

```bash
git clone https://github.com/amaltrigui19/projet_smart_parking.git
cd projet_smart_parking
git checkout -b iot/amal-s1-merjeu
python3 iot/capture.py
```

Le script affiche le chemin de la photo et sa netteté, par exemple :

```
Caméra utilisée : picamera2 (1280x720)
Photo enregistrée : .../iot/photos_test/photo_20261105_141502.jpg  (netteté 312, nette)
```

Les photos sont enregistrées dans `iot/photos_test/`, un dossier ignoré par Git.

## En cas de problème

| Symptôme | Piste |
|---|---|
| `rpicam-hello --list-cameras` ne liste rien | Éteindre le Pi, vérifier que le câble ruban est bien enfoncé et dans le bon sens, puis rallumer. |
| `Aucune caméra trouvée à l'index 0` (webcam) | Essayer `--camera 1`, ou vérifier que la webcam est bien branchée (`lsusb`). |
| `ModuleNotFoundError: picamera2` | Installer `python3-picamera2` avec `apt` ; dans un environnement virtuel, l'avoir créé avec `--system-site-packages`. |
| Photo floue | Régler la mise au point à la main (selon le module), améliorer l'éclairage, retirer le film de protection de l'objectif. |
| Photo sombre | Attendre plus longtemps avant la prise (`--delai 4`) ou ajouter de la lumière. |
