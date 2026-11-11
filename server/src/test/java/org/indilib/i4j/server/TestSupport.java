package org.indilib.i4j.server;

import org.indilib.i4j.protocol.INDIProtocol;
import org.indilib.i4j.protocol.api.INDIConnection;
import org.indilib.i4j.protocol.api.INDIInputStream;
import org.indilib.i4j.server.api.INDIClientInterface;
import org.indilib.i4j.server.api.INDIDeviceInterface;
import org.indilib.i4j.server.api.INDIServerEventHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * helpers for the server tests.
 */
final class TestSupport {

    static final long TIMEOUT_MILLIS = 5000;

    private TestSupport() {
    }

    static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    static void await(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("timed out waiting for: " + what);
            }
            Thread.sleep(20);
        }
    }

    static boolean canConnect(int port) {
        try (Socket ignored = new Socket("localhost", port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * records the server events.
     */
    static final class Events implements INDIServerEventHandler {

        final List<String> events = new CopyOnWriteArrayList<>();

        @Override
        public boolean acceptClient(INDIConnection clientSocket) {
            return true;
        }

        @Override
        public void connectionWithClientBroken(INDIClientInterface client) {
            events.add("client broken");
        }

        @Override
        public void connectionWithClientEstablished(INDIClientInterface client) {
            events.add("client established");
        }

        @Override
        public void driverDisconnected(INDIDeviceInterface device) {
            events.add("driver disconnected " + device.getDeviceIdentifier());
        }

        long count(String event) {
            return events.stream().filter(e -> e.startsWith(event)).count();
        }
    }

    /**
     * a client that collects everything it receives.
     */
    static final class Client implements AutoCloseable {

        final INDIConnection connection;

        /**
         * everything received, in order; expect() searches all of it, so
         * messages arriving in another order than expected are not lost.
         */
        final List<INDIProtocol<?>> received = new CopyOnWriteArrayList<>();

        volatile boolean ended;

        Client(INDIConnection connection) {
            this.connection = connection;
            Thread reader = new Thread(() -> {
                try {
                    INDIInputStream in = connection.getINDIInputStream();
                    for (INDIProtocol<?> message = in.readObject(); message != null; message = in.readObject()) {
                        received.add(message);
                    }
                } catch (IOException e) {
                    // connection closed
                } finally {
                    ended = true;
                }
            }, "test client reader");
            reader.setDaemon(true);
            reader.start();
        }

        Client send(INDIProtocol<?> message) throws IOException {
            connection.getINDIOutputStream().writeObject(message);
            return this;
        }

        <T extends INDIProtocol<?>> T expect(Class<T> type, Predicate<T> condition) throws InterruptedException {
            long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
            while (System.currentTimeMillis() < deadline) {
                for (INDIProtocol<?> message : received) {
                    if (type.isInstance(message) && condition.test(type.cast(message))) {
                        return type.cast(message);
                    }
                }
                Thread.sleep(20);
            }
            return fail("client did not receive the expected " + type.getSimpleName() + ", received " + received.size() + " messages");
        }

        @Override
        public void close() throws IOException {
            connection.close();
        }
    }

    /**
     * a network driver (another INDI server) that sends fixed xml to the
     * server as soon as it connects.
     */
    static final class FakeNetworkDriver implements AutoCloseable {

        private final ServerSocket serverSocket;

        private volatile Socket socket;

        FakeNetworkDriver(String xml) throws IOException {
            serverSocket = new ServerSocket(0);
            Thread thread = new Thread(() -> {
                try {
                    socket = serverSocket.accept();
                    OutputStream out = socket.getOutputStream();
                    out.write(xml.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                } catch (IOException e) {
                    // closed
                }
            }, "fake network driver");
            thread.setDaemon(true);
            thread.start();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        @Override
        public void close() throws IOException {
            if (socket != null) {
                socket.close();
            }
            serverSocket.close();
        }
    }
}
