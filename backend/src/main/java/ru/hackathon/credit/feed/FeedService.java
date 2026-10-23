package ru.hackathon.credit.feed;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.hackathon.credit.application.ApplicationRepository;
import ru.hackathon.credit.application.ApplicationStatus;
import ru.hackathon.credit.application.LoanApplication;
import ru.hackathon.credit.common.ApiException;
import ru.hackathon.credit.company.Company;
import ru.hackathon.credit.company.CompanyRepository;

/**
 * Лента «Для вас»: события по заявкам клиента плюс новости и подсказки, подобранные под сегмент, отрасль,
 * продукт и статус текущей заявки. У каждой карточки есть объяснение, почему она показана.
 */
@Service
public class FeedService {

    private final FeedItemRepository items;
    private final NewsItemRepository news;
    private final CompanyRepository companies;
    private final ApplicationRepository applications;

    public FeedService(FeedItemRepository items, NewsItemRepository news, CompanyRepository companies,
                       ApplicationRepository applications) {
        this.items = items;
        this.news = news;
        this.companies = companies;
        this.applications = applications;
    }

    public record FeedCard(String id, String type, String title, String body, String reason,
                           Long relatedApplicationId, Instant createdAt, boolean read) {
    }

    /** Событие по заявке попадает в ленту в момент смены статуса. */
    public void onStatusChanged(LoanApplication app, ApplicationStatus from, ApplicationStatus to) {
        if (to == ApplicationStatus.DRAFT || to == ApplicationStatus.SCORING || from == to) {
            return;
        }
        FeedItem item = new FeedItem();
        item.setCompanyId(app.getCompanyId());
        item.setType("STATUS");
        item.setTitle(statusTitle(to));
        item.setBody("Заявка " + app.getNumber() + ": " + statusBody(to));
        item.setReason("Событие по вашей заявке " + app.getNumber());
        item.setRelatedApplicationId(app.getId());
        items.save(item);
    }

    @Transactional(readOnly = true)
    public List<FeedCard> feed(Long companyId) {
        Company company = companies.findById(companyId).orElseThrow(() -> ApiException.notFound("Компания не найдена"));
        // Подсказки подбираются по самой свежей заявке клиента
        Optional<LoanApplication> current = applications.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .findFirst();

        List<FeedCard> cards = new ArrayList<>();
        for (FeedItem i : items.findTop20ByCompanyIdOrderByCreatedAtDesc(companyId)) {
            cards.add(new FeedCard("event-" + i.getId(), i.getType(), i.getTitle(), i.getBody(), i.getReason(),
                    i.getRelatedApplicationId(), i.getCreatedAt(), i.isRead()));
        }

        List<NewsItem> matched = news.findAll().stream()
                .filter(n -> matches(n, company, current.orElse(null)))
                .sorted(Comparator.comparingInt((NewsItem n) -> relevance(n)).reversed()
                        .thenComparing(NewsItem::getWeight, Comparator.reverseOrder()))
                .limit(5)
                .toList();
        Instant now = Instant.now();
        for (NewsItem n : matched) {
            cards.add(new FeedCard("news-" + n.getId(), n.getType(), n.getTitle(), n.getBody(),
                    reason(n, company, current.orElse(null)), current.map(LoanApplication::getId)
                    .filter(id -> n.getTargetStatus() != null || n.getTargetProductCode() != null).orElse(null),
                    now, false));
        }
        // Подсказки к текущей заявке показываем первыми, затем события и общие новости
        cards.sort(Comparator.comparingInt((FeedCard c) -> order(c)).thenComparing(FeedCard::createdAt,
                Comparator.reverseOrder()));
        return cards;
    }

    @Transactional
    public void markRead(Long companyId, Long itemId) {
        FeedItem item = items.findByIdAndCompanyId(itemId, companyId)
                .orElseThrow(() -> ApiException.notFound("Карточка ленты не найдена"));
        item.setRead(true);
    }

    static boolean matches(NewsItem n, Company c, LoanApplication current) {
        if (n.getTargetLegalForm() != null && !n.getTargetLegalForm().equals(c.getLegalForm())) {
            return false;
        }
        if (n.getTargetIndustryRisk() != null && !n.getTargetIndustryRisk().equals(c.getIndustryRisk())) {
            return false;
        }
        if (n.getTargetProductCode() != null
                && (current == null || !n.getTargetProductCode().equals(current.getProductCode()))) {
            return false;
        }
        if (n.getTargetStatus() != null) {
            return current != null && n.getTargetStatus().equals(current.getStatus().name());
        }
        return true;
    }

    static int relevance(NewsItem n) {
        int r = 0;
        if (n.getTargetStatus() != null) {
            r += 4;
        }
        if (n.getTargetProductCode() != null) {
            r += 3;
        }
        if (n.getTargetIndustryRisk() != null || n.getTargetLegalForm() != null) {
            r += 2;
        }
        return r;
    }

    static String reason(NewsItem n, Company c, LoanApplication current) {
        List<String> parts = new ArrayList<>();
        if (n.getTargetStatus() != null && current != null) {
            parts.add("статус вашей заявки «" + current.getStatus().label() + "»");
        }
        if (n.getTargetProductCode() != null) {
            parts.add("вы оформляете этот продукт");
        }
        if (n.getTargetLegalForm() != null) {
            parts.add("IP".equals(c.getLegalForm()) ? "вы индивидуальный предприниматель" : "ваша компания — ООО");
        }
        if (n.getTargetIndustryRisk() != null) {
            parts.add("особенности вашей отрасли: " + c.getOkvedName().toLowerCase());
        }
        if (parts.isEmpty()) {
            return "Показано, потому что: новость для всех клиентов";
        }
        return "Показано, потому что: " + String.join(", ", parts);
    }

    static int order(FeedCard c) {
        if ("TIP".equals(c.type()) && c.relatedApplicationId() != null) {
            return 0;
        }
        if ("STATUS".equals(c.type()) && !c.read()) {
            return 1;
        }
        if ("STATUS".equals(c.type())) {
            return 3;
        }
        return 2;
    }

    static String statusTitle(ApplicationStatus s) {
        return switch (s) {
            case SUBMITTED -> "Заявка принята";
            case NEED_INFO -> "Банку нужны данные";
            case MANUAL_REVIEW -> "Заявку рассматривает специалист";
            case OFFER_MADE -> "Готово предложение";
            case SIGNING -> "Осталось подписать договор";
            case ISSUED -> "Кредит выдан";
            case DECLINED -> "Решение по заявке";
            case CLOSED -> "Заявка закрыта";
            default -> s.label();
        };
    }

    static String statusBody(ApplicationStatus s) {
        return switch (s) {
            case SUBMITTED -> "проверяем данные и оцениваем заявку.";
            case NEED_INFO -> "ответьте на запрос на странице заявки, чтобы продолжить рассмотрение.";
            case MANUAL_REVIEW -> "заявку проверяет сотрудник банка, обычно это занимает несколько часов.";
            case OFFER_MADE -> "посмотрите условия и примите решение.";
            case SIGNING -> "подтвердите договор кодом на странице заявки.";
            case ISSUED -> "средства зачислены на расчётный счёт (симуляция).";
            case DECLINED -> "банк не может одобрить заявку, причины указаны на странице заявки.";
            case CLOSED -> "вы отказались от предложения.";
            default -> s.label();
        };
    }
}
