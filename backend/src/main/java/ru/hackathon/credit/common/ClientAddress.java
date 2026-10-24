package ru.hackathon.credit.common;

import jakarta.servlet.http.HttpServletRequest;

/** Адрес клиента с учётом прокси nginx (первый адрес из X-Forwarded-For). */
public final class ClientAddress {

    private ClientAddress() {
    }

    public static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
