package org.architech.launcher.authentication.account;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Account {
    private AccountType type;
    private String userType;
    private String username;
    private String uuid;

    @JsonProperty
    private String accessToken;
    private long expiresAtSec;
    private String skinUrl;
    private String email;

    @JsonProperty
    private String mcAccessToken;
    private long  mcAccessTokenExpiresAt;

    @JsonProperty
    private String msaRefreshToken;
    private boolean ownsMinecraft;

    @JsonProperty
    private String launcherToken;

    public Account() {}

    public AccountType getType() { return type; }
    public void setType(AccountType type) { this.type = type; }

    public String getUserType() { return userType; }
    public void setUserType(String userType) { this.userType = userType; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public void setExpiresAtSec(long expiresAtSec) { this.expiresAtSec = expiresAtSec; }
    public long getExpiresAtSec() { return expiresAtSec; }

    public void setLauncherToken(String launcherToken) { this.launcherToken = launcherToken; }
    public String getLauncherToken() { return launcherToken; }

    public void setSkinUrl(String skinUrl) { this.skinUrl = skinUrl; }
    public String getSkinUrl() { return skinUrl; }

    public void setMsaRefreshToken(String msaRefreshToken) { this.msaRefreshToken = msaRefreshToken; }
    public String getMsaRefreshToken() { return msaRefreshToken; }

    @Override
    public String toString() {
        return "Account{" +
                "type=" + type +
                ", userType='" + userType + '\'' +
                ", username='" + username + '\'' +
                ", uuid='" + uuid + '\'' +
                ", expiresAtSec=" + expiresAtSec +
                ", ownsMinecraft=" + ownsMinecraft +
                '}';
    }

    @JsonIgnore
    public void clearSensitive() {
        this.accessToken = null;
        this.mcAccessToken = null;
        this.msaRefreshToken = null;
        this.launcherToken = null;
    }
}