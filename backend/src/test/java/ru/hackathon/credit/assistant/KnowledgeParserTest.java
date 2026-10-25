package ru.hackathon.credit.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import ru.hackathon.credit.assistant.kb.KnowledgeChunk;
import ru.hackathon.credit.assistant.kb.KnowledgeParser;
import ru.hackathon.credit.product.Purpose;

class KnowledgeParserTest {

    private final List<KnowledgeChunk> chunks = KnowledgeParser.loadAll();

    @Test
    void parsesMetadataAndBody() {
        List<KnowledgeChunk> parsed = KnowledgeParser.parse("glossary", """
                # Заголовок файла
                Вступление не попадает во фрагменты.

                ## Залог
                id: glossary.collateral
                keywords: залог имущество

                Имущество, которое обеспечивает кредит.

                ## Без идентификатора
                Пропускается.
                """);
        assertEquals(1, parsed.size());
        assertEquals("glossary.collateral", parsed.get(0).id());
        assertEquals("Залог", parsed.get(0).title());
        assertEquals("залог имущество", parsed.get(0).keywords());
        assertEquals("Имущество, которое обеспечивает кредит.", parsed.get(0).body());
    }

    @Test
    void idsAreUniqueAndBodiesNotEmpty() {
        Set<String> ids = new HashSet<>();
        for (KnowledgeChunk c : chunks) {
            assertTrue(ids.add(c.id()), "Повтор идентификатора " + c.id());
            assertFalse(c.body().isBlank(), "Пустой фрагмент " + c.id());
            assertTrue(c.id().startsWith(c.source() + "."), "Идентификатор не из своего раздела: " + c.id());
        }
    }

    @Test
    void everyPurposeHasEverydayWording() {
        Set<String> tags = chunks.stream().filter(c -> c.source().equals("purposes")).map(KnowledgeChunk::tag)
                .collect(Collectors.toSet());
        for (Purpose p : Purpose.values()) {
            assertTrue(tags.contains(p.name()), "Нет бытовых формулировок для цели " + p);
        }
    }

    @Test
    void everyPairOfProductsHasComparison() {
        List<String> codes = List.of("BUSINESS_OVERDRAFT", "EQUIPMENT_GROWTH", "WORKING_EXPRESS");
        Set<String> ids = chunks.stream().map(KnowledgeChunk::id).collect(Collectors.toSet());
        for (int i = 0; i < codes.size(); i++) {
            for (int j = i + 1; j < codes.size(); j++) {
                assertTrue(ids.contains("compare." + codes.get(i) + "." + codes.get(j)));
            }
        }
    }
}
