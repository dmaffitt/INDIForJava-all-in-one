package org.indilib.i4j.server;

import org.indilib.i4j.server.api.INDIServerAccess;
import org.indilib.i4j.server.api.INDIServerAccessLookup;
import org.indilib.i4j.server.api.INDIServerInterface;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.indilib.i4j.server.TestSupport.canConnect;
import static org.indilib.i4j.server.TestSupport.freePort;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(30)
class ServerLifecycleTest {

    private final INDIServerAccess access = INDIServerAccessLookup.indiServerAccess();

    @AfterEach
    void stopServer() {
        access.stop();
    }

    @Test
    void serverAccessIsFoundThroughServiceLoader() {
        assertEquals(INDIServerAccessImpl.class, access.getClass());
    }

    @Test
    void isListeningAsSoonAsItIsCreated() throws Exception {
        int port = freePort();
        INDIServerInterface server = access.createOrGet(null, port);
        assertTrue(server.isServerRunning());
        assertEquals(port, server.getPort());
        assertTrue(canConnect(port));

        access.stop();
        assertFalse(server.isServerRunning());
        assertFalse(access.isRunning());
        assertFalse(canConnect(port));
    }

    @Test
    void concurrentCallersShareOneServer() throws Exception {
        int port = freePort();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<INDIServerInterface>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> access.createOrGet(null, port)));
            }
            Set<INDIServerInterface> servers = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Future<INDIServerInterface> future : futures) {
                servers.add(future.get());
            }
            assertEquals(1, servers.size());
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void restartReturnsANewRunningServer() throws Exception {
        int port = freePort();
        INDIServerInterface first = access.createOrGet(null, port);
        INDIServerInterface second = access.restart(null, port);
        assertNotSame(first, second);
        assertFalse(first.isServerRunning());
        assertTrue(second.isServerRunning());
        assertTrue(canConnect(port));
    }

    @Test
    void stopClosesTheAdditionalAcceptors() throws Exception {
        INDIServerInterface server = access.createOrGet(null, freePort());
        int zipPort = freePort();
        int websocketPort = freePort();
        server.activateAcceptor("zip", zipPort);
        server.activateAcceptor("websocket", websocketPort);
        assertTrue(canConnect(zipPort), "zip acceptor");
        assertTrue(canConnect(websocketPort), "websocket acceptor");

        access.stop();
        assertFalse(canConnect(zipPort), "zip acceptor after stop");
        assertFalse(canConnect(websocketPort), "websocket acceptor after stop");
    }

    @Test
    void unknownAcceptorIsIgnored() throws Exception {
        INDIServerInterface server = access.createOrGet(null, freePort());
        assertDoesNotThrow(() -> server.activateAcceptor("no such acceptor"));
    }
}
