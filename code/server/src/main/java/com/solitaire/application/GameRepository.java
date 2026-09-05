package com.solitaire.application;

import java.util.Optional;
import java.util.UUID;

public interface GameRepository {

    void insert(GameRecord game);

    Optional<GameRecord> findByIdForUpdate(UUID id);

    Optional<GameRecord> findInProgressByUserForUpdate(UUID userId);

    Optional<GameRecord> findInProgressByUser(UUID userId);

    void update(GameRecord game);
}
