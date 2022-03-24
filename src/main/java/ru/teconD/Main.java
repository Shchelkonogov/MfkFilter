package ru.teconD;

import ru.teconD.mfkFilter.MfkServer;
import ru.teconD.mfkFilter.util.PasswordReader;

import java.io.IOException;
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

        String remoteHost = null;
        String localHost = null;

        for (String arg: args) {
            if (arg.startsWith("-remote=")) {
                remoteHost = arg.replaceFirst("-remote=", "");
            }
            if (arg.startsWith("-local=")) {
                localHost = arg.replaceFirst("-local=", "");
            }
        }

        PasswordReader reader = new PasswordReader();

        if ((remoteHost == null) || remoteHost.equals("")) {
            LOGGER.warning("remote host parameter required (-remote=<value>)");
            remoteHost = reader.readLine("enter remote host: ");
        }

        LOGGER.log(Level.INFO, "init parameters remoteHost: {0} localHost: {1}", new Object[] {remoteHost, localHost});

        mfkServer.setRemoteHost(remoteHost);
        mfkServer.setLocalHost(localHost);

        try {
            mfkServer.start();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "error start filter server", e);
        }
    }
}
