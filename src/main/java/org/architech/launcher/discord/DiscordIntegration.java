package org.architech.launcher.discord;

import net.arikia.dev.drpc.DiscordEventHandlers;
import net.arikia.dev.drpc.DiscordRichPresence;
import net.arikia.dev.drpc.DiscordRPC;
import org.architech.launcher.utils.LogManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DiscordIntegration {
    private static final String APP_ID = "1409949210568298647";

    private static final ScheduledExecutorService discordExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "RPC-Callback-Handler");
        t.setDaemon(true);
        return t;
    });

    public static void start() {
        DiscordEventHandlers handlers = new DiscordEventHandlers.Builder().build();
        DiscordRPC.discordInitialize(APP_ID, handlers, true);

        DiscordRichPresence presence = new DiscordRichPresence.Builder("В лаунчере")
                .setDetails("Загрузка клиента")
                .setBigImage("embedded_cover", "Launcher")
                .build();

        DiscordRPC.discordUpdatePresence(presence);

        discordExecutor.scheduleWithFixedDelay(() -> {
            try { DiscordRPC.discordRunCallbacks(); } catch (Throwable t) { LogManager.getLogger().warning("RPC callback failed: " + t.getMessage()); }
        }, 0, 2, TimeUnit.SECONDS);

        LogManager.getLogger().info("Запуск интеграции с discord...");
    }

    public static void stop() {
        discordExecutor.shutdownNow();
        try {
            discordExecutor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        DiscordRPC.discordShutdown();
        LogManager.getLogger().info("Остановка интеграции с discord...");
    }

}
