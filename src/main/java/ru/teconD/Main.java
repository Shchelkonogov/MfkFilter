package ru.teconD;

import ru.teconD.mfkFilter.MfkServer;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Maksim Shchelkonogov
 */
public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
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

        if ((remoteHost == null) || remoteHost.equals("")) {
            LOGGER.warning("remote host parameter required (-remote=<value>)");
            return;
        }

        mfkServer.setRemoteHost(remoteHost);
        mfkServer.setLocalHost(localHost);

        try {
            mfkServer.start();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "error start filter server", e);
        }
    }
}
