package org.architech.launcher.utils.logging;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class LogManager {
    private static Logger logger;

    public static Logger getLogger() {
        if (logger == null) {
            setupLogger();
        }
        return logger;
    }

    public static void setupLogger() {
        logger = Logger.getLogger("GlobalLogger");
        logger.setUseParentHandlers(false);

        try {
            FileHandler fh = new FileHandler("launcher.log", false);
            fh.setEncoding("UTF-8");

            fh.setFormatter(new SimpleFormatter());

            logger.addHandler(fh);

        } catch (IOException e) {
            e.printStackTrace();
        }

        logger.setLevel(Level.ALL);
    }
}
