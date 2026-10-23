package ru.hackathon.credit.integration.mock;

import java.security.SecureRandom;

import ru.hackathon.credit.integration.SigningProvider;

/** Выпускает шестизначный код. В демо-режиме код показывается прямо в интерфейсе. */
public class MockSigningProvider implements SigningProvider {

    private final SecureRandom random = new SecureRandom();

    @Override
    public String issueCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }
}
