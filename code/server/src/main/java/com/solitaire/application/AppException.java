package com.solitaire.application;

import java.util.Map;

public class AppException extends RuntimeException {

    private final int status;
    private final String code;
    private final Map<String, Object> details;

    public AppException(int status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? Map.of() : details;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }

    public static AppException validation(String message) {
        return new AppException(400, "VALIDATION_ERROR", message, Map.of());
    }

    public static AppException emailTaken() {
        return new AppException(400, "EMAIL_TAKEN", "このメールアドレスは既に使われています。", Map.of());
    }

    public static AppException unauthorized() {
        return new AppException(401, "UNAUTHORIZED", "認証に失敗しました。", Map.of());
    }

    public static AppException forbidden() {
        return new AppException(403, "FORBIDDEN", "権限がありません。", Map.of());
    }

    public static AppException notFound(String message) {
        return new AppException(404, "NOT_FOUND", message, Map.of());
    }
}
