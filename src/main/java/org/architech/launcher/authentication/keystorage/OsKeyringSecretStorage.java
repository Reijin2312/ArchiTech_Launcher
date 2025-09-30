package org.architech.launcher.authentication.keystorage;

import com.github.javakeyring.Keyring;
import com.github.javakeyring.PasswordAccessException;
import java.util.Optional;

public class OsKeyringSecretStorage implements SecretStorage {
    private final String domain;

    public OsKeyringSecretStorage(String domain) {
        this.domain = domain;
    }

    @Override
    public void putSecret(String key, String secret) throws Exception {
        try (Keyring kr = Keyring.create()) {
            kr.setPassword(domain, key, secret);
        }
    }

    @Override
    public Optional<String> getSecret(String key) throws Exception {
        try (Keyring kr = Keyring.create()) {
            try {
                String s = kr.getPassword(domain, key);
                return Optional.ofNullable(s);
            } catch (PasswordAccessException pae) {
                return Optional.empty();
            }
        }
    }

    @Override
    public void deleteSecret(String key) throws Exception {
        try (Keyring kr = Keyring.create()) {
            kr.deletePassword(domain, key);
        }
    }
}
