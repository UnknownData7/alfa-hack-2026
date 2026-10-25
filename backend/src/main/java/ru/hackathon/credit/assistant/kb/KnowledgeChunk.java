package ru.hackathon.credit.assistant.kb;

/** Фрагмент базы знаний: раздел Markdown-файла или карточка продукта. */
public record KnowledgeChunk(String id, String source, String title, String body, String keywords, String tag) {
}
