package ru.teconD.mfkFilter;

import ru.teconD.mfkFilter.model.RemoveIndex;

import java.io.*;
import java.lang.reflect.Array;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Класс клиента mfk запускает отдельный поток
 * @author Maksim Shchelkonogov
 */
final class MfkClient extends Thread {

    private static final Logger LOGGER = Logger.getLogger(MfkClient.class.getName());

    private Set<String> cache = new HashSet<>();

    private Socket mfkSocket;

    private byte protocolVersion = 2;
    private int messagesCount = 0;

    MfkClient(Socket mfkSocket) {
        this.mfkSocket = mfkSocket;
    }

    @Override
    public void run() {
        LOGGER.log(Level.INFO, "create thread {0} for new connection from {1}",
                new Object[] {this.getId(), mfkSocket.getInetAddress().getHostAddress()});

        try (DataInputStream mfkIn = new DataInputStream(new BufferedInputStream(mfkSocket.getInputStream()));
             BufferedOutputStream mfkOut = new BufferedOutputStream(mfkSocket.getOutputStream());
             Socket driverSocket = new Socket(MfkServer.remoteHost, 20100);
             DataInputStream driverIn = new DataInputStream(new BufferedInputStream(driverSocket.getInputStream()));
             BufferedOutputStream driverOut = new BufferedOutputStream(driverSocket.getOutputStream())) {
            driverSocket.setSoTimeout(10000);

            while (true) {
                try {
                    LOGGER.info("wait data from mfk");
                    transferDataWithFilter(mfkIn, driverOut, bytes -> {
                        switch (bytes[2]) {
                            case 1:
                                // Определяем версию протокола
                                String version = Integer.toBinaryString(bytes[3] & 0xff);

                                switch (version) {
                                    case "100000":
                                        protocolVersion = 2;
                                        break;
                                    case "10000":
                                    default:
                                        protocolVersion = 1;
                                }

                                // Фиксируем номер контроллера в системе, что бы слушать оба контроллера,
                                // иначе из-за разных номеров слетает линковка объекта
                                bytes[5] = 1;
                                break;
                            case 3:
                                LOGGER.info("data package");
                                // Получаем список, для удаления
                                List<RemoveIndex> removeIndices = new ArrayList<>();
                                switch (protocolVersion) {
                                    case 1:
                                        messagesCount = findNotAllowData(bytes, removeIndices, 4, protocolVersion);
                                        break;
                                    case 2:
                                        int markSize = ByteBuffer.wrap(bytes, 5, 1).get();
                                        messagesCount = findNotAllowData(bytes, removeIndices, 6 + markSize, protocolVersion);
                                        break;
                                }

                                if (!removeIndices.isEmpty()) {
                                    // Сортируем в обратном порядке, т.к удаляем с конца
                                    removeIndices.sort(Collections.reverseOrder());

                                    // Удаляем и уменьшаем количество сообщений
                                    int newMessageCount = messagesCount;
                                    for (RemoveIndex index: removeIndices) {
                                        bytes = remove(bytes, index.getStartIndex(), index.getStartIndex() + index.getOffset());
                                        newMessageCount--;
                                    }

                                    LOGGER.log(Level.INFO, "message count before filtering {0} and after filtering {1}", new Object[] {messagesCount, newMessageCount});

                                    if (newMessageCount == 0) {
                                        throw new FilterException("no message to send");
                                    }

                                    // Устанавливаем новый размер пакета
                                    byte[] packageBuffer = ByteBuffer.allocate(4).putInt(bytes.length - 2).array();
                                    bytes[0] = packageBuffer[2];
                                    bytes[1] = packageBuffer[3];

                                    // Устанавливаем новое количество сообщений
                                    byte[] newMessageCountAsBytes = ByteBuffer.allocate(2).putShort((short) newMessageCount).array();
                                    switch (protocolVersion) {
                                        case 1:
                                            bytes[3] = newMessageCountAsBytes[1];
                                            break;
                                        case 2:
                                            bytes[3] = newMessageCountAsBytes[0];
                                            bytes[4] = newMessageCountAsBytes[1];
                                            break;
                                    }
                                } else {
                                    LOGGER.log(Level.INFO, "data filtering is not required. Message count {0}", messagesCount);
                                }
                                break;
                        }
                        return bytes;
                    });

                    LOGGER.info("wait data from driver");
                    transferDataWithFilter(driverIn, mfkOut, bytes -> {
                        ByteBuffer buffer = ByteBuffer.wrap(bytes);

                        // Устанавливаем количество сообщений, которое было до удаления лишних
                        if ((bytes.length > 3) && (buffer.get(2) == 4)) {
                            byte[] messagesCountBuffer = ByteBuffer.allocate(2).putShort((short) messagesCount).array();

                            switch (buffer.getShort(0)) {
                                case 2:
//                                    System.out.printf("message count %s\n", buffer.get(3));
                                    bytes[3] = messagesCountBuffer[1];
                                    break;
                                case 3:
//                                    System.out.printf("message count %s\n", buffer.getShort(3));
                                    bytes[3] = messagesCountBuffer[0];
                                    bytes[4] = messagesCountBuffer[1];
                                    break;
                            }
                        }
                        return bytes;
                    });
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "error with filtering data", e);
                    return;
                } catch (FilterException e) {
                    if (e.getMessage().equals("no message to send")) {
                        int messageConfirmSize;
                        switch (protocolVersion) {
                            case 1:
                                messageConfirmSize = 2;
                                break;
                            case 2:
                            default:
                                messageConfirmSize = 3;
                        }

                        byte[] response = new byte[messageConfirmSize + 2];
                        response[0] = 0;
                        response[1] = (byte) messageConfirmSize;
                        response[2] = (byte) 4;

                        byte[] messageCountArray = ByteBuffer.allocate(4).putInt(messagesCount).array();

                        switch (messageConfirmSize) {
                            case 3:
                                response[3] = messageCountArray[2];
                                response[4] = messageCountArray[3];
                                break;
                            case 2:
                            default:
                                response[3] = messageCountArray[3];
                        }

                        LOGGER.info("send load message ok");

                        mfkOut.write(response);
                        mfkOut.flush();
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "error create driver sockets", e);
        } finally {
            MfkServer.block = false;
        }
    }

    /**
     * Метод перенаправляет данные с применением филтра этих данных
     * @param in входной поток
     * @param out выходной поток
     * @param filter фильтр
     * @throws IOException в случае ошибки работы с потоками
     */
    private void transferDataWithFilter(DataInputStream in, OutputStream out, Filter<byte[]> filter) throws IOException, FilterException {
        byte[] sizeBytes = new byte[2];

        if (in.read(sizeBytes, 0, 2) != -1) {
            int size = Short.toUnsignedInt(ByteBuffer.wrap(sizeBytes).getShort());

//            System.out.printf("size %s message %s\n", size, Arrays.toString(sizeBytes));

            byte[] dataBytes = new byte[size];
            in.readFully(dataBytes);

//            if (size > 50) {
//                System.out.printf("big data size %s\n", size);
//            } else {
//                System.out.printf("data %s\n", Arrays.toString(dataBytes));
//            }

            // слепляем данные заголовка и основного тела
            byte[] instance = (byte[]) Array.newInstance(sizeBytes.getClass().getComponentType(), sizeBytes.length + dataBytes.length);
            System.arraycopy(sizeBytes, 0, instance, 0, sizeBytes.length);
            System.arraycopy(dataBytes, 0, instance, sizeBytes.length, dataBytes.length);

//            if (size > 50) {
//                System.out.printf("all big data size %s\n", instance.length);
//            } else {
//                System.out.printf("all data %s\n", Arrays.toString(instance));
//            }

            // Применяем фильтра
            instance = filter.filter(instance);

            // Отправка отфильтрованных данных
            out.write(instance, 0, instance.length);
            out.flush();
        } else {
            throw new IOException("can't read head of package");
        }
    }

    /**
     * Разбор входного трафика PushEvent и формирование списка {@link RemoveIndex} с данными для удаления
     * @param data входные данные
     * @param remove выходные данные
     * @param startIndex начальное смещение
     * @param protocolVersion версия протокола PushEvent
     * @return количество сообщений
     */
    private int findNotAllowData(byte[] data, List<RemoveIndex> remove, int startIndex, int protocolVersion) {
        // increment используется в 2 версии протокола, в ней добовляется 1 байт под статуст контроллера
        int increment;
        int messagesCount;
        switch (protocolVersion) {
            case 2:
                messagesCount = Short.toUnsignedInt(ByteBuffer.wrap(data, 3, 2).getShort());
                increment = 1;
                break;
            case 1:
            default:
                messagesCount = Byte.toUnsignedInt(ByteBuffer.wrap(data, 3, 1).get());
                increment = 0;
        }

        int add;
        int repeatCount = 0;
        try {
            for (int i = startIndex; i < data.length; i += add) {
                if (repeatCount == messagesCount) {
                    break;
                }

                int bufferNumber = data[i + 8 + increment] & 0xff;
                int eventCode = ((data[i + 9 + increment] & 0xff) << 24) | ((data[i + 10 + increment] & 0xff) << 16) |
                        ((data[i + 11 + increment] & 0xff) << 8) | (data[i + 12 + increment] & 0xff);
                int additionalDataSize = (data[i + 13 + increment] & 0xff);

                // размер заголовка
                add = 4 +   // секунды
                        4 +  // наносекунды
                        increment + // статус контроллера (есть только в 2 версии протокола)
                        1 +  // номер буфера
                        4 +  // код события
                        1; // количество дополнительных данных

                int additionalDataStartIndex = i + add;

                short dataCount = 0;
                int addIndex = 0;
                for (int j = 0; j < additionalDataSize; j++) {
                    int type = (data[additionalDataStartIndex + addIndex] & 0xff);
                    int count = (data[additionalDataStartIndex + 1 + addIndex] & 0xff);

                    add += 2; // заголовок дополнительных данных
                    addIndex += 2;
                    dataCount += count;

                    switch (type) {
                        case 250:
                        case 252:
                        case 253: {
                            add += 4 * count;
                            addIndex += 4 * count;
                            break;
                        }
                        case 251: {
                            add += 8 * count;
                            addIndex += 8 * count;
                            break;
                        }
                        case 255: {
                            // В типе 255 (bool) длина округляется до ближайшего большего или равного числа кратного 8
                            // count показывает сколько значений bool будет, а надо высчитать сколько это байт.
                            count = (int) Math.ceil(count / 8d);
                            add += count;
                            addIndex += count;
                            break;
                        }
                        case 64:
                        case 249:
                        case 254:
                        default: {
                            add += count;
                            addIndex += count;
                        }
                    }
                }

//                LOGGER.log(Level.INFO, "bufferNumber {0} eventCode {1} size {2} startIndex {3} endIndex {4}",  new Object[]{bufferNumber, eventCode, dataCount, i, i + add});
                cache.add(bufferNumber + "/" + eventCode + "/" + dataCount);

                if (!MfkServer.permitData.contains(bufferNumber + "/" + eventCode + "/" + dataCount)) {
                    remove.add(new RemoveIndex(i, add));
                }

                repeatCount++;
            }
        } catch (IndexOutOfBoundsException e) {
            LOGGER.log(Level.WARNING, "Pars data error:", e);
        }

        LOGGER.log(Level.INFO, "cache group {0}", cache);

        return repeatCount;
    }

    /**
     * Удаление из массива элементы
     * @param original входной массив
     * @param removeStart индекс с которого удалять
     * @param removeEnd индекс по который удалять
     * @param <T> тип данных
     * @return новый массив
     */
    private <T> T remove(T original, int removeStart, int removeEnd) {
        if (!original.getClass().isArray()) {
            throw new IllegalArgumentException("Only arrays are accepted.");
        }

        int originalLen = Array.getLength(original);

        @SuppressWarnings("unchecked")
        T instance = (T) Array.newInstance(original.getClass().getComponentType(), originalLen - (removeEnd - removeStart));

        System.arraycopy(original, 0, // from original[0]
                instance, 0,                     // to instance[0]
                removeStart);             // this many elements
        System.arraycopy(original, removeEnd, // from original[removeEnd]
                instance, removeStart,                   // to instance[removeStart]
                originalLen - removeEnd);         // this many elements

        return instance;
    }
}
