package com.solitaire.application;

import java.util.List;
import java.util.UUID;

public interface GameMoveRepository {

    void insert(GameMoveRow row);

    List<GameMoveRow> listByGameOrderBySeqDesc(UUID gameId);

    int nextSeq(UUID gameId);
}
