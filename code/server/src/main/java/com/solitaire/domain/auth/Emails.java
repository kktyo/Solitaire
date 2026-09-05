package com.solitaire.domain.auth;

public final class Emails {

    private Emails() {}

    public static String normalize(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase();
    }
}
