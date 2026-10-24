package ru.hackathon.credit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import ru.hackathon.credit.application.ApplicationDtos.ClientOfferView;
import ru.hackathon.credit.application.ApplicationDtos.HistoryEntry;
import ru.hackathon.credit.offer.Offer;

/** Клиентские представления не раскрывают внутреннюю оценку банка. */
class ClientViewsTest {

    private static StatusHistory h(ApplicationStatus from, ApplicationStatus to, ActorType actor, String comment) {
        StatusHistory s = new StatusHistory();
        s.setFromStatus(from);
        s.setToStatus(to);
        s.setActorType(actor);
        s.setComment(comment);
        s.setCreatedAt(Instant.now());
        return s;
    }

    @Test
    void historyHidesInternalComments() {
        List<StatusHistory> entries = new ArrayList<>();
        entries.add(h(null, ApplicationStatus.DRAFT, ActorType.CLIENT, "Черновик"));
        entries.add(h(ApplicationStatus.DRAFT, ApplicationStatus.SUBMITTED, ActorType.CLIENT, "Отправлена"));
        entries.add(h(ApplicationStatus.SUBMITTED, ApplicationStatus.SCORING, ActorType.SYSTEM, "Скоринг"));
        entries.add(h(ApplicationStatus.SCORING, ApplicationStatus.MANUAL_REVIEW, ActorType.SYSTEM,
                "Грейд C, 62 балла, нагрузка 40,4%"));
        entries.add(h(ApplicationStatus.MANUAL_REVIEW, ApplicationStatus.MANUAL_REVIEW, ActorType.BANK,
                "Пересчёт: грейд B"));
        entries.add(h(ApplicationStatus.MANUAL_REVIEW, ApplicationStatus.NEED_INFO, ActorType.BANK,
                "Выручка ниже порога, нужна выписка"));
        entries.add(h(ApplicationStatus.NEED_INFO, ApplicationStatus.SUBMITTED, ActorType.CLIENT, "Ответ"));
        entries.add(h(ApplicationStatus.SUBMITTED, ApplicationStatus.DECLINED, ActorType.SYSTEM,
                "Стоп-фактор: задолженность"));

        List<HistoryEntry> result = ApplicationViews.clientHistory(entries);

        assertEquals(7, result.size(), "Записи без смены статуса скрываются");
        String all = result.toString();
        for (String secret : new String[] {"Грейд", "балл", "нагрузка", "Выручка", "Стоп-фактор", "Пересчёт"}) {
            assertFalse(all.contains(secret), "Клиенту ушло: " + secret);
        }
        assertEquals("Заявку рассматривает специалист банка", result.get(3).comment());
        assertEquals("Вы дополнили заявку", result.get(5).comment());
        assertEquals("Банк", result.get(3).actorLabel());
        assertEquals("Вы", result.get(1).actorLabel());
    }

    @Test
    void counterOfferShowsOnlyFactualChanges() {
        LoanApplication a = new LoanApplication();
        a.setRequestedAmount(new BigDecimal("1400000"));
        a.setRequestedTermMonths(6);
        Offer o = new Offer();
        o.setKind(Offer.COUNTER);
        o.setAmount(new BigDecimal("840000"));
        o.setTermMonths(6);

        ClientOfferView view = ApplicationViews.clientOfferView(o, a);

        assertEquals("Банк готов выдать кредит на других условиях", view.summary());
        assertEquals(1, view.changes().size());
        assertTrue(view.changes().get(0).startsWith("Сумма: 840"));
    }

    @Test
    void declinedNextStepHasNoReasons() {
        ApplicationDtos.NextStep step = ApplicationViews.nextStep(ApplicationStatus.DECLINED);
        assertEquals("NEW_APPLICATION", step.action());
        assertFalse(step.text().toLowerCase().contains("причин"));
    }
}
