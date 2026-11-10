package org.indilib.i4j.server;

import org.indilib.i4j.protocol.DefTextVector;
import org.indilib.i4j.protocol.GetProperties;
import org.indilib.i4j.protocol.api.INDIConnection;
import org.indilib.i4j.protocol.url.INDIURLStreamHandlerFactory;
import org.indilib.i4j.server.TestSupport.Client;
import org.indilib.i4j.server.TestSupport.FakeNetworkDriver;
import org.indilib.i4j.server.api.INDIServerAccess;
import org.indilib.i4j.server.api.INDIServerAccessLookup;
import org.indilib.i4j.server.api.INDIServerInterface;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.URI;

import static org.indilib.i4j.server.TestSupport.freePort;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(30)
class WebsocketTest {

    private final INDIServerAccess access = INDIServerAccessLookup.indiServerAccess();

    @AfterEach
    void stopServer() {
        access.stop();
    }

    @Test
    void clientTalksToTheServerOverWebsocket() throws Exception {
        INDIURLStreamHandlerFactory.init();
        // the acceptors work with the server from the server access
        INDIServerInterface server = access.createOrGet(null, freePort());
        int websocketPort = freePort();
        server.activateAcceptor("websocket", websocketPort);

        INDIConnection connection = (INDIConnection) URI.create("indiw://localhost:" + websocketPort + "/websocket").toURL().openConnection();
        try (Client client = new Client(connection);
             FakeNetworkDriver driver = new FakeNetworkDriver(
                     "<defTextVector device=\"Fake\" name=\"P\" state=\"Idle\" perm=\"ro\"><defText name=\"t\">over websocket</defText></defTextVector>")) {
            client.send(new GetProperties().setVersion("1.7"));
            Thread.sleep(300); // the fake driver sends its definitions once, the client must be listening
            server.loadNetworkDriver("localhost", driver.port());

            DefTextVector vector = client.expect(DefTextVector.class, m -> "Fake".equals(m.getDevice()));
            assertEquals("over websocket", vector.getElements().get(0).getTextContent().trim());
        }
    }
}
