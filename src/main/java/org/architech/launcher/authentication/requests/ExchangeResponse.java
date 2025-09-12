package org.architech.launcher.authentication.requests;

import org.architech.launcher.authentication.ely_by.ElyProfile;

public class ExchangeResponse {
    public String launcherToken;
    public String launcherTokenExpiresAt;
    public ElyProfile profile;
}
