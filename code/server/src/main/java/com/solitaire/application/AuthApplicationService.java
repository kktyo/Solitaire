package com.solitaire.application;

import com.solitaire.domain.auth.Emails;
import com.solitaire.domain.auth.TokenHashes;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Transactional
public class AuthApplicationService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Duration ACCESS_TTL = Duration.ofMinutes(60);
    private static final Duration REFRESH_TTL = Duration.ofDays(30);

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordHasher passwords;
    private final TokenIssuer tokens;
    private final Clock clock;

    public AuthApplicationService(
            UserRepository users,
            RefreshTokenRepository refreshTokens,
            PasswordHasher passwords,
            TokenIssuer tokens,
            Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwords = passwords;
        this.tokens = tokens;
        this.clock = clock;
    }

    public AuthTokens register(String emailRaw, String password) {
        String email = Emails.normalize(emailRaw);
        validateCredentials(email, password);
        if (users.findByNormalizedEmail(email).isPresent()) {
            throw AppException.emailTaken();
        }
        Instant now = clock.instant();
        UserRecord user = new UserRecord(UUID.randomUUID(), email, passwords.hash(password), now, now);
        users.insert(user);
        return issue(user);
    }

    public AuthTokens login(String emailRaw, String password) {
        String email = Emails.normalize(emailRaw);
        if (email.isBlank() || password == null) {
            throw AppException.unauthorized();
        }
        UserRecord user = users.findByNormalizedEmail(email).orElse(null);
        if (user == null || !passwords.matches(password, user.passwordHash())) {
            throw AppException.unauthorized();
        }
        return issue(user);
    }

    public AuthTokens refresh(String refreshToken) {
        var parsed = tokens.parseRefresh(refreshToken);
        RefreshTokenRecord row = refreshTokens
                .findById(parsed.jti())
                .orElseThrow(AppException::unauthorized);
        Instant now = clock.instant();
        if (row.revokedAt() != null || row.expiresAt().isBefore(now)) {
            throw AppException.unauthorized();
        }
        if (!row.tokenHash().equals(TokenHashes.sha256(refreshToken))) {
            throw AppException.unauthorized();
        }
        if (!row.userId().equals(parsed.userId())) {
            throw AppException.unauthorized();
        }
        UserRecord user = users.findById(row.userId()).orElseThrow(AppException::unauthorized);
        refreshTokens.revoke(row.id(), now);
        return issue(user);
    }

    public void logout(UUID userId, String refreshToken) {
        var parsed = tokens.parseRefresh(refreshToken);
        RefreshTokenRecord row = refreshTokens.findById(parsed.jti()).orElse(null);
        if (row == null || !row.userId().equals(userId)) {
            return;
        }
        refreshTokens.revoke(row.id(), clock.instant());
    }

    private AuthTokens issue(UserRecord user) {
        Instant now = clock.instant();
        UUID jti = UUID.randomUUID();
        String access = tokens.issueAccess(user.id(), now.plus(ACCESS_TTL));
        String refresh = tokens.issueRefresh(user.id(), jti, now.plus(REFRESH_TTL));
        refreshTokens.insert(new RefreshTokenRecord(
                jti, user.id(), TokenHashes.sha256(refresh), now.plus(REFRESH_TTL), null, now));
        return new AuthTokens(user.id(), user.email(), access, refresh, ACCESS_TTL.toSeconds());
    }

    private static void validateCredentials(String email, String password) {
        if (email.isBlank() || !EMAIL.matcher(email).matches()) {
            throw AppException.validation("メールアドレスの形式が正しくありません。");
        }
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw AppException.validation("パスワードは8文字以上にしてください。");
        }
    }

    public record AuthTokens(UUID userId, String email, String accessToken, String refreshToken, long expiresIn) {}

    public interface PasswordHasher {
        String hash(String raw);

        boolean matches(String raw, String hash);
    }

    public interface TokenIssuer {
        String issueAccess(UUID userId, Instant exp);

        String issueRefresh(UUID userId, UUID jti, Instant exp);

        ParsedRefresh parseRefresh(String token);
    }

    public record ParsedRefresh(UUID userId, UUID jti) {}
}
