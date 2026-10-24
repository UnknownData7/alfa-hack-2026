package ru.hackathon.credit.assistant;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Настройки помощника. Пороги проактивности вынесены в конфигурацию, чтобы подбирать их по метрикам, а не на глаз.
 */
@ConfigurationProperties(prefix = "app.assistant")
public record AssistantProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("true") boolean proactive,
        /** Бездействие на форме подбора до предложения помощи, секунд. */
        @DefaultValue("20") int idleSeconds,
        /** Смен цели за минуту. */
        @DefaultValue("3") int purposeChanges,
        /** Изменений суммы или срока без отправки формы. */
        @DefaultValue("4") int valueChanges,
        /** Сколько секунд клиент смотрит на рекомендации без выбора. */
        @DefaultValue("30") int recommendationsSeconds,
        /** Шаг мастера без изменений, секунд. */
        @DefaultValue("45") int wizardIdleSeconds,
        /** Одна и та же ошибка проверки подряд. */
        @DefaultValue("2") int validationRepeats,
        @DefaultValue("3") int maxOffersPerSession,
        /** Сообщений помощнику в минуту на пользователя. */
        @DefaultValue("20") int ratePerMinute) {
}
