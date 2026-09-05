package com.solitaire.application;

import java.util.List;
import java.util.UUID;

public interface GameResultRepository {

    void insert(GameResultRow row);

    List<GameResultRow> listByUserOrderByClearedAtDesc(UUID userId, int limit);
}
