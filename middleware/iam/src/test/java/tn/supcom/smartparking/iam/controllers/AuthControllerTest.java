package tn.supcom.smartparking.iam.controllers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.json.JsonObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tn.supcom.smartparking.iam.core.token.IssuedToken;
import tn.supcom.smartparking.iam.exceptions.IamException;
import tn.supcom.smartparking.iam.managers.AuthenticationManager;
import tn.supcom.smartparking.iam.managers.AuthenticationManager.LoginResult;
import tn.supcom.smartparking.iam.model.Role;
import tn.supcom.smartparking.iam.model.User;

class AuthControllerTest {

    final AuthenticationManager manager = mock(AuthenticationManager.class);
    final AuthController controller = new AuthController(manager);
    static final User SARA = new User("u_65f0aa", "Sara", "sara@exemple.tn", "$argon2id$secret", Role.DRIVER, true, Instant.EPOCH);

    private static void erreur400(Runnable r) {
        IamException e = assertThrows(IamException.class, r::run);
        assertEquals(400, e.status());
        assertEquals("invalid_request", e.code());
    }

    @Test
    void registerLitLeJsonEnAnglaisEtEffaceLeMotDePasse() {
        List<char[]> recus = new ArrayList<>();
        when(manager.register(eq("Sara"), eq("sara@exemple.tn"), any())).thenAnswer(i -> {
            char[] p = i.getArgument(2);
            assertEquals("Motdepasse1", new String(p)); // reçu en clair pendant l'appel…
            recus.add(p);
            return SARA;
        });
        JsonObject r = controller.register("{\"name\":\"Sara\",\"email\":\"sara@exemple.tn\",\"password\":\"Motdepasse1\",\"role\":\"OPERATOR\"}");

        assertArrayEquals(new char[11], recus.get(0)); // …puis effacé
        assertEquals("u_65f0aa", r.getString("id"));
        assertEquals("Sara", r.getString("name"));
        assertEquals("DRIVER", r.getString("role"));
        assertEquals(true, r.getBoolean("active"));
        assertFalse(r.toString().contains("argon2"), "jamais l'empreinte dans une réponse");
    }

    @Test
    void loginRenvoieAccessTokenTypeExpiresInEtUser() {
        when(manager.login(eq("sara@exemple.tn"), any())).thenReturn(new LoginResult(SARA, new IssuedToken("eyJ.a.b", 3600)));
        JsonObject r = controller.login("{\"email\":\"sara@exemple.tn\",\"password\":\"Motdepasse1\"}");
        assertEquals("eyJ.a.b", r.getString("access_token"));
        assertEquals("Bearer", r.getString("type"));
        assertEquals(3600, r.getJsonNumber("expires_in").longValue());
        assertEquals("sara@exemple.tn", r.getJsonObject("user").getString("email"));
        assertFalse(r.toString().contains("argon2"));
    }

    @Test
    void meLitLEnTeteBearer() {
        when(manager.currentUser("eyJ.a.b")).thenReturn(SARA);
        JsonObject r = controller.me("Bearer eyJ.a.b");
        assertEquals("u_65f0aa", r.getString("id"));
        assertEquals(0, r.getJsonArray("permissions").size());
        controller.me("bearer   eyJ.a.b ");
        verify(manager, org.mockito.Mockito.times(2)).currentUser("eyJ.a.b");
    }

    @Test
    void meSansEnTeteOuAutreTypeRefuse401() {
        for (String entete : new String[] {null, "", "Bearer", "Bearer ", "Basic dXNlcjpwYXNz", "eyJ.a.b"}) {
            IamException e = assertThrows(IamException.class, () -> controller.me(entete), String.valueOf(entete));
            assertEquals(401, e.status());
        }
    }

    @Test
    void corpsInvalides400() {
        erreur400(() -> controller.login(null));
        erreur400(() -> controller.login(" "));
        erreur400(() -> controller.login("{pas du json"));
        erreur400(() -> controller.login("[1,2]"));
        erreur400(() -> controller.login("{\"email\":\"a@b.tn\",\"password\":12345678}"));
        erreur400(() -> controller.register("{\"name\":[\"Sara\"],\"email\":\"a@b.tn\",\"password\":\"Motdepasse1\"}"));
    }

    @Test
    void champsAbsentsTransmisCommeNullAuManager() {
        when(manager.login(any(), any())).thenThrow(IamException.invalidRequest("L'email et le mot de passe sont obligatoires."));
        erreur400(() -> controller.login("{}"));
        verify(manager).login(null, null);
        when(manager.register(anyString(), any(), any())).thenReturn(SARA);
    }
}
