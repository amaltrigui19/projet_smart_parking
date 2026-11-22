package tn.supcom.smartparking.iam.managers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import tn.supcom.smartparking.iam.core.security.PasswordHasher;
import tn.supcom.smartparking.iam.core.token.IssuedToken;
import tn.supcom.smartparking.iam.core.token.TokenClaims;
import tn.supcom.smartparking.iam.core.token.TokenIssuer;
import tn.supcom.smartparking.iam.core.token.TokenValidator;
import tn.supcom.smartparking.iam.core.validation.RegistrationValidator;
import tn.supcom.smartparking.iam.exceptions.IamException;
import tn.supcom.smartparking.iam.model.Role;
import tn.supcom.smartparking.iam.model.User;
import tn.supcom.smartparking.iam.repository.UserRepository;

/**
 * Inscription, connexion et utilisateur courant : toute la logique de l'authentification.
 * Ne dépend que d'interfaces (dépôt, hachage, jetons, validation), remplaçables et faciles à simuler en test.
 *
 * <p>Les mots de passe arrivent en {@code char[]} : c'est l'appelant qui les efface après l'appel.
 */
@ApplicationScoped
public class AuthenticationManager {

    private static final Logger LOG = Logger.getLogger(AuthenticationManager.class.getName());

    @Inject
    UserRepository users;

    @Inject
    PasswordHasher hasher;

    @Inject
    TokenIssuer tokenIssuer;

    @Inject
    TokenValidator tokenValidator;

    @Inject
    RegistrationValidator validator;

    /** Résultat d'une connexion réussie. */
    public record LoginResult(User user, IssuedToken token) {
    }

    public AuthenticationManager() {
        // Pour CDI
    }

    public AuthenticationManager(UserRepository users, PasswordHasher hasher, TokenIssuer tokenIssuer,
                                 TokenValidator tokenValidator, RegistrationValidator validator) {
        this.users = users;
        this.hasher = hasher;
        this.tokenIssuer = tokenIssuer;
        this.tokenValidator = tokenValidator;
        this.validator = validator;
    }

    /** Inscription publique : le compte est toujours DRIVER (un rôle envoyé par le client est ignoré). */
    public User register(String name, String email, char[] password) {
        return createAccount(name, email, password, Role.DRIVER);
    }

    /** Crée un compte du rôle donné, après validation des entrées. */
    public User createAccount(String name, String email, char[] password, Role role) {
        validator.validate(name, email, password).ifPresent(message -> {
            throw IamException.invalidRequest(message);
        });
        return users.create(name.trim(), validator.normalizeEmail(email), hasher.hash(password), role);
    }

    /**
     * Vérifie l'email et le mot de passe, puis émet un jeton. Même erreur 401 si l'un des deux est faux ;
     * 403 si le compte est désactivé, seulement après un mot de passe correct (rien n'est révélé aux autres).
     * Si l'empreinte a été calculée avec d'anciens paramètres, elle est refaite (needsRehash).
     */
    public LoginResult login(String email, char[] password) {
        if (email == null || email.isBlank() || password == null || password.length == 0) {
            throw IamException.invalidRequest("L'email et le mot de passe sont obligatoires.");
        }
        if (password.length > RegistrationValidator.MAX_PASSWORD_LENGTH) {
            throw IamException.invalidCredentials();
        }
        Optional<User> found = users.findByEmail(validator.normalizeEmail(email));
        if (found.isEmpty()) {
            hasher.verifyDummy(password); // même durée de réponse qu'un compte existant
            throw IamException.invalidCredentials();
        }
        User user = found.get();
        if (!hasher.verify(password, user.passwordHash())) {
            throw IamException.invalidCredentials();
        }
        if (!user.active()) {
            throw IamException.accountDisabled();
        }
        if (hasher.needsRehash(user.passwordHash())) {
            user = rehash(user, password);
        }
        return new LoginResult(user, tokenIssuer.issue(user));
    }

    /**
     * Utilisateur du jeton {@code Authorization: Bearer}. 401 si le jeton est invalide ou si le compte n'existe plus ;
     * 403 si le compte a été désactivé depuis (ses jetons sont refusés).
     */
    public User currentUser(String accessToken) {
        TokenClaims claims = tokenValidator.validate(accessToken);
        User user = users.findById(claims.subject()).orElseThrow(() ->
                new IamException(401, "unauthenticated", "Ce compte n'existe plus : reconnectez-vous."));
        if (!user.active()) {
            throw IamException.accountDisabled();
        }
        return user;
    }

    public Optional<User> findByEmail(String email) {
        return email == null ? Optional.empty() : users.findByEmail(validator.normalizeEmail(email));
    }

    private User rehash(User user, char[] password) {
        String newHash = hasher.hash(password);
        try {
            users.updatePasswordHash(user.id(), newHash);
            LOG.info(() -> "Empreinte du mot de passe refaite avec les paramètres actuels : " + user.id());
            return user.withPasswordHash(newHash);
        } catch (RuntimeException e) {
            // La connexion réussit quand même ; on réessaiera à la prochaine.
            LOG.log(Level.WARNING, "Re-hachage non enregistré pour " + user.id(), e);
            return user;
        }
    }
}
