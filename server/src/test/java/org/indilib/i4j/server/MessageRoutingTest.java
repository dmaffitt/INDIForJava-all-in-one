package org.indilib.i4j.server;

import org.indilib.i4j.protocol.DefTextVector;
import org.indilib.i4j.protocol.EnableBLOB;
import org.indilib.i4j.protocol.GetProperties;
import org.indilib.i4j.protocol.Message;
import org.indilib.i4j.protocol.NewNumberVector;
import org.indilib.i4j.server.TestSupport.Client;
import org.indilib.i4j.server.TestSupport.Events;
import org.indilib.i4j.server.TestSupport.FakeNetworkDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.indilib.i4j.server.TestSupport.await;
import static org.indilib.i4j.server.TestSupport.freePort;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(30)
class MessageRoutingTest {

    private static final String DRIVER_XML = "<defTextVector device=\"Fake\" name=\"P\" state=\"Idle\" perm=\"ro\"><defText name=\"t\">x</defText></defTextVector>"
            + "<message message=\"global hello\"/>"
            + "<message device=\"Fake\" message=\"device hello\"/>";

    private INDIServer server;

    private final Events events = new Events();

    @BeforeEach
    void startServer() throws Exception {
        server = new INDIServer(freePort());
        server.addEventHandler(events);
    }

    @AfterEach
    void stopServer() {
        server.stopServer();
    }

    @Test
    void driverMessagesReachTheClient() throws Exception {
        try (Client client = new Client(server.createConnection()); FakeNetworkDriver driver = new FakeNetworkDriver(DRIVER_XML)) {
            client.send(new GetProperties().setVersion("1.7"));
            server.loadNetworkDriver("localhost", driver.port());

            client.expect(DefTextVector.class, m -> "Fake".equals(m.getDevice()));
            // a message without device is global, the driver must not be disconnected for it
            client.expect(Message.class, m -> m.getDevice() == null && "global hello".equals(m.getMessage()));
            client.expect(Message.class, m -> "Fake".equals(m.getDevice()) && "device hello".equals(m.getMessage()));
            assertEquals(0, events.count("driver disconnected"));
            assertEquals(1, server.getDevices().size());
        }
    }

    @Test
    void malformedClientMessagesDoNotDisconnectTheClient() throws Exception {
        try (Client client = new Client(server.createConnection()); FakeNetworkDriver driver = new FakeNetworkDriver(DRIVER_XML)) {
            client.send(new GetProperties().setVersion("1.7"));
            client.send(new NewNumberVector()); // no device, no name
            client.send(new EnableBLOB().setDevice("Fake")); // no rule
            server.loadNetworkDriver("localhost", driver.port());

            client.expect(DefTextVector.class, m -> "Fake".equals(m.getDevice()));
            assertEquals(0, events.count("client broken"));
        }
    }

    @Test
    void closedClientsAreRemoved() throws Exception {
        Client piped = new Client(server.createConnection());
        piped.send(new GetProperties().setVersion("1.7"));
        piped.close();
        await("piped client removed", () -> events.count("client broken") == 1);

        try (Socket socket = new Socket("localhost", server.getPort())) {
            OutputStream out = socket.getOutputStream();
            out.write("<getProperties version=\"1.7\"/>".getBytes(StandardCharsets.UTF_8));
            out.flush();
            await("socket client connected", () -> events.count("client established") == 2);
        }
        await("socket client removed", () -> events.count("client broken") == 2);
    }

    @Test
    void stoppingTheServerRemovesDevicesAndNotifiesClients() throws Exception {
        try (Client client = new Client(server.createConnection()); FakeNetworkDriver driver = new FakeNetworkDriver(DRIVER_XML)) {
            client.send(new GetProperties().setVersion("1.7"));
            server.loadNetworkDriver("localhost", driver.port());
            client.expect(DefTextVector.class, m -> "Fake".equals(m.getDevice()));

            server.stopServer();
            await("device removed", () -> server.getDevices().isEmpty());
            await("client disconnected", () -> client.ended);
        }
    }
}
