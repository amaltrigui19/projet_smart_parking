package tn.supcom.smartparking.iam.repository;

import java.util.Optional;
import tn.supcom.smartparking.iam.exceptions.IamException;
import tn.supcom.smartparking.iam.model.Role;
import tn.supcom.smartparking.iam.model.User;

/** Accès aux comptes (collection MongoDB {@code users}). */
public interface UserRepository {

    /** L'email doit déjà être normalisé. */
    Optional<User> findByEmail(String email);

    /** Identifiant de l'API (« u_ » + ObjectId) ; vide s'il est mal formé ou inconnu. */
    Optional<User> findById(String id);

    /**
     * Crée le compte (actif).
     *
     * @throws IamException 409 {@code email_already_exists} si l'email est déjà pris
     */
    User create(String name, String email, String passwordHash, Role role);

    /** Remplace l'empreinte du mot de passe (re-hachage avec de nouveaux paramètres). */
    void updatePasswordHash(String id, String passwordHash);
}
