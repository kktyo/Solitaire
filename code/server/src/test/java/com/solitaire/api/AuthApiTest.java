package com.solitaire.api;

import com.solitaire.application.AppException;
import com.solitaire.application.AuthApplicationService;
import com.solitaire.application.GameApplicationService;
import com.solitaire.application.GameMoveRepository;
import com.solitaire.config.SecurityConfig;
import com.solitaire.infra.db.BoardJsonCodec;
import com.solitaire.infra.security.JwtTokenIssuer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {com.solitaire.api.auth.AuthController.class, com.solitaire.api.game.GameController.class})
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class AuthApiTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AuthApplicationService auth;

    @MockitoBean
    GameApplicationService games;

    @MockitoBean
    GameMoveRepository moves;

    @MockitoBean
    BoardJsonCodec codec;

    @MockitoBean
    JwtTokenIssuer jwt;

    @Test
    void gamesCurrentWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/games/current")).andExpect(status().isUnauthorized());
    }

    @Test
    void registerDuplicateIs400() throws Exception {
        when(auth.register(anyString(), anyString())).thenThrow(AppException.emailTaken());
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"password\":\"password1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void unknownLoginIs401() throws Exception {
        when(auth.login(anyString(), anyString())).thenThrow(AppException.unauthorized());
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@example.com\",\"password\":\"password1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
