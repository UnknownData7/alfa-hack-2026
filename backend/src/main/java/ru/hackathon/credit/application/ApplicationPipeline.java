package ru.hackathon.credit.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Асинхронная обработка заявки после отправки. Между шагами настраиваемая пауза, чтобы смена статусов
 * была видна в интерфейсе. После перезапуска приложения незавершённые заявки дообрабатываются.
 */
@Component
public class ApplicationPipeline {

    private static final Logger log = LoggerFactory.getLogger(ApplicationPipeline.class);
    private static final int MAX_STEPS = 5;

    private final PipelineSteps steps;
    private final long stepDelayMs;
    private final io.micrometer.core.instrument.MeterRegistry metrics;

    public ApplicationPipeline(PipelineSteps steps, @Value("${app.pipeline.step-delay-ms}") long stepDelayMs,
                               io.micrometer.core.instrument.MeterRegistry metrics) {
        this.steps = steps;
        this.stepDelayMs = stepDelayMs;
        this.metrics = metrics;
    }

    @Async("pipelineExecutor")
    public void process(Long applicationId) {
        org.slf4j.MDC.put("applicationId", String.valueOf(applicationId));
        io.micrometer.core.instrument.Timer.Sample sample = io.micrometer.core.instrument.Timer.start(metrics);
        String result = "unknown";
        try {
            for (int i = 0; i < MAX_STEPS; i++) {
                pause();
                ApplicationStatus status = steps.advance(applicationId);
                result = status.name();
                if (status != ApplicationStatus.SUBMITTED && status != ApplicationStatus.SCORING) {
                    break;
                }
            }
            log.info("Обработка заявки {} завершена со статусом {}", applicationId, result);
        } catch (RuntimeException e) {
            result = "error";
            log.error("Ошибка обработки заявки {}, обработка продолжится после перезапуска", applicationId, e);
        } finally {
            sample.stop(metrics.timer("credit.pipeline.duration", "result", result));
            org.slf4j.MDC.remove("applicationId");
        }
    }

    private void pause() {
        if (stepDelayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(stepDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
