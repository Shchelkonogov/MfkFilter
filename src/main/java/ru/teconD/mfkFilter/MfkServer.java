package ru.teconD.mfkFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Класс сервер для получения данных от контроллера MFK
 * @author Maksim Shchelkonogov
 */
public final class MfkServer {

    private static final Logger LOGGER = Logger.getLogger(MfkServer.class.getName());

    private static volatile MfkServer instance;

    private String localHost;

    static String remoteHost;
    static List<String> permitData = new ArrayList<>();

    private ServerSocket serverSocket;

    private MfkServer() throws IOException, NoSuchFieldException, URISyntaxException {
        Path path = Paths.get(MfkServer.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .getParent()
                .resolve("permitData");
        if (Files.exists(path)) {
            permitData = Files.readAllLines(path);
        } else {
            throw new NoSuchFieldException("no permitData file exists " + path);
        }
    }

    public static MfkServer getInstance() {
        if (instance == null) {
            synchronized (MfkServer.class) {
                if (instance == null) {
                    try {
                        instance = new MfkServer();
                    } catch (IOException | NoSuchFieldException | URISyntaxException e) {
                        LOGGER.log(Level.WARNING, "error load permitData file", e);
                    }
                }
            }
        }
        return instance;
    }

    /**
     * Запуск сервера
     * @throws IOException в случае ошибки создания {@link ServerSocket}
     */
    public void start() throws IOException {
        if ((serverSocket != null) && (!serverSocket.isClosed())) {
            return;
        }

        if ((localHost != null) && (!localHost.equals(""))) {
            serverSocket = new ServerSocket(20100, 10, InetAddress.getByName(localHost));
            LOGGER.log(Level.INFO, "created server socket for local address {0}", localHost);
        } else {
            serverSocket = new ServerSocket(20100, 10);
            LOGGER.info("created server socket");
        }

        Socket socket;

        while (!serverSocket.isClosed()) {
            try {
                socket = serverSocket.accept();
                new MfkClient(socket);
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    LOGGER.log(Level.WARNING, "error create socket connection", e);
                } else {
                    LOGGER.warning("Server socket is closed");
                }
            }
        }
    }

    /**
     * Остановка сервера
     */
    public void stop() {
        if ((serverSocket != null) && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "error close server socket", e);
            }
        }
    }

    public void setRemoteHost(String remoteHost) {
        MfkServer.remoteHost = remoteHost;
    }

    public void setLocalHost(String localHost) {
        this.localHost = localHost;
    }
}
