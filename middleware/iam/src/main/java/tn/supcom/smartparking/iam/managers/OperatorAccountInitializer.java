package tn.supcom.smartparking.iam.managers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Startup;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tn.supcom.smartparking.iam.core.security.Secrets;
import tn.supcom.smartparking.iam.exceptions.IamException;
import tn.supcom.smartparking.iam.model.Role;

/**
 * Au démarrage, crée le compte OPERATOR de démonstration s'il n'existe pas encore dans {@code users}.
 *
 * <p>Seulement si {@code OPERATOR_EMAIL} et {@code OPERATOR_PASSWORD} sont définis (server/.env, jamais dans Git) ;
 * {@code OPERATOR_NAME} est facultatif. Le mot de passe est haché en Argon2id comme les autres et n'apparaît jamais
 * dans les journaux. Un compte qui existe déjà n'est pas modifié. Les anciens comptes (collection {@code utilisateurs},
 * empreintes PBKDF2) ne sont pas repris : la collection {@code users} repart de zéro.
 */
@ApplicationScoped
public class OperatorAccountInitializer {

    private static final Logger LOG = Logger.getLogger(OperatorAccountInitializer.class.getName());

    @Inject
    AuthenticationManager manager;

    @Inject
    @ConfigProperty(name = "OPERATOR_EMAIL")
    Optional<String> email;

    @Inject
    @ConfigProperty(name = "OPERATOR_PASSWORD")
    Optional<String> password;

    @Inject
    @ConfigProperty(name = "OPERATOR_NAME", defaultValue = "Opérateur démo")
    String name;

    /** Ce qui s'est passé (pour les journaux et les tests). */
    public enum Outcome { NOT_CONFIGURED, ALREADY_EXISTS, CREATED, FAILED }

    public OperatorAccountInitializer() {
        // Pour CDI
    }

    public OperatorAccountInitializer(AuthenticationManager manager, String email, String password, String name) {
        this.manager = manager;
        this.email = Optional.ofNullable(email);
        this.password = Optional.ofNullable(password);
        this.name = name;
    }

    void onStartup(@Observes Startup event) {
        ensureOperatorAccount();
    }

    public Outcome ensureOperatorAccount() {
        if (email.filter(e -> !e.isBlank()).isEmpty() || password.filter(p -> !p.isBlank()).isEmpty()) {
            LOG.info("Compte opérateur de démo : OPERATOR_EMAIL ou OPERATOR_PASSWORD non défini, rien à créer.");
            return Outcome.NOT_CONFIGURED;
        }
        String e = email.get();
        char[] secret = password.get().toCharArray();
        try {
            if (manager.findByEmail(e).isPresent()) {
                LOG.info(() -> "Compte opérateur de démo : " + e + " existe déjà, inchangé.");
                return Outcome.ALREADY_EXISTS;
            }
            manager.createAccount(name, e, secret, Role.OPERATOR);
            LOG.info(() -> "Compte opérateur de démo créé : " + e);
            return Outcome.CREATED;
        } catch (IamException ex) {
            LOG.warning(() -> "Compte opérateur de démo non créé : " + ex.getMessage());
            return Outcome.FAILED;
        } catch (RuntimeException ex) {
            // MongoDB injoignable au démarrage, par exemple : l'application démarre quand même.
            LOG.log(Level.WARNING, "Compte opérateur de démo non créé (MongoDB injoignable ?)", ex);
            return Outcome.FAILED;
        } finally {
            Secrets.wipe(secret);
        }
    }
}
