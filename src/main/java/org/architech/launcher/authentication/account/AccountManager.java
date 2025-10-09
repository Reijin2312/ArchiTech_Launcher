package org.architech.launcher.authentication.account;

public final class AccountManager {
    private static Account CURRENT;

    public static synchronized Account getCurrentAccount() {
        if(CURRENT==null) CURRENT = AccountStore.load();
        return CURRENT;
    }

    public static synchronized void setCurrentAccount(Account a) {
        CURRENT = a; AccountStore.save(a);
    }

    public static synchronized void clear() {
        CURRENT = null; AccountStore.save(null);
    }

    public static synchronized boolean isLogged() {
        return CURRENT != null;
    }
}