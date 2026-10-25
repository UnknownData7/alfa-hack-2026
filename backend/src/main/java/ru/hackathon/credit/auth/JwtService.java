package ru.hackathon.credit.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

/**
 * Выпуск и проверка JWT (HS256). Секрет берётся из переменной окружения JWT_SECRET, срок жизни короткий.
 */
@Service
public class JwtService {

    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_COMPANY = "cid";
    static final String ISSUER = "credit-cabinet";

    private final SecretKey key;
    private final Duration ttl;
    private final JwtEncoder encoder;

    public JwtService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.ttl-minutes}") long ttlMinutes) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET должен быть не короче 32 байт");
        }
        this.key = new SecretKeySpec(bytes, "HmacSHA256");
        this.ttl = Duration.ofMinutes(ttlMinutes);
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    public JwtDecoder decoder() {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    public IssuedToken issue(AppUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getLogin())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_ROLES, List.of(user.getRole().name()));
        if (user.getCompanyId() != null) {
            claims.claim(CLAIM_COMPANY, user.getCompanyId());
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
