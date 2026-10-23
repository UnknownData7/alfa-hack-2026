package ru.hackathon.credit.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Журнал ключевых бизнес-событий: пишется в БД и в структурированный лог. */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    public void log(Long applicationId, String actorType, Long actorId, String eventType, String details) {
        AuditEvent e = new AuditEvent();
        e.setApplicationId(applicationId);
        e.setActorType(actorType);
        e.setActorId(actorId);
        e.setEventType(eventType);
        e.setDetails(details != null && details.length() > 2000 ? details.substring(0, 2000) : details);
        repository.save(e);
        log.info("audit event={} application={} actor={}:{}", eventType, applicationId, actorType, actorId);
    }
}
