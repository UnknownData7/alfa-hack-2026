package ru.hackathon.credit.common;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import ru.hackathon.credit.decision.CreditDecisionEngine;
import ru.hackathon.credit.integration.CompanyRegistryClient;
import ru.hackathon.credit.integration.CreditBureauClient;
import ru.hackathon.credit.integration.DisbursementProvider;
import ru.hackathon.credit.integration.TaxServiceClient;
import ru.hackathon.credit.integration.mock.MockCompanyRegistryClient;
import ru.hackathon.credit.integration.mock.MockCreditBureauClient;
import ru.hackathon.credit.integration.mock.MockDisbursementProvider;
import ru.hackathon.credit.integration.mock.MockExternalData;
import ru.hackathon.credit.integration.mock.MockTaxServiceClient;
import ru.hackathon.credit.pricing.PricingCalculator;
import ru.hackathon.credit.product.ProductMatcher;
import ru.hackathon.credit.scoring.ScoringEngine;

/**
 * Доменные компоненты без зависимости от Spring и адаптеры внешних систем.
 * Чтобы подключить реальную интеграцию, достаточно заменить бин mock-реализации.
 */
@Configuration
@EnableAsync
public class DomainConfig {

    @Bean
    public PricingCalculator pricingCalculator(@Value("${app.pricing.key-rate-percent}") double keyRate) {
        return new PricingCalculator(keyRate);
    }

    @Bean
    public ScoringEngine scoringEngine() {
        return new ScoringEngine();
    }

    @Bean
    public CreditDecisionEngine creditDecisionEngine(PricingCalculator pricing) {
        return new CreditDecisionEngine(pricing);
    }

    @Bean
    public ProductMatcher productMatcher(@Value("${app.pricing.key-rate-percent}") double keyRate) {
        return new ProductMatcher(keyRate);
    }

    @Bean
    public MockExternalData mockExternalData(@Value("${app.mocks.latency-ms}") long latencyMs,
                                             @Value("${app.mocks.unavailable-sources}") String unavailable) {
        Set<String> sources = Arrays.stream(unavailable.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        return new MockExternalData(latencyMs, sources);
    }

    @Bean
    public CompanyRegistryClient companyRegistryClient(MockExternalData data) {
        return new MockCompanyRegistryClient(data);
    }

    @Bean
    public CreditBureauClient creditBureauClient(MockExternalData data) {
        return new MockCreditBureauClient(data);
    }

    @Bean
    public TaxServiceClient taxServiceClient(MockExternalData data) {
        return new MockTaxServiceClient(data);
    }

    @Bean
    public DisbursementProvider disbursementProvider() {
        return new MockDisbursementProvider();
    }

    /** Пул для асинхронной обработки заявок после отправки. */
    @Bean(name = "pipelineExecutor")
    public Executor pipelineExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("pipeline-");
        // Идентификатор корреляции запроса переходит в поток конвейера, чтобы обработку заявки можно было
        // проследить по логам от отправки до решения
        executor.setTaskDecorator(task -> {
            java.util.Map<String, String> context = org.slf4j.MDC.getCopyOfContextMap();
            return () -> {
                if (context != null) {
                    org.slf4j.MDC.setContextMap(context);
                }
                try {
                    task.run();
                } finally {
                    org.slf4j.MDC.clear();
                }
            };
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
