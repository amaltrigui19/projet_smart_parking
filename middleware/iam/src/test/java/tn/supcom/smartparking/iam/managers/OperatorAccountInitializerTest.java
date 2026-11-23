package tn.supcom.smartparking.iam.managers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tn.supcom.smartparking.iam.exceptions.IamException;
import tn.supcom.smartparking.iam.managers.OperatorAccountInitializer.Outcome;
import tn.supcom.smartparking.iam.model.Role;
import tn.supcom.smartparking.iam.model.User;

class OperatorAccountInitializerTest {

    final AuthenticationManager manager = mock(AuthenticationManager.class);

    @Test
    void creeLeCompteOperatorEtEffaceLeMotDePasse() {
        List<char[]> recus = new ArrayList<>();
        when(manager.findByEmail("ops@exemple.tn")).thenReturn(Optional.empty());
        when(manager.createAccount(eq("Opérateur démo"), eq("ops@exemple.tn"), any(), eq(Role.OPERATOR))).thenAnswer(i -> {
            recus.add(i.getArgument(2));
            return null;
        });
        Outcome r = new OperatorAccountInitializer(manager, "ops@exemple.tn", "Demo-2026!", "Opérateur démo").ensureOperatorAccount();
        assertEquals(Outcome.CREATED, r);
        assertArrayEquals(new char[10], recus.get(0)); // effacé après la création
    }

    @Test
    void compteExistantInchange() {
        when(manager.findByEmail("ops@exemple.tn")).thenReturn(Optional.of(
                new User("u_1", "Ops", "ops@exemple.tn", "h", Role.OPERATOR, true, Instant.EPOCH)));
        assertEquals(Outcome.ALREADY_EXISTS,
                new OperatorAccountInitializer(manager, "ops@exemple.tn", "Demo-2026!", "Ops").ensureOperatorAccount());
        verify(manager, never()).createAccount(anyString(), anyString(), any(), any());
    }

    @Test
    void rienSansConfiguration() {
        assertEquals(Outcome.NOT_CONFIGURED, new OperatorAccountInitializer(manager, null, "x", "Ops").ensureOperatorAccount());
        assertEquals(Outcome.NOT_CONFIGURED, new OperatorAccountInitializer(manager, "ops@exemple.tn", " ", "Ops").ensureOperatorAccount());
        verifyNoInteractions(manager);
    }

    @Test
    void erreursNEmpechentPasLeDemarrage() {
        when(manager.findByEmail(anyString())).thenReturn(Optional.empty());
        when(manager.createAccount(anyString(), anyString(), any(), any())).thenThrow(IamException.invalidRequest("trop court"));
        assertEquals(Outcome.FAILED, new OperatorAccountInitializer(manager, "ops@exemple.tn", "court", "Ops").ensureOperatorAccount());
        when(manager.findByEmail(anyString())).thenThrow(new IllegalStateException("MongoDB injoignable"));
        assertEquals(Outcome.FAILED, new OperatorAccountInitializer(manager, "ops@exemple.tn", "Demo-2026!", "Ops").ensureOperatorAccount());
    }
}
