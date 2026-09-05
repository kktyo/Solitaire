package com.solitaire.api;

import com.solitaire.application.AppException;
import com.solitaire.application.GameMoveRepository;
import com.solitaire.application.GameRecord;
import com.solitaire.infra.db.BoardJsonCodec;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private final BoardJsonCodec codec;
    private final GameMoveRepository moves;

    public ApiExceptionHandler(BoardJsonCodec codec, GameMoveRepository moves) {
        this.codec = codec;
        this.moves = moves;
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<Map<String, Object>> handle(AppException ex) {
        Map<String, Object> details = new LinkedHashMap<>(ex.details());
        Object game = details.get("game");
        if (game instanceof GameRecord g) {
            boolean canUndo = "IN_PROGRESS".equals(g.getStatus())
                    && com.solitaire.application.GameApplicationService.findUndoTarget(
                                    moves.listByGameOrderBySeqDesc(g.getId()))
                            != null;
            details.put("game", GameResponses.of(g, canUndo, codec));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", ex.code());
        body.put("message", ex.getMessage());
        body.put("details", details);
        return ResponseEntity.status(ex.status()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex) {
        Map<String, Object> fields = new LinkedHashMap<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(err -> fields.put(err.getField(), err.getDefaultMessage()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "VALIDATION_ERROR");
        body.put("message", "入力が正しくありません。");
        body.put("details", fields);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> other(Exception ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "INTERNAL_ERROR");
        body.put("message", "時間をおいて再度お試しください。");
        body.put("details", Map.of());
        return ResponseEntity.status(500).body(body);
    }
}
