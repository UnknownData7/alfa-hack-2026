package ru.hackathon.credit.assistant;

import java.util.List;

import ru.hackathon.credit.product.ProductMatcher;
import ru.hackathon.credit.product.Purpose;

/**
 * Инструменты помощника: обёртки над существующими сервисами без новых бизнес-правил. Работают от имени текущего
 * клиента и не имеют доступа к данным внутренней оценки банка.
 */
public interface AssistantTools {

    /** Подбор продуктов, как {@code POST /api/products/recommend}. */
    List<ProductMatcher.Recommendation> recommend(Purpose purpose, double amount, int termMonths);

    List<ProductFacts.ProductInfo> products();
}
