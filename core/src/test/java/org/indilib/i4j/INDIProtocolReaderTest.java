package org.indilib.i4j;

import org.indilib.i4j.protocol.GetProperties;
import org.indilib.i4j.protocol.INDIProtocol;
import org.indilib.i4j.protocol.api.INDIConnection;
import org.indilib.i4j.protocol.api.INDIInputStream;
import org.indilib.i4j.protocol.io.INDIPipedConnections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class INDIProtocolReaderTest {

    /**
     * collects the messages and counts the finishReader calls.
     */
    static final class RecordingParser implements INDIProtocolParser {

        final List<INDIProtocol<?>> messages = new CopyOnWriteArrayList<>();

        final AtomicInteger finished = new AtomicInteger();

        final CountDownLatch finishedLatch = new CountDownLatch(1);

        private final INDIConnection connection;

        RecordingParser(INDIConnection connection) {
            this.connection = connection;
        }

        @Override
        public void processProtocolMessage(INDIProtocol<?> message) {
            messages.add(message);
        }

        @Override
        public INDIInputStream getInputStream() {
            try {
                return connection.getINDIInputStream();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void finishReader() {
            finished.incrementAndGet();
            finishedLatch.countDown();
        }
    }

    @Test
    void finishesOnceWhenTheOtherSideCloses() throws Exception {
        INDIPipedConnections pipe = new INDIPipedConnections();
        RecordingParser parser = new RecordingParser(pipe.second());
        new INDIProtocolReader(parser, "test reader").start();

        pipe.first().getINDIOutputStream().writeObject(new GetProperties().setVersion("1.7"));
        pipe.first().close();

        assertTrue(parser.finishedLatch.await(5, TimeUnit.SECONDS), "finishReader was not called");
        Thread.sleep(200);
        assertEquals(1, parser.finished.get());
        assertEquals(1, parser.messages.size());
    }

    @Test
    void finishesWhenStopped() throws Exception {
        INDIPipedConnections pipe = new INDIPipedConnections();
        RecordingParser parser = new RecordingParser(pipe.second());
        INDIProtocolReader reader = new INDIProtocolReader(parser, "test reader");
        reader.start();

        reader.setStop(true);
        // the reader notices the stop after its next message
        pipe.first().getINDIOutputStream().writeObject(new GetProperties().setVersion("1.7"));

        assertTrue(parser.finishedLatch.await(5, TimeUnit.SECONDS), "finishReader was not called");
        assertEquals(1, parser.finished.get());
    }
}
