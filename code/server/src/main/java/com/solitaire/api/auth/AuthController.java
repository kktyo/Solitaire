package com.solitaire.api.auth;

import com.solitaire.api.GameResponses;
import com.solitaire.application.AuthApplicationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthApplicationService auth;

    public AuthController(AuthApplicationService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody CredentialsRequest body) {
        var tokens = auth.register(body == null ? null : body.email(), body == null ? null : body.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(body(tokens));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody CredentialsRequest body) {
        return body(auth.login(body == null ? null : body.email(), body == null ? null : body.password()));
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh(@RequestBody RefreshRequest body) {
        return body(auth.refresh(body == null ? null : body.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshRequest body) {
        auth.logout(GameResponses.currentUser(), body == null ? null : body.refreshToken());
        return ResponseEntity.noContent().build();
    }

    private static Map<String, Object> body(AuthApplicationService.AuthTokens t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", t.userId().toString());
        m.put("email", t.email());
        m.put("accessToken", t.accessToken());
        m.put("refreshToken", t.refreshToken());
        m.put("expiresIn", t.expiresIn());
        return m;
    }
}
