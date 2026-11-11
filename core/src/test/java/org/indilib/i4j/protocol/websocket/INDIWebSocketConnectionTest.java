package org.indilib.i4j.protocol.websocket;

import jakarta.websocket.MessageHandler;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import org.indilib.i4j.protocol.GetProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Timeout(10)
class INDIWebSocketConnectionTest {

    /**
     * a session that keeps the binary message handler, counts the closes and
     * discards what is sent.
     */
    static final class FakeSession {

        final AtomicReference<MessageHandler.Partial<byte[]>> handler = new AtomicReference<>();

        final AtomicInteger closes = new AtomicInteger();

        private final RemoteEndpoint.Basic remote = (RemoteEndpoint.Basic) Proxy.newProxyInstance(RemoteEndpoint.Basic.class.getClassLoader(),
                new Class<?>[]{RemoteEndpoint.Basic.class}, (proxy, method, args) -> null);

        @SuppressWarnings("unchecked")
        final Session session = (Session) Proxy.newProxyInstance(Session.class.getClassLoader(), new Class<?>[]{Session.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "addMessageHandler":
                    handler.set((MessageHandler.Partial<byte[]>) args[1]);
                    return null;
                case "getBasicRemote":
                    return remote;
                case "close":
                    closes.incrementAndGet();
                    return null;
                default:
                    return null;
            }
        });

        void receive(String xml) {
            handler.get().onMessage(xml.getBytes(StandardCharsets.UTF_8), true);
        }
    }

    @Test
    void passesReceivedMessagesToTheReader() throws Exception {
        FakeSession fake = new FakeSession();
        INDIWebSocketConnection connection = new INDIWebSocketConnection(fake.session);

        fake.receive("<getProperties version=\"1.7\"/>");

        GetProperties received = assertInstanceOf(GetProperties.class, connection.getINDIInputStream().readObject());
        assertEquals("1.7", received.getVersion());
    }

    @Test
    void dropsMessagesThatArriveAfterClose() throws Exception {
        FakeSession fake = new FakeSession();
        INDIWebSocketConnection connection = new INDIWebSocketConnection(fake.session);
        connection.close();
        assertEquals(1, fake.closes.get());

        // a message the other side sent before it learned of the close
        fake.receive("<getProperties version=\"1.7\"/>");

        assertEquals(1, fake.closes.get(), "a late message must not close the session again");
    }
}
