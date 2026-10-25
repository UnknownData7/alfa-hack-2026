package ru.hackathon.credit.assistant.kb;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Разбор файлов базы знаний. Фрагмент начинается с заголовка второго уровня, за ним идут строки метаданных
 * {@code id:}, {@code tag:}, {@code keywords:}, затем текст. Раздел без {@code id} пропускается.
 */
public final class KnowledgeParser {

    public static final String LOCATION = "classpath*:knowledge/*.md";

    private KnowledgeParser() {
    }

    public static List<KnowledgeChunk> loadAll() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(LOCATION);
            List<KnowledgeChunk> chunks = new ArrayList<>();
            for (Resource r : resources) {
                String name = r.getFilename();
                try (InputStream in = r.getInputStream()) {
                    chunks.addAll(parse(name.substring(0, name.length() - 3), new String(in.readAllBytes(), StandardCharsets.UTF_8)));
                }
            }
            return chunks;
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось прочитать базу знаний", e);
        }
    }

    public static List<KnowledgeChunk> parse(String source, String markdown) {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        String title = null;
        String id = null;
        String tag = null;
        String keywords = null;
        StringBuilder body = new StringBuilder();
        boolean meta = false;
        for (String raw : markdown.replace("\r", "").split("\n")) {
            String line = raw.stripTrailing();
            if (line.startsWith("## ")) {
                add(chunks, source, id, title, body, keywords, tag);
                title = line.substring(3).trim();
                id = null;
                tag = null;
                keywords = null;
                body.setLength(0);
                meta = true;
                continue;
            }
            if (title == null || line.startsWith("# ")) {
                continue;
            }
            if (meta && line.startsWith("id:")) {
                id = line.substring(3).trim();
            } else if (meta && line.startsWith("tag:")) {
                tag = line.substring(4).trim();
            } else if (meta && line.startsWith("keywords:")) {
                keywords = line.substring(9).trim();
            } else {
                meta = false;
                body.append(line).append('\n');
            }
        }
        add(chunks, source, id, title, body, keywords, tag);
        return chunks;
    }

    private static void add(List<KnowledgeChunk> chunks, String source, String id, String title, StringBuilder body,
                            String keywords, String tag) {
        if (id != null && title != null) {
            chunks.add(new KnowledgeChunk(id, source, title, body.toString().strip(), keywords, tag));
        }
    }
}
