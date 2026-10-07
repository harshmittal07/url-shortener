package io.github.harshmittal.urlshortener.link.domain;

import io.github.harshmittal.urlshortener.link.domain.UrlPolicyDecision.Accepted;
import io.github.harshmittal.urlshortener.link.domain.UrlPolicyDecision.Rejected;
import io.github.harshmittal.urlshortener.shared.audit.domain.AuditAction;
import io.github.harshmittal.urlshortener.shared.audit.domain.AuditContext;
import io.github.harshmittal.urlshortener.shared.audit.domain.AuditTrail;
import io.github.harshmittal.urlshortener.shared.id.domain.IdGenerator;
import io.github.harshmittal.urlshortener.shared.tx.domain.UnitOfWork;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owner use cases for links: create (T2), read and soft delete (T3). */
public final class LinkService {

    static final String RESOURCE_TYPE = "LINK";
    /** One attempt plus three retries (R7). */
    static final int MAX_ATTEMPTS = 4;
    /** ACCESS_DENIED reason for another key's link; a wrong role is WRONG_ROLE (A13). */
    static final String NOT_OWNER = "NOT_OWNER";

    private static final Logger log = LoggerFactory.getLogger(LinkService.class);

    private final LinkRepository links;
    private final ShortCodeGenerator codes;
    private final UrlPolicy urlPolicy;
    private final AuditTrail audit;
    private final UnitOfWork unitOfWork;
    private final Clock clock;
    private final IdGenerator ids;

    public LinkService(
            LinkRepository links,
            ShortCodeGenerator codes,
            UrlPolicy urlPolicy,
            AuditTrail audit,
            UnitOfWork unitOfWork,
            Clock clock,
            IdGenerator ids) {
        this.links = links;
        this.codes = codes;
        this.urlPolicy = urlPolicy;
        this.audit = audit;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
        this.ids = ids;
    }

    /**
     * Creates a link with a new code, even for a target the owner shortened before (R6). The link
     * and its {@code LINK_CREATED} event commit together (R18).
     *
     * @throws UrlRejectedException if the target fails the URL policy; audited as URL_REJECTED
     * @throws CodeGenerationFailedException if every attempt collided; nothing is stored
     */
    public Link create(String targetUrl, UUID ownerKeyId, AuditContext context) {
        String normalized = switch (urlPolicy.evaluate(targetUrl)) {
            case Accepted accepted -> accepted.normalizedUrl();
            case Rejected rejected -> {
                log.warn("URL rejected: reason={} actorKeyId={}", rejected.reason(), ownerKeyId);
                audit.recordRejection(
                        context,
                        AuditAction.URL_REJECTED,
                        RESOURCE_TYPE,
                        null,
                        rejected.reason().name());
                throw new UrlRejectedException(rejected.reason());
            }
        };
        Link created = unitOfWork.inTransaction(() -> insertWithFreeCode(normalized, ownerKeyId, context));
        log.info("Link created: code={} actorKeyId={}", created.code(), ownerKeyId);
        return created;
    }

    /**
     * Returns the caller's active link (R9).
     *
     * @throws LinkNotFoundException if the code is malformed, unknown, deleted or another key's;
     *     another key's link is audited as ACCESS_DENIED (R11, S-08)
     */
    public Link get(String code, UUID ownerKeyId, AuditContext context) {
        return ownActiveLink(code, ownerKeyId, context);
    }

    /**
     * Soft-deletes the caller's active link (R10). The status change and its {@code LINK_DELETED}
     * event commit together (R18).
     *
     * @throws LinkNotFoundException as for {@link #get}
     */
    public void delete(String code, UUID ownerKeyId, AuditContext context) {
        unitOfWork.inTransaction(() -> {
            Link link = ownActiveLink(code, ownerKeyId, context);
            if (!links.markDeleted(link.code())) {
                // Deleted concurrently since the read: the same answer as an already-deleted link.
                throw new LinkNotFoundException();
            }
            audit.recordChange(
                    context,
                    AuditAction.LINK_DELETED,
                    RESOURCE_TYPE,
                    link.code().value());
            return link;
        });
        log.info("Link deleted: code={} actorKeyId={}", code, ownerKeyId);
    }

    private Link ownActiveLink(String code, UUID ownerKeyId, AuditContext context) {
        Link link = ShortCode.parse(code)
                .flatMap(links::findByCode)
                .filter(Link::isActive)
                .orElseThrow(LinkNotFoundException::new);
        if (!link.ownerKeyId().equals(ownerKeyId)) {
            log.warn("Access denied: reason={} code={} actorKeyId={}", NOT_OWNER, link.code(), ownerKeyId);
            audit.recordRejection(
                    context,
                    AuditAction.ACCESS_DENIED,
                    RESOURCE_TYPE,
                    link.code().value(),
                    NOT_OWNER);
            throw new LinkNotFoundException();
        }
        return link;
    }

    private Link insertWithFreeCode(String targetUrl, UUID ownerKeyId, AuditContext context) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Link link = new Link(
                    ids.newId(),
                    codes.next(),
                    targetUrl,
                    ownerKeyId,
                    LinkStatus.ACTIVE,
                    clock.instant().truncatedTo(ChronoUnit.MICROS));
            if (links.insertIfCodeFree(link)) {
                audit.recordChange(
                        context,
                        AuditAction.LINK_CREATED,
                        RESOURCE_TYPE,
                        link.code().value());
                return link;
            }
        }
        throw new CodeGenerationFailedException(MAX_ATTEMPTS);
    }
}
