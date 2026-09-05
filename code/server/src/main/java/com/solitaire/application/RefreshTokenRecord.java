package com.solitaire.application;

import java.time.Instant;
import java.util.UUID;

public record RefreshTokenRecord(
        UUID id, UUID userId, String tokenHash, Instant expiresAt, Instant revokedAt, Instant createdAt) {}
