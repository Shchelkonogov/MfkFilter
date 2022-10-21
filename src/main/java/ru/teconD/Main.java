package ru.teconD;

import ru.teconD.mfkFilter.MfkServer;
import ru.teconD.mfkFilter.util.PasswordReader;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * @author Maksim Shchelkonogov
 */
public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) throws IOException {
        LogManager.getLogManager().readConfiguration(Main.class.getResourceAsStream("/log.properties"));

        MfkServer mfkServer = MfkServer.getInstance();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                LOGGER.info("Shutting down...");
                Thread.sleep(200);
                if (mfkServer != null) {
                    mfkServer.stop();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.log(Level.WARNING, "error shutdown hook", e);
            }
        }));

        Properties prop = new Properties();
        for (String arg: args) {
            if (arg.startsWith("-") && arg.contains("=")) {
                String[] split = arg.split("=");
                prop.setProperty(split[0].substring(1), split[1]);
            }
        }

        PasswordReader reader = new PasswordReader();

        if ((prop.getProperty("remote") == null) || (prop.getProperty("remote").equals(""))) {
            LOGGER.warning("remote host parameter required (-remote=<value>)");
            prop.setProperty("remote", reader.readLine("enter remote host: "));
        }

        LOGGER.log(Level.INFO, "init parameters {0}", prop);

        mfkServer.setRemoteHost(prop.getProperty("remote"));
        mfkServer.setLocalHost(prop.getProperty("local"));

        if (prop.getProperty("permitHosts") != null) {
            mfkServer.setPermitHosts(new HashSet<>(Arrays.asList(prop.getProperty("permitHosts").split(","))));
        }

        try {
            mfkServer.start();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "error start filter server", e);
        }
    }
}
