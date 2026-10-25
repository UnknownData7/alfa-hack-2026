package ru.hackathon.credit.assistant.kb;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.hackathon.credit.assistant.ProductFacts;

/**
 * База знаний в PostgreSQL: Markdown-файлы из {@code knowledge/} и карточки продуктов из каталога индексируются
 * в {@code kb_chunk}, поиск полнотекстовый с русской морфологией. Индекс строится при старте и заново, если таблица
 * пуста (например после сброса демо-данных).
 */
@Service
public class PostgresKnowledgeBase implements KnowledgeBase {

    private static final Logger log = LoggerFactory.getLogger(PostgresKnowledgeBase.class);

    private static final RowMapper<KnowledgeChunk> MAPPER = (rs, i) -> new KnowledgeChunk(rs.getString("id"),
            rs.getString("source"), rs.getString("title"), rs.getString("body"), rs.getString("keywords"),
            rs.getString("tag"));

    private final JdbcTemplate jdbc;
    private final ProductFacts products;

    public PostgresKnowledgeBase(JdbcTemplate jdbc, ProductFacts products) {
        this.jdbc = jdbc;
        this.products = products;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void indexOnStartup() {
        reindex();
    }

    @Transactional
    public synchronized int reindex() {
        List<KnowledgeChunk> chunks = new ArrayList<>(KnowledgeParser.loadAll());
        for (ProductFacts.ProductInfo p : products.all()) {
            chunks.add(new KnowledgeChunk("product." + p.code(), "product", p.name(), p.summary(),
                    "продукт условия " + p.name(), p.code()));
        }
        jdbc.update("delete from kb_chunk");
        jdbc.batchUpdate("insert into kb_chunk (id, source, title, body, keywords, tag, version) values (?, ?, ?, ?, ?, ?, ?)",
                chunks, chunks.size(), (ps, c) -> {
                    ps.setString(1, c.id());
                    ps.setString(2, c.source());
                    ps.setString(3, c.title());
                    ps.setString(4, c.body());
                    ps.setString(5, c.keywords());
                    ps.setString(6, c.tag());
                    ps.setString(7, version(c));
                });
        log.info("assistant knowledge base indexed chunks={}", chunks.size());
        return chunks.size();
    }

    @Override
    public Optional<KnowledgeChunk> get(String id) {
        ensureIndexed();
        return jdbc.query("select * from kb_chunk where id = ?", MAPPER, id).stream().findFirst();
    }

    @Override
    public List<KnowledgeChunk> search(String query, Set<String> sources, int limit) {
        List<String> terms = KnowledgeBase.terms(query);
        if (terms.isEmpty() || sources.isEmpty()) {
            return List.of();
        }
        ensureIndexed();
        // Слова проверены регулярным выражением (только буквы и цифры), поэтому их можно собрать в tsquery через «или»
        String tsquery = String.join(" | ", terms);
        String placeholders = String.join(",", sources.stream().map(s -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(tsquery);
        args.addAll(sources);
        args.add(limit);
        return jdbc.query("select c.* from kb_chunk c, to_tsquery('russian', ?) q where c.tsv @@ q and c.source in ("
                + placeholders + ") order by ts_rank_cd(c.tsv, q) desc, c.id limit ?", MAPPER, args.toArray());
    }

    private void ensureIndexed() {
        Integer count = jdbc.queryForObject("select count(*) from kb_chunk", Integer.class);
        if (count == null || count == 0) {
            reindex();
        }
    }

    private static String version(KnowledgeChunk c) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((c.title() + "\n" + c.body() + "\n" + c.keywords()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
