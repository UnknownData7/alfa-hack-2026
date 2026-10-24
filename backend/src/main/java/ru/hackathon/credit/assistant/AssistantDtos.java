package ru.hackathon.credit.assistant;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import ru.hackathon.credit.product.Purpose;

/** Запросы и ответы помощника. Разговор хранит браузер: с каждым шагом он присылает состояние и заполненные поля. */
public final class AssistantDtos {

    private AssistantDtos() {
    }

    /** Что помощник уже узнал о потребности клиента. */
    public record Slots(Purpose purpose,
                        @Positive @Max(1_000_000_000) Long amount,
                        @Min(1) @Max(120) Integer termMonths,
                        @Min(1) @Max(120) Integer termHint,
                        @Size(max = 30) String productCode) {

        public static final Slots EMPTY = new Slots(null, null, null, null, null);

        public Slots withPurpose(Purpose p) {
            return new Slots(p, amount, termMonths, termHint, productCode);
        }

        public Slots withAmount(Long a) {
            return new Slots(purpose, a, termMonths, termHint, productCode);
        }

        public Slots withTerm(Integer t) {
            return new Slots(purpose, amount, t, termHint, productCode);
        }

        public Slots withTermHint(Integer h) {
            return new Slots(purpose, amount, termMonths, h, productCode);
        }

        public Slots withProduct(String code) {
            return new Slots(purpose, amount, termMonths, termHint, code);
        }
    }

    public enum Page { HOME, NEED, WIZARD, APPLICATION, HISTORY, BENEFITS, OTHER }

    /** Где сейчас клиент и что введено в форму на странице. */
    public record PageContext(Page page,
                              @Min(0) @Max(3) Integer wizardStep,
                              @Size(max = 20) String applicationStatus,
                              @Size(max = 20) String offerKind,
                              Purpose purpose,
                              @Positive @Max(1_000_000_000) Long amount,
                              @Min(1) @Max(120) Integer termMonths,
                              @Size(max = 30) String productCode) {
    }

    public record MessageRequest(DialogState state, @Valid Slots slots, @Valid PageContext page,
                                 @NotBlank(message = "Напишите вопрос")
                                 @Size(max = 1000, message = "Сообщение не длиннее 1000 символов") String text) {
    }

    public record ActionRequest(DialogState state, @Valid Slots slots, @Valid PageContext page,
                                @NotBlank @Size(max = 40) String action,
                                Map<@Size(max = 40) String, @Size(max = 100) String> params) {
    }

    /** Кнопка ответа: нажатие отправляет действие на сервер (кроме подсветки, её выполняет браузер). */
    public record Button(String label, String action, Map<String, String> params) {
    }

    /** Действие в интерфейсе: подставить значения в форму, открыть мастер, перейти на страницу, подсветить блок. */
    public record UiAction(String type, Map<String, String> params) {
    }

    /** Вариант срока с ориентировочным платежом по ставке «от». */
    public record TermOption(int termMonths, double monthlyPayment, double overpayment, boolean recommended) {
    }

    public record Reply(DialogState state, Slots slots, String text, List<TermOption> terms, List<Button> buttons,
                        List<UiAction> ui, List<String> sources) {
    }

    public enum EventType { OPENED, OFFER_SHOWN, OFFER_ACCEPTED, OFFER_DISMISSED }

    /** Событие виджета для продуктовых метрик. Текст сообщений не передаётся. */
    public record EventRequest(@NotNull EventType type,
                               @Size(max = 30) @Pattern(regexp = "[A-Z_]*") String signal,
                               Page page,
                               @Size(max = 30) @Pattern(regexp = "[A-Z_]*") String entry) {
    }

    public record ConfigDto(boolean enabled, boolean proactive, int idleSeconds, int purposeChanges, int valueChanges,
                            int recommendationsSeconds, int wizardIdleSeconds, int validationRepeats,
                            int maxOffersPerSession) {
    }
}
