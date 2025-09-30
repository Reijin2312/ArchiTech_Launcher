package org.architech.launcher.authentication.keystorage;

import java.util.Optional;

public interface SecretStorage {
    void putSecret(String key, String secret) throws Exception;

    Optional<String> getSecret(String key) throws Exception;

    void deleteSecret(String key) throws Exception;
}
