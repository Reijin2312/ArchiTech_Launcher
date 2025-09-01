package org.architech.launcher.discord;

import net.arikia.dev.drpc.DiscordEventHandlers;
import net.arikia.dev.drpc.DiscordRichPresence;
import net.arikia.dev.drpc.DiscordRPC;

public class DiscordIntegration {
    private static final String APP_ID = "1409949210568298647";

    private static Thread rpcThread;

    public static void start() {
        DiscordEventHandlers handlers = new DiscordEventHandlers.Builder().build();
        DiscordRPC.discordInitialize(APP_ID, handlers, true);

        DiscordRichPresence presence = new DiscordRichPresence.Builder("В лаунчере")
                .setDetails("Загрузка клиента")
                .setBigImage("embedded_cover", "Launcher")
                .build();

        DiscordRPC.discordUpdatePresence(presence);

        rpcThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                DiscordRPC.discordRunCallbacks();
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "RPC-Callback-Handler");

        rpcThread.setDaemon(true);
        rpcThread.start();
    }

    public static void stop() {
        if (rpcThread != null && rpcThread.isAlive()) {
            rpcThread.interrupt();
            try {
                rpcThread.join(1000);
            } catch (InterruptedException ignored) {}
        }
        DiscordRPC.discordShutdown();
    }
}
