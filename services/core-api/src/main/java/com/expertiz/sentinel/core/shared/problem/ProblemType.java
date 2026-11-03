package com.expertiz.sentinel.core.shared.problem;

import java.net.URI;

/** Types de problèmes de l'API (spec §11) : le champ {@code type} vaut {@code urn:sentinel:problem:<code>}. */
public enum ProblemType {
    VALIDATION_ERROR("validation-error", 400, "Requête invalide"),
    UNAUTHORIZED("unauthorized", 401, "Authentification requise"),
    FORBIDDEN("forbidden", 403, "Accès refusé"),
    ORIGIN_NOT_ALLOWED("origin-not-allowed", 403, "Origine non autorisée"),
    PASSWORD_CHANGE_REQUIRED("password-change-required", 403, "Changement de mot de passe requis"),
    NOT_FOUND("not-found", 404, "Ressource introuvable"),
    METHOD_NOT_ALLOWED("method-not-allowed", 405, "Méthode non autorisée"),
    NOT_ACCEPTABLE("not-acceptable", 406, "Format de réponse non disponible"),
    UNSUPPORTED_MEDIA_TYPE("unsupported-media-type", 415, "Type de contenu non pris en charge"),
    VERSION_CONFLICT("version-conflict", 409, "Conflit de version"),
    LAST_SUPER_ADMIN("last-super-admin", 409, "Dernier super-administrateur"),
    SELF_MODIFICATION_FORBIDDEN("self-modification-forbidden", 409, "Modification de son propre compte interdite"),
    SERVER_IN_USE("server-in-use", 409, "VPS utilisé"),
    SERVER_NOT_DEPLOYABLE("server-not-deployable", 409, "VPS non déployable"),
    DEPLOYMENT_EXISTS("deployment-exists", 409, "Déploiement existant"),
    DUPLICATE("duplicate", 409, "Valeur déjà utilisée"),
    LOGIN_THROTTLED("login-throttled", 429, "Trop de tentatives"),
    RATE_LIMITED("rate-limited", 429, "Trop de requêtes"),
    INTERNAL_ERROR("internal-error", 500, "Erreur interne");

    private static final String URN_PREFIX = "urn:sentinel:problem:";

    private final String code;
    private final int status;
    private final String title;

    ProblemType(String code, int status, String title) {
        this.code = code;
        this.status = status;
        this.title = title;
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }

    public String title() {
        return title;
    }

    public URI type() {
        return URI.create(URN_PREFIX + code);
    }

    /** Type générique pour une erreur levée par Spring MVC, selon son statut HTTP. */
    static ProblemType forFrameworkStatus(int status) {
        return switch (status) {
            case 400 -> VALIDATION_ERROR;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            default -> INTERNAL_ERROR;
        };
    }
}
