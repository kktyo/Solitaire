package com.solitaire.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    void insert(RefreshTokenRecord token);

    Optional<RefreshTokenRecord> findById(UUID id);

    void revoke(UUID id, Instant at);
}
