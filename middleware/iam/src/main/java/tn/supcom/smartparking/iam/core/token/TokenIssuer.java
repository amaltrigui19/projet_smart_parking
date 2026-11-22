package tn.supcom.smartparking.iam.core.token;

import tn.supcom.smartparking.iam.model.User;

/** Émet un jeton d'accès signé pour un utilisateur. */
public interface TokenIssuer {

    IssuedToken issue(User user);
}
