package org.architech.launcher.auth;

public final class Auth {
    private static Account CURRENT;

    public static synchronized Account current(){
        if(CURRENT==null) CURRENT = CurrentAccountStore.load();
        return CURRENT;
    }

    public static synchronized void set(Account a){ CURRENT = a; CurrentAccountStore.save(a); }

    public static synchronized void clear(){ CURRENT = null; CurrentAccountStore.save(null); }

    public static synchronized boolean isLogged(){ return CURRENT!=null && CURRENT.type!=AccountType.OFFLINE; }

    public static synchronized void updateOfflineName(String name) {
        if(CURRENT != null && CURRENT.type==AccountType.OFFLINE){
            CURRENT.username = name;
            CURRENT.uuid = Uuids.offlineUuid(name);
            CurrentAccountStore.save(CURRENT);
        }
    }
}