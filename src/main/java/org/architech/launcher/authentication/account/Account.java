package org.architech.launcher.authentication.account;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Account {
    private String username;
    private String uuid;
    private String email;
    private String skinUrl;

    @JsonProperty
    private String accessToken;
    private long accessExpiresAtSec;

    @JsonProperty
    private String refreshToken;
    private long refreshExpiresAtSec;

    public Account() {}

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getSkinUrl() { return skinUrl; }
    public void setSkinUrl(String skinUrl) { this.skinUrl = skinUrl; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public long getAccessExpiresAtSec() { return accessExpiresAtSec; }
    public void setAccessExpiresAtSec(long accessExpiresAtSec) { this.accessExpiresAtSec = accessExpiresAtSec; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public long getRefreshExpiresAtSec() { return refreshExpiresAtSec; }
    public void setRefreshExpiresAtSec(long refreshExpiresAtSec) { this.refreshExpiresAtSec = refreshExpiresAtSec; }

    @JsonIgnore
    public void clearSensitive() {
        this.accessToken = null;
        this.refreshToken = null;
    }

    @Override public String toString() {
        return "Account{" +
                "username='" + username + '\'' +
                ", uuid='" + uuid + '\'' +
                ", accessExp=" + accessExpiresAtSec +
                '}';
    }
}