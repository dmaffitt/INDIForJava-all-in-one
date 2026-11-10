package org.indilib.i4j.protocol.io;

import org.indilib.i4j.protocol.GetProperties;
import org.indilib.i4j.protocol.INDIProtocol;
import org.indilib.i4j.protocol.api.INDIConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(10)
class INDIPipedConnectionsTest {

    @Test
    void passesMessagesBothWays() throws IOException {
        INDIPipedConnections pipe = new INDIPipedConnections();
        pipe.first().getINDIOutputStream().writeObject(new GetProperties().setVersion("1.7"));
        GetProperties received = assertInstanceOf(GetProperties.class, pipe.second().getINDIInputStream().readObject());
        assertEquals("1.7", received.getVersion());
    }

    @Test
    void closingOneEndEndsTheOtherEndsReader() throws Exception {
        INDIPipedConnections pipe = new INDIPipedConnections();
        CompletableFuture<INDIProtocol<?>> read = CompletableFuture.supplyAsync(() -> readQuietly(pipe.second()));
        Thread.sleep(200); // let the reader block
        pipe.first().close();
        assertNull(read.get(5, TimeUnit.SECONDS));
    }

    @Test
    void closingWakesUpItsOwnReader() throws Exception {
        INDIPipedConnections pipe = new INDIPipedConnections();
        CompletableFuture<INDIProtocol<?>> read = CompletableFuture.supplyAsync(() -> readQuietly(pipe.first()));
        Thread.sleep(200);
        pipe.first().close();
        assertNull(read.get(5, TimeUnit.SECONDS));
    }

    @Test
    void writingToAClosedConnectionFails() throws IOException {
        INDIPipedConnections pipe = new INDIPipedConnections();
        pipe.first().close();
        assertThrows(IOException.class, () -> pipe.first().getINDIOutputStream().writeObject(new GetProperties()));
    }

    private static INDIProtocol<?> readQuietly(INDIConnection connection) {
        try {
            return connection.getINDIInputStream().readObject();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
