package ru.hackathon.credit.scoring;

/**
 * Один фактор скоринга: что учитывалось, с каким значением и сколько баллов дало.
 */
public record Factor(String code, String title, String value, int points, String comment) {
}
