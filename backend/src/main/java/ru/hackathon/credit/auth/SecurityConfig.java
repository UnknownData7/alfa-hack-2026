package ru.hackathon.credit.auth;

import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless-безопасность на JWT. Права проверяются на сервере: клиентские и банковские эндпоинты разделены по ролям,
 * а принадлежность заявки клиенту проверяется в сервисах.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        // Метрики доступны только на служебном порту внутри сети Docker
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/api/ping").permitAll()
                        .requestMatchers("/api/dev/**", "/error").permitAll()
                        .requestMatchers("/api/bank/**").hasRole(Role.UNDERWRITER.name())
                        .requestMatchers("/api/client/**", "/api/applications/**", "/api/feed/**",
                                "/api/products/recommend", "/api/assistant/**").hasRole(Role.CLIENT.name())
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint((req, res, e) -> writeProblem(res, mapper, HttpStatus.UNAUTHORIZED,
                                "UNAUTHORIZED", "Требуется вход", "Войдите в систему заново: токен отсутствует или истёк")))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> writeProblem(res, mapper, HttpStatus.UNAUTHORIZED,
                                "UNAUTHORIZED", "Требуется вход", "Войдите в систему заново: токен отсутствует или истёк"))
                        .accessDeniedHandler((req, res, e) -> writeProblem(res, mapper, HttpStatus.FORBIDDEN,
                                "FORBIDDEN", "Нет доступа", "Недостаточно прав для этого действия")));
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtService jwtService) {
        return jwtService.decoder();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(JwtService.CLAIM_ROLES);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    private static void writeProblem(jakarta.servlet.http.HttpServletResponse res, ObjectMapper mapper,
                                     HttpStatus status, String code, String title, String detail)
            throws java.io.IOException {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setProperty("code", code);
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getOutputStream(), pd);
    }
}
