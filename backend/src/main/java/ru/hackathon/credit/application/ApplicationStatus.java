package ru.hackathon.credit.application;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Статусы заявки и допустимые переходы (docs/STATE_MACHINE.md). Любой переход вне таблицы запрещён.
 */
public enum ApplicationStatus {
    DRAFT("Черновик"),
    SUBMITTED("Подана"),
    NEED_INFO("Нужны данные"),
    SCORING("Скоринг"),
    MANUAL_REVIEW("Ручная проверка"),
    OFFER_MADE("Предложение условий"),
    SIGNING("Подписание"),
    ISSUED("Выдан"),
    DECLINED("Отказ"),
    CLOSED("Закрыта");

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> TRANSITIONS = Map.of(
            DRAFT, EnumSet.of(SUBMITTED),
            SUBMITTED, EnumSet.of(NEED_INFO, SCORING),
            NEED_INFO, EnumSet.of(SUBMITTED),
            SCORING, EnumSet.of(OFFER_MADE, DECLINED, MANUAL_REVIEW),
            MANUAL_REVIEW, EnumSet.of(MANUAL_REVIEW, NEED_INFO, OFFER_MADE, DECLINED),
            OFFER_MADE, EnumSet.of(SIGNING, CLOSED),
            SIGNING, EnumSet.of(ISSUED),
            ISSUED, EnumSet.noneOf(ApplicationStatus.class),
            DECLINED, EnumSet.noneOf(ApplicationStatus.class),
            CLOSED, EnumSet.noneOf(ApplicationStatus.class));

    private final String label;

    ApplicationStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Название для клиента: без внутренних терминов банка вроде «скоринг». */
    public String clientLabel() {
        return switch (this) {
            case SCORING -> "Проверка";
            case MANUAL_REVIEW -> "На рассмотрении";
            case OFFER_MADE -> "Есть предложение";
            case ISSUED -> "Кредит выдан";
            default -> label;
        };
    }

    public boolean canMoveTo(ApplicationStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    public boolean isFinal() {
        return TRANSITIONS.get(this).isEmpty();
    }
}
