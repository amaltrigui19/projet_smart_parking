package tn.supcom.smartparking.iam;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;

/** Inscription et connexion (contrat §2.5, US-01). */
@ApplicationScoped
public class ServiceAuthentification {

    @Inject
    DepotUtilisateurs depot;

    /** Inscription publique : le compte est toujours CONDUCTEUR (un rôle envoyé est ignoré). */
    public Utilisateur inscrire(String nom, String email, String motDePasse) {
        return creerCompte(nom, email, motDePasse, Role.CONDUCTEUR);
    }

    /** Crée un compte du rôle donné, après validation des entrées. */
    public Utilisateur creerCompte(String nom, String email, String motDePasse, Role role) {
        String erreur = ValidationInscription.verifier(nom, email, motDePasse);
        if (erreur != null) {
            throw ErreurIam.requeteInvalide(erreur);
        }
        char[] mdp = motDePasse.toCharArray();
        try {
            String empreinte = HachageMotDePasse.hacher(mdp);
            return depot.creer(nom.trim(), ValidationInscription.normaliserEmail(email), empreinte, role);
        } finally {
            HachageMotDePasse.effacer(mdp);
        }
    }

    /**
     * Vérifie l'email et le mot de passe. Même erreur 401 si l'un des deux est faux ; 403 si le compte
     * est désactivé (seulement après un mot de passe correct, pour ne rien révéler aux autres).
     */
    public Utilisateur connecter(String email, String motDePasse) {
        if (email == null || email.isBlank() || motDePasse == null || motDePasse.isEmpty()) {
            throw ErreurIam.requeteInvalide("L'email et le mot de passe sont obligatoires.");
        }
        if (motDePasse.length() > ValidationInscription.LONGUEUR_MAX_MOT_DE_PASSE) {
            throw ErreurIam.identifiantsInvalides();
        }
        char[] mdp = motDePasse.toCharArray();
        try {
            Optional<Utilisateur> trouve = depot.parEmail(ValidationInscription.normaliserEmail(email));
            if (trouve.isEmpty()) {
                HachageMotDePasse.verifierFactice(mdp); // même durée de réponse qu'un compte existant
                throw ErreurIam.identifiantsInvalides();
            }
            Utilisateur u = trouve.get();
            if (!HachageMotDePasse.verifier(mdp, u.motDePasseHash())) {
                throw ErreurIam.identifiantsInvalides();
            }
            if (!u.actif()) {
                throw ErreurIam.compteDesactive();
            }
            return u;
        } finally {
            HachageMotDePasse.effacer(mdp);
        }
    }

    public Optional<Utilisateur> parId(String id) {
        return depot.parId(id);
    }

    public Optional<Utilisateur> parEmail(String email) {
        return depot.parEmail(ValidationInscription.normaliserEmail(email));
    }
}
