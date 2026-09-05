package com.solitaire.infra.security;

import com.solitaire.application.AuthApplicationService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenIssuer implements AuthApplicationService.TokenIssuer {

    private final SecretKey accessKey;
    private final SecretKey refreshKey;

    public JwtTokenIssuer(
            @Value("${solitaire.jwt.access-secret}") String accessSecret,
            @Value("${solitaire.jwt.refresh-secret}") String refreshSecret) {
        this.accessKey = Keys.hmacShaKeyFor(pad(accessSecret).getBytes(StandardCharsets.UTF_8));
        this.refreshKey = Keys.hmacShaKeyFor(pad(refreshSecret).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String issueAccess(UUID userId, Instant exp) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("typ", "access")
                .issuedAt(new Date())
                .expiration(Date.from(exp))
                .signWith(accessKey)
                .compact();
    }

    @Override
    public String issueRefresh(UUID userId, UUID jti, Instant exp) {
        return Jwts.builder()
                .subject(userId.toString())
                .id(jti.toString())
                .claim("typ", "refresh")
                .issuedAt(new Date())
                .expiration(Date.from(exp))
                .signWith(refreshKey)
                .compact();
    }

    @Override
    public AuthApplicationService.ParsedRefresh parseRefresh(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(refreshKey).build().parseSignedClaims(token).getPayload();
            if (!"refresh".equals(c.get("typ", String.class))) {
                throw com.solitaire.application.AppException.unauthorized();
            }
            return new AuthApplicationService.ParsedRefresh(UUID.fromString(c.getSubject()), UUID.fromString(c.getId()));
        } catch (com.solitaire.application.AppException e) {
            throw e;
        } catch (Exception e) {
            throw com.solitaire.application.AppException.unauthorized();
        }
    }

    public UUID parseAccess(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(accessKey).build().parseSignedClaims(token).getPayload();
            if (!"access".equals(c.get("typ", String.class))) {
                throw com.solitaire.application.AppException.unauthorized();
            }
            return UUID.fromString(c.getSubject());
        } catch (com.solitaire.application.AppException e) {
            throw e;
        } catch (Exception e) {
            throw com.solitaire.application.AppException.unauthorized();
        }
    }

    private static String pad(String secret) {
        if (secret == null) {
            secret = "";
        }
        if (secret.length() >= 32) {
            return secret;
        }
        return (secret + "0".repeat(32)).substring(0, 32);
    }
}
