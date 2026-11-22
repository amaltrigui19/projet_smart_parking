// Page de démo provisoire (plan-temps-reel.html) : branche le plan SVG sur js/temps-reel.js.
// Le plan sera remplacé par celui d'Amal ; ce fichier montre seulement comment s'en servir.
import { utilisateurConnecte } from './auth.js';
import { afficherStatut, brancherPlan, STATUTS } from './temps-reel.js';

const LIBELLES = { libre: 'libre', occupee: 'occupée', reservee: 'réservée' };
const TAILLE_JOURNAL = 8;

// Sans session valide, js/espace.js renvoie déjà vers la connexion : on ne démarre rien.
if (utilisateurConnecte()) {
  const plan = document.getElementById('plan');
  const journal = document.getElementById('journal');
  const affiches = new Map(); // place -> état affiché (pour ne noter que les vrais changements)
  let etatCharge = false;

  const heure = (iso) => {
    const d = iso ? new Date(iso) : new Date();
    return Number.isNaN(d.getTime()) ? '' : d.toLocaleTimeString('fr-FR');
  };

  function noter(texte) {
    const ligne = document.createElement('li');
    ligne.textContent = texte;
    journal.prepend(ligne);
    while (journal.children.length > TAILLE_JOURNAL) journal.lastElementChild.remove();
  }

  function majResume(places) {
    const n = { libre: 0, occupee: 0, reservee: 0 };
    for (const p of places.values()) n[p.etat] += 1;
    document.getElementById('nb-libres').textContent = n.libre;
    document.getElementById('nb-occupees').textContent = n.occupee;
    document.getElementById('nb-reservees').textContent = n.reservee;
  }

  const pastille = afficherStatut(document.getElementById('statut-temps-reel'));
  const tempsReel = brancherPlan(plan, {
    surStatut(statut, detail) {
      pastille(statut, detail);
      // Plan grisé tant qu'il n'est pas à jour (coupure du WebSocket)
      plan.closest('.plan').classList.toggle('perime', statut !== STATUTS.CONNECTE);
    },
    surChangement(place, etat, info) {
      // Infobulle de la place : « A1 (PMR) : libre »
      const titre = plan.querySelector(`[data-place="${place}"] > title`);
      if (titre) titre.textContent = `${titre.textContent.split(' : ')[0]} : ${LIBELLES[etat]}`;
      const avant = affiches.get(place);
      affiches.set(place, etat);
      if (etatCharge) {
        if (avant !== etat) noter(`${heure(info.depuis)} · ${place} : ${LIBELLES[etat]}`);
        majResume(tempsReel.places);
      }
    },
    surEtatComplet(places) {
      etatCharge = true;
      majResume(places);
      noter(`${heure()} · état complet chargé (${places.size} places)`);
    },
  });
}
