
package org.architech.launcher.auth;

public class GameParams {
    public String accessToken;
    public Profile selectedProfile;
    public String skinUrl;

    public static class Profile {
        public String id;
        public String name;
        public String uuid;
    }
}