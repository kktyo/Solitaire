package com.solitaire.infra.security;

import com.solitaire.application.AuthApplicationService;
import org.springframework.stereotype.Component;

@Component
public class BcryptPasswordHasher implements AuthApplicationService.PasswordHasher {

    private final org.springframework.security.crypto.password.PasswordEncoder encoder =
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(10);

    @Override
    public String hash(String raw) {
        return encoder.encode(raw);
    }

    @Override
    public boolean matches(String raw, String hash) {
        return encoder.matches(raw, hash);
    }
}
