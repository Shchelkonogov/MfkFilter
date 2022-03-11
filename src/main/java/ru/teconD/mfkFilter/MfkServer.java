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
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Класс сервер для получения данных от контроллера MFK
 * @author Maksim Shchelkonogov
 */
public final class MfkServer {

    private static final Logger LOGGER = Logger.getLogger(MfkServer.class.getName());

    private static volatile MfkServer instance;

    static volatile boolean block = false;

    static String remoteHost;
    static List<String> permitData = new ArrayList<>();

    private String localHost;
    private ServerSocket serverSocket;
    private ThreadPoolExecutor service;

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

        BlockingQueue<Runnable> boundedQueue = new ArrayBlockingQueue<>(10);
        service = new ThreadPoolExecutor(10, 10, 60, SECONDS, boundedQueue, new ThreadPoolExecutor.AbortPolicy());

        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();

                LOGGER.log(Level.INFO, "new socket {0}", socket.getRemoteSocketAddress());

                if (block) {
                    LOGGER.info("one connection is already accepted. This connection is ignore");

                    service.submit(new MfkClientIgnore(socket));
                } else {
                    block = true;
                    service.submit(new MfkClient(socket));

                    LOGGER.log(Level.INFO, "thread statistic pool size = {0}, active threads = {1}, queued tasks = {2}, completed tasks = {3}",
                            new Object[] {service.getPoolSize(), service.getActiveCount(), service.getQueue().size(), service.getCompletedTaskCount()});
                }
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    LOGGER.log(Level.WARNING, "error create socket connection", e);
                } else {
                    LOGGER.warning("Server socket is closed");
                }
            } catch (RejectedExecutionException ignore) {
                if (block) {
                    block = false;
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

        if ((service != null) && !service.isShutdown()) {
            service.shutdown();
        }
    }

    public void setRemoteHost(String remoteHost) {
        MfkServer.remoteHost = remoteHost;
    }

    public void setLocalHost(String localHost) {
        this.localHost = localHost;
    }
}
