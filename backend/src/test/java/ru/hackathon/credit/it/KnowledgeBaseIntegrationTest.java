package ru.hackathon.credit.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import ru.hackathon.credit.assistant.kb.KnowledgeBase;
import ru.hackathon.credit.assistant.kb.KnowledgeChunk;

/** Полнотекстовый поиск PostgreSQL по базе знаний помощника: русская морфология и разделы. */
class KnowledgeBaseIntegrationTest extends IntegrationTestBase {

    @Autowired
    private KnowledgeBase kb;

    @Test
    void findsTermByQuestionInOtherWordForm() {
        List<KnowledgeChunk> hits = kb.search("Что такое залог?", KnowledgeBase.ANSWER_SOURCES, 3);
        assertFalse(hits.isEmpty());
        assertEquals("glossary.collateral", hits.get(0).id());
    }

    @Test
    void mapsEverydayWordsToPurpose() {
        assertEquals("CASH_GAP", topTag("нужно закрыть аренду, пока не пришли деньги от заказчика"));
        assertEquals("NEW_DIRECTION", topTag("у меня кофейня, хочу открыть вторую точку"));
        assertEquals("TRANSPORT", topTag("хочу купить грузовик для доставки"));
    }

    @Test
    void indexesProductCardsFromCatalogAndRebuildsAfterReset() {
        assertTrue(kb.get("product.WORKING_EXPRESS").orElseThrow().body().contains("ставка от 18% годовых"));
        assertTrue(kb.search("разница между экспрессом и овердрафтом", Set.of("compare"), 1).get(0).id()
                .equals("compare.BUSINESS_OVERDRAFT.WORKING_EXPRESS"));
    }

    @Test
    void offTopicQuestionHasNoAnswer() {
        assertTrue(kb.search("какая завтра погода", KnowledgeBase.ANSWER_SOURCES, 3).isEmpty());
    }

    private String topTag(String text) {
        List<KnowledgeChunk> hits = kb.search(text, Set.of("purposes"), 1);
        return hits.isEmpty() ? null : hits.get(0).tag();
    }
}
