package com.solitaire.application;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    void insert(UserRecord user);

    Optional<UserRecord> findByNormalizedEmail(String email);

    Optional<UserRecord> findById(UUID id);
}
