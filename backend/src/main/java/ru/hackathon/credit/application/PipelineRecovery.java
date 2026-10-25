package ru.hackathon.credit.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** После перезапуска приложения дообрабатывает заявки, оставшиеся в автоматических статусах. */
@Component
public class PipelineRecovery {

    private static final Logger log = LoggerFactory.getLogger(PipelineRecovery.class);

    private final ApplicationRepository applications;
    private final ApplicationPipeline pipeline;

    public PipelineRecovery(ApplicationRepository applications, ApplicationPipeline pipeline) {
        this.applications = applications;
        this.pipeline = pipeline;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumeUnfinished() {
        List<LoanApplication> unfinished = applications.findByStatusIn(
                List.of(ApplicationStatus.SUBMITTED, ApplicationStatus.SCORING));
        for (LoanApplication app : unfinished) {
            log.info("Продолжаем обработку заявки {} после перезапуска", app.getNumber());
            pipeline.process(app.getId());
        }
    }
}
