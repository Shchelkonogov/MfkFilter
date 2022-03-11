package ru.teconD.mfkFilter;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Класс для ответа контроллеру в запрете общения
 * @author Maksim Shchelkonogov
 */
public class MfkClientIgnore extends Thread {

    private static final Logger LOGGER = Logger.getLogger(MfkClientIgnore.class.getName());

    private Socket socket;

    MfkClientIgnore(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        LOGGER.log(Level.INFO, "create thread {0} for new connection from {1}",
                new Object[] {this.getId(), socket.getInetAddress().getHostAddress()});

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
             BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream())) {
            byte[] sizeBytes = new byte[2];

            if (in.read(sizeBytes, 0, 2) != -1) {
                int size = Short.toUnsignedInt(ByteBuffer.wrap(sizeBytes).getShort());

                byte[] dataBytes = new byte[size];
                in.readFully(dataBytes);

                if (dataBytes[0] == 1) {
                    byte[] response = new byte[6];

                    response[0] = 0;
                    response[1] = 4 & 0xff;
                    response[2] = 3 & 0xff;
                    response[3] = 40 & 0xff; // 0010 1000 это версия 2
//                    response[3] = 24 & 0xff; // 0001 1000 это версия 1
                    response[4] = 1 & 0xff;
                    response[5] = 1 & 0xff; // Номер сервера в системе (для теста написал 1)

                    LOGGER.info("send failure identification message");

                    out.write(response, 0, response.length);
                    out.flush();
                } else {
                    LOGGER.log(Level.WARNING, "unknown message {0}", dataBytes[0]);
                }
            } else {
                LOGGER.warning("can't read head of message");
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "error work with client", e);
        }
    }
}
