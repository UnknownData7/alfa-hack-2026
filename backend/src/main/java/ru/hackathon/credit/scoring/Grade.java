package ru.hackathon.credit.scoring;

public enum Grade {
    A, B, C, D;

    public static Grade fromScore(int score) {
        if (score >= 80) {
            return A;
        }
        if (score >= 65) {
            return B;
        }
        if (score >= 50) {
            return C;
        }
        return D;
    }
}
