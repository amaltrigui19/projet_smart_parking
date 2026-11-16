@echo off
rem Lance Label Studio en local pour corriger les annotations du dataset (sprint 2).
rem Double-cliquer sur ce fichier, ou le lancer depuis cmd / PowerShell. Puis ouvrir http://localhost:8081
rem Arrêter : Ctrl+C dans cette fenêtre.
rem Port 8081 : le port 8080 est souvent déjà pris (ex. Oracle Database XE, processus TNSLSNR).

rem Label Studio est installé dans un environnement Python séparé (voir iot/annotation/README.md)
set "LS_EXE=%USERPROFILE%\venvs\label-studio\Scripts\label-studio.exe"
if not exist "%LS_EXE%" (
  echo Label Studio introuvable : %LS_EXE%
  echo Installer d'abord :  python -m venv "%USERPROFILE%\venvs\label-studio"
  echo                      "%USERPROFILE%\venvs\label-studio\Scripts\python.exe" -m pip install label-studio
  pause
  exit /b 1
)

rem Autoriser Label Studio à afficher les images du dossier iot\dataset (chemin calculé depuis ce fichier)
for %%I in ("%~dp0..\dataset") do set "RACINE=%%~fI"
set LABEL_STUDIO_LOCAL_FILES_SERVING_ENABLED=true
set "LABEL_STUDIO_LOCAL_FILES_DOCUMENT_ROOT=%RACINE%"

rem Base de données de Label Studio (projets, annotations) : en dehors du dépôt
set "LABEL_STUDIO_BASE_DATA_DIR=%USERPROFILE%\label-studio-data"

echo Racine des images : %RACINE%
echo Donnees Label Studio : %LABEL_STUDIO_BASE_DATA_DIR%
"%LS_EXE%" start --port 8081
