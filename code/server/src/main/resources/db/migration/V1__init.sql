CREATE TABLE users (
    id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    email NVARCHAR(256) NOT NULL,
    password_hash NVARCHAR(255) NOT NULL,
    created_at DATETIME2 NOT NULL,
    updated_at DATETIME2 NOT NULL
);
CREATE UNIQUE INDEX UX_users_email ON users(email);

CREATE TABLE refresh_tokens (
    id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    user_id UNIQUEIDENTIFIER NOT NULL,
    token_hash NVARCHAR(255) NOT NULL,
    expires_at DATETIME2 NOT NULL,
    revoked_at DATETIME2 NULL,
    created_at DATETIME2 NOT NULL,
    CONSTRAINT FK_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE UNIQUE INDEX UX_refresh_tokens_hash ON refresh_tokens(token_hash);
CREATE INDEX IX_refresh_tokens_user ON refresh_tokens(user_id);

CREATE TABLE games (
    id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    user_id UNIQUEIDENTIFIER NOT NULL,
    status NVARCHAR(16) NOT NULL,
    version INT NOT NULL,
    move_count INT NOT NULL,
    elapsed_ms BIGINT NOT NULL,
    timing_started_at DATETIME2 NULL,
    board_json NVARCHAR(MAX) NOT NULL,
    started_at DATETIME2 NOT NULL,
    updated_at DATETIME2 NOT NULL,
    cleared_at DATETIME2 NULL,
    CONSTRAINT FK_games_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE UNIQUE INDEX UX_games_user_in_progress ON games(user_id) WHERE status = N'IN_PROGRESS';
CREATE INDEX IX_games_user_status ON games(user_id, status, cleared_at);

CREATE TABLE game_moves (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    game_id UNIQUEIDENTIFIER NOT NULL,
    seq INT NOT NULL,
    kind NVARCHAR(8) NOT NULL,
    move_json NVARCHAR(MAX) NULL,
    board_before_json NVARCHAR(MAX) NOT NULL,
    move_count_before INT NOT NULL,
    elapsed_ms_before BIGINT NOT NULL,
    created_at DATETIME2 NOT NULL,
    CONSTRAINT FK_game_moves_game FOREIGN KEY (game_id) REFERENCES games(id),
    CONSTRAINT UQ_game_moves_game_seq UNIQUE (game_id, seq)
);

CREATE TABLE game_results (
    game_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    user_id UNIQUEIDENTIFIER NOT NULL,
    move_count INT NOT NULL,
    elapsed_ms BIGINT NOT NULL,
    cleared_at DATETIME2 NOT NULL,
    CONSTRAINT FK_game_results_game FOREIGN KEY (game_id) REFERENCES games(id),
    CONSTRAINT FK_game_results_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX IX_game_results_user_cleared ON game_results(user_id, cleared_at DESC);
