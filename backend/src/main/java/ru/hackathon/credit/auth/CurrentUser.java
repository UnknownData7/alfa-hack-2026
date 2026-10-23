package ru.hackathon.credit.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import ru.hackathon.credit.common.ApiException;

/** Текущий пользователь из проверенного JWT. */
public record CurrentUser(Long id, String login, Role role, Long companyId) {

    public static CurrentUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Требуется вход");
        }
        Object cid = jwt.getClaims().get(JwtService.CLAIM_COMPANY);
        return new CurrentUser(
                ((Number) jwt.getClaims().get(JwtService.CLAIM_USER_ID)).longValue(),
                jwt.getSubject(),
                Role.valueOf(jwt.getClaimAsStringList(JwtService.CLAIM_ROLES).get(0)),
                cid == null ? null : ((Number) cid).longValue());
    }

    /** Компания клиента. Для сотрудника банка вызывать нельзя. */
    public Long requireCompanyId() {
        if (companyId == null) {
            throw ApiException.forbidden("Действие доступно только клиенту");
        }
        return companyId;
    }
}
