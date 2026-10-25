package ru.hackathon.credit.offer;

import java.util.List;

/** Пояснение к предложению: краткий вывод, факторы решения и что изменилось относительно заявки. */
public record OfferExplanation(String summary, List<String> reasons, List<String> changes) {
}
