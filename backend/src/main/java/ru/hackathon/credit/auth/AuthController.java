package ru.hackathon.credit.auth;

import java.time.Instant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import ru.hackathon.credit.audit.AuditService;
import ru.hackathon.credit.common.ApiException;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Вход")
public class AuthController {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService audit;

    public AuthController(AppUserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                          AuditService audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.audit = audit;
    }

    public record LoginRequest(@NotBlank(message = "Укажите логин") String login,
                               @NotBlank(message = "Укажите пароль") String password) {
    }

    public record UserDto(Long id, String login, String fullName, Role role, Long companyId) {
    }

    public record LoginResponse(String token, Instant expiresAt, UserDto user) {
    }

    @PostMapping("/login")
    @Operation(summary = "Вход по логину и паролю, возвращает JWT")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AppUser user = users.findByLogin(request.login().trim())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS",
                        "Неверный логин или пароль"));
        JwtService.IssuedToken token = jwtService.issue(user);
        audit.log(null, user.getRole() == Role.UNDERWRITER ? "BANK" : "CLIENT", user.getId(), "LOGIN", null);
        return new LoginResponse(token.token(), token.expiresAt(), toDto(user));
    }

    @GetMapping("/me")
    @Operation(summary = "Текущий пользователь и роль")
    public UserDto me() {
        CurrentUser current = CurrentUser.get();
        AppUser user = users.findById(current.id()).orElseThrow(() -> ApiException.notFound("Учётная запись не найдена"));
        return toDto(user);
    }

    static UserDto toDto(AppUser u) {
        return new UserDto(u.getId(), u.getLogin(), u.getFullName(), u.getRole(), u.getCompanyId());
    }
}
