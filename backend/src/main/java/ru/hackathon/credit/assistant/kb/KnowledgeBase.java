package ru.hackathon.credit.assistant.kb;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Поиск по базе знаний помощника. */
public interface KnowledgeBase {

    /** Разделы с ответами клиенту. Цели и отказы ищутся отдельно: первые по своему разделу, вторые выбирает фильтр. */
    Set<String> ANSWER_SOURCES = Set.of("glossary", "process", "faq", "compare", "product");

    Optional<KnowledgeChunk> get(String id);

    /** Лучшие фрагменты по запросу среди указанных разделов, по убыванию релевантности. */
    List<KnowledgeChunk> search(String query, Set<String> sources, int limit);

    /** Слова запроса для поиска: буквы и цифры от трёх символов, без повторов, не больше двенадцати. */
    static List<String> terms(String text) {
        Set<String> words = new LinkedHashSet<>();
        if (text != null) {
            for (String w : text.toLowerCase(Locale.ROOT).replace('ё', 'е').split("[^\\p{L}\\p{N}]+")) {
                if (w.length() >= 3 && words.size() < 12) {
                    words.add(w);
                }
            }
        }
        return new ArrayList<>(words);
    }
}
