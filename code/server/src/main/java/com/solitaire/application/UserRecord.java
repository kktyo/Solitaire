package com.solitaire.application;

import java.time.Instant;
import java.util.UUID;

public record UserRecord(UUID id, String email, String passwordHash, Instant createdAt, Instant updatedAt) {}
