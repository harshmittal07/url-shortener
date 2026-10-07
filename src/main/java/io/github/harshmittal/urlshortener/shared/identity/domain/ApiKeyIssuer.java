package io.github.harshmittal.urlshortener.shared.identity.domain;

import io.github.harshmittal.urlshortener.shared.audit.domain.AuditAction;
import io.github.harshmittal.urlshortener.shared.audit.domain.AuditContext;
import io.github.harshmittal.urlshortener.shared.audit.domain.AuditTrail;
import io.github.harshmittal.urlshortener.shared.id.domain.IdGenerator;
import io.github.harshmittal.urlshortener.shared.tx.domain.UnitOfWork;
import java.time.Clock;
import java.time.temporal.ChronoUnit;

/** Issues owner keys (R2). Stores only the prefix and hash; the key is returned once. */
public final class ApiKeyIssuer {

    static final String RESOURCE_TYPE = "API_KEY";

    private final ApiKeyRepository keys;
    private final KeyMaterialGenerator keyMaterial;
    private final AuditTrail audit;
    private final UnitOfWork unitOfWork;
    private final Clock clock;
    private final IdGenerator ids;

    public ApiKeyIssuer(
            ApiKeyRepository keys,
            KeyMaterialGenerator keyMaterial,
            AuditTrail audit,
            UnitOfWork unitOfWork,
            Clock clock,
            IdGenerator ids) {
        this.keys = keys;
        this.keyMaterial = keyMaterial;
        this.audit = audit;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
        this.ids = ids;
    }

    public IssuedApiKey issue(AuditContext context) {
        return unitOfWork.inTransaction(() -> {
            KeyMaterial material = keyMaterial.next();
            ApiKeyToken token = new ApiKeyToken(material.prefix(), material.secret());
            ApiKey key = new ApiKey(
                    ids.newId(),
                    material.prefix(),
                    token.sha256Hex(),
                    clock.instant().truncatedTo(ChronoUnit.MICROS),
                    null);
            keys.insert(key);
            audit.recordChange(
                    context,
                    AuditAction.API_KEY_CREATED,
                    RESOURCE_TYPE,
                    key.id().toString());
            return new IssuedApiKey(key.id(), token.value(), key.createdAt());
        });
    }
}
