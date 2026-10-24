package ru.hackathon.credit.assistant;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import ru.hackathon.credit.assistant.kb.KnowledgeBase;
import ru.hackathon.credit.assistant.kb.KnowledgeChunk;
import ru.hackathon.credit.assistant.kb.KnowledgeParser;

/**
 * База знаний для юнит-тестов: те же файлы, что в продукте, поиск по началу слова вместо морфологии PostgreSQL.
 * Поиск в настоящей базе проверяет {@code KnowledgeBaseIntegrationTest}.
 */
class InMemoryKnowledgeBase implements KnowledgeBase {

    private static final Set<String> STOP = Set.of("что", "такое", "как", "это", "для", "если", "или", "мне", "меня",
            "нужно", "нужен", "нужна", "можно", "чем", "при", "под", "без", "есть", "уже", "только", "где", "когда",
            "почему", "зачем", "сколько", "какой", "какая", "какие", "хочу", "надо", "нам", "пока", "еще", "так", "все",
            "был", "быть", "моей", "мой", "моя", "наш", "наша");

    private final Map<String, KnowledgeChunk> chunks;

    InMemoryKnowledgeBase(List<KnowledgeChunk> extra) {
        this.chunks = java.util.stream.Stream.concat(KnowledgeParser.loadAll().stream(), extra.stream())
                .collect(Collectors.toMap(KnowledgeChunk::id, Function.identity(), (a, b) -> a, java.util.LinkedHashMap::new));
    }

    @Override
    public Optional<KnowledgeChunk> get(String id) {
        return Optional.ofNullable(chunks.get(id));
    }

    @Override
    public List<KnowledgeChunk> search(String query, Set<String> sources, int limit) {
        List<String> stems = KnowledgeBase.terms(query).stream().filter(w -> !STOP.contains(w))
                .map(w -> w.length() <= 4 ? w : w.substring(0, 5)).toList();
        return chunks.values().stream()
                .filter(c -> sources.contains(c.source()))
                .map(c -> Map.entry(c, score(c, stems)))
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<KnowledgeChunk, Integer>comparingByValue().reversed()
                        .thenComparing(e -> e.getKey().id(), Comparator.naturalOrder()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    private static int score(KnowledgeChunk c, List<String> stems) {
        List<String> strong = KnowledgeBase.terms(c.title() + " " + (c.keywords() == null ? "" : c.keywords()));
        List<String> weak = KnowledgeBase.terms(c.body().toLowerCase(Locale.ROOT));
        int score = 0;
        for (String s : stems) {
            if (strong.stream().anyMatch(w -> w.startsWith(s))) {
                score += 2;
            } else if (weak.stream().anyMatch(w -> w.startsWith(s))) {
                score += 1;
            }
        }
        return score;
    }
}
