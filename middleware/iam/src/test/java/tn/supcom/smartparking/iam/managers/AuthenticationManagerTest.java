package tn.supcom.smartparking.iam.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tn.supcom.smartparking.iam.core.security.PasswordHasher;
import tn.supcom.smartparking.iam.core.token.IssuedToken;
import tn.supcom.smartparking.iam.core.token.TokenClaims;
import tn.supcom.smartparking.iam.core.token.TokenIssuer;
import tn.supcom.smartparking.iam.core.token.TokenValidator;
import tn.supcom.smartparking.iam.core.validation.DefaultRegistrationValidator;
import tn.supcom.smartparking.iam.exceptions.IamException;
import tn.supcom.smartparking.iam.managers.AuthenticationManager.LoginResult;
import tn.supcom.smartparking.iam.model.Role;
import tn.supcom.smartparking.iam.model.User;
import tn.supcom.smartparking.iam.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthenticationManagerTest {

    @Mock
    UserRepository users;
    @Mock
    PasswordHasher hasher;
    @Mock
    TokenIssuer issuer;
    @Mock
    TokenValidator validator;

    AuthenticationManager manager;

    static final User SARA = new User("u_65f0aa", "Sara", "sara@exemple.tn", "$argon2id$hash", Role.DRIVER, true, Instant.EPOCH);
    static final IssuedToken JETON = new IssuedToken("jwt", 3600);

    @BeforeEach
    void preparer() {
        manager = new AuthenticationManager(users, hasher, issuer, validator, new DefaultRegistrationValidator());
        when(issuer.issue(any())).thenReturn(JETON);
    }

    private static IamException erreur(int statut, String code, Runnable r) {
        IamException e = assertThrows(IamException.class, r::run);
        assertEquals(statut, e.status());
        assertEquals(code, e.code());
        return e;
    }

    // ---------- Inscription ----------

    @Test
    void inscriptionToujoursDriverAvecEmailNormaliseEtEmpreinte() {
        char[] mdp = "Motdepasse1".toCharArray();
        when(hasher.hash(mdp)).thenReturn("$argon2id$nouvelle");
        when(users.create("Sara", "sara@exemple.tn", "$argon2id$nouvelle", Role.DRIVER)).thenReturn(SARA);

        assertSame(SARA, manager.register("  Sara ", "  Sara@Exemple.TN ", mdp));
        verify(users).create("Sara", "sara@exemple.tn", "$argon2id$nouvelle", Role.DRIVER);
    }

    @Test
    void inscriptionInvalideRefuseeSansHacherNiEcrire() {
        erreur(400, "invalid_request", () -> manager.register("", "sara@exemple.tn", "Motdepasse1".toCharArray()));
        erreur(400, "invalid_request", () -> manager.register("Sara", "pas-un-email", "Motdepasse1".toCharArray()));
        erreur(400, "invalid_request", () -> manager.register("Sara", "sara@exemple.tn", "court".toCharArray()));
        verifyNoInteractions(hasher, users);
    }

    @Test
    void emailDejaPrisRemonteEn409() {
        when(hasher.hash(any())).thenReturn("h");
        when(users.create(anyString(), anyString(), anyString(), any())).thenThrow(IamException.emailAlreadyExists());
        erreur(409, "email_already_exists", () -> manager.register("Sara", "sara@exemple.tn", "Motdepasse1".toCharArray()));
    }

    // ---------- Connexion ----------

    @Test
    void connexionReussieEmetUnJeton() {
        char[] mdp = "Motdepasse1".toCharArray();
        when(users.findByEmail("sara@exemple.tn")).thenReturn(Optional.of(SARA));
        when(hasher.verify(mdp, SARA.passwordHash())).thenReturn(true);

        LoginResult r = manager.login(" SARA@exemple.tn", mdp);
        assertSame(SARA, r.user());
        assertSame(JETON, r.token());
        verify(users, never()).updatePasswordHash(anyString(), anyString());
    }

    @Test
    void emailInconnuFaitLaVerificationFacticeEtRepondLaMemeErreur() {
        char[] mdp = "Motdepasse1".toCharArray();
        when(users.findByEmail("inconnu@exemple.tn")).thenReturn(Optional.empty());
        IamException e = erreur(401, "invalid_credentials", () -> manager.login("inconnu@exemple.tn", mdp));
        verify(hasher).verifyDummy(mdp);
        assertEquals("Email ou mot de passe incorrect.", e.getMessage());
    }

    @Test
    void mauvaisMotDePasseMemeErreurQuEmailInconnu() {
        when(users.findByEmail("sara@exemple.tn")).thenReturn(Optional.of(SARA));
        when(hasher.verify(any(), eq(SARA.passwordHash()))).thenReturn(false);
        IamException e = erreur(401, "invalid_credentials", () -> manager.login("sara@exemple.tn", "Mauvais123".toCharArray()));
        assertEquals("Email ou mot de passe incorrect.", e.getMessage());
        verifyNoInteractions(issuer);
    }

    @Test
    void compteDesactiveSeulementApresUnMotDePasseCorrect() {
        User desactive = new User("u_1", "X", "x@exemple.tn", "h", Role.DRIVER, false, Instant.EPOCH);
        when(users.findByEmail("x@exemple.tn")).thenReturn(Optional.of(desactive));
        when(hasher.verify(any(), eq("h"))).thenReturn(false);
        erreur(401, "invalid_credentials", () -> manager.login("x@exemple.tn", "Mauvais123".toCharArray()));
        when(hasher.verify(any(), eq("h"))).thenReturn(true);
        erreur(403, "account_disabled", () -> manager.login("x@exemple.tn", "Bon12345".toCharArray()));
        verifyNoInteractions(issuer);
    }

    @Test
    void anciensParametresArgon2EmpreinteRefaiteALaConnexion() {
        char[] mdp = "Motdepasse1".toCharArray();
        when(users.findByEmail("sara@exemple.tn")).thenReturn(Optional.of(SARA));
        when(hasher.verify(mdp, SARA.passwordHash())).thenReturn(true);
        when(hasher.needsRehash(SARA.passwordHash())).thenReturn(true);
        when(hasher.hash(mdp)).thenReturn("$argon2id$plus-fort");

        LoginResult r = manager.login("sara@exemple.tn", mdp);
        verify(users).updatePasswordHash("u_65f0aa", "$argon2id$plus-fort");
        assertEquals("$argon2id$plus-fort", r.user().passwordHash());
    }

    @Test
    void reHachageRateNEmpechePasLaConnexion() {
        when(users.findByEmail("sara@exemple.tn")).thenReturn(Optional.of(SARA));
        when(hasher.verify(any(), anyString())).thenReturn(true);
        when(hasher.needsRehash(anyString())).thenReturn(true);
        when(hasher.hash(any())).thenReturn("h2");
        doThrow(new IllegalStateException("MongoDB injoignable")).when(users).updatePasswordHash(anyString(), anyString());
        assertSame(JETON, manager.login("sara@exemple.tn", "Motdepasse1".toCharArray()).token());
    }

    @Test
    void champsManquantsOuMotDePasseTropLong() {
        erreur(400, "invalid_request", () -> manager.login(null, "x".toCharArray()));
        erreur(400, "invalid_request", () -> manager.login("sara@exemple.tn", new char[0]));
        erreur(400, "invalid_request", () -> manager.login("sara@exemple.tn", null));
        erreur(401, "invalid_credentials", () -> manager.login("sara@exemple.tn", new char[129]));
        verifyNoInteractions(users, hasher);
    }

    // ---------- Utilisateur courant ----------

    @Test
    void utilisateurCourantDepuisLeJeton() {
        when(validator.validate("jwt")).thenReturn(new TokenClaims("k", "u_65f0aa", "sara@exemple.tn", Role.DRIVER, "j", Instant.EPOCH, Instant.EPOCH));
        when(users.findById("u_65f0aa")).thenReturn(Optional.of(SARA));
        assertSame(SARA, manager.currentUser("jwt"));
    }

    @Test
    void jetonInvalideCompteSupprimeOuDesactive() {
        when(validator.validate("faux")).thenThrow(IamException.unauthenticated());
        erreur(401, "unauthenticated", () -> manager.currentUser("faux"));

        when(validator.validate("jwt")).thenReturn(new TokenClaims("k", "u_1", "x", Role.DRIVER, "j", Instant.EPOCH, Instant.EPOCH));
        when(users.findById("u_1")).thenReturn(Optional.empty());
        erreur(401, "unauthenticated", () -> manager.currentUser("jwt"));

        when(users.findById("u_1")).thenReturn(Optional.of(new User("u_1", "X", "x", "h", Role.DRIVER, false, Instant.EPOCH)));
        erreur(403, "account_disabled", () -> manager.currentUser("jwt"));
    }
}
