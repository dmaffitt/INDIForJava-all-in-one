package org.indilib.i4j.server;

import org.indilib.i4j.protocol.DefTextVector;
import org.indilib.i4j.protocol.DelProperty;
import org.indilib.i4j.protocol.GetProperties;
import org.indilib.i4j.protocol.Message;
import org.indilib.i4j.server.TestSupport.Client;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.indilib.i4j.server.TestSupport.await;
import static org.indilib.i4j.server.TestSupport.freePort;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * a native driver is an external process talking INDI on stdin and stdout.
 */
@Timeout(30)
@DisabledOnOs(OS.WINDOWS)
class NativeDriverTest {

    /**
     * Logs 1 MB to stderr first (more than any pipe buffer, the driver blocks
     * if nobody reads it), defines a property, and answers the first input it
     * gets on stdin.
     */
    private static final String DRIVER_SCRIPT = """
            #!/bin/sh
            echo $$ > "$(dirname "$0")/pid"
            head -c 1048576 /dev/zero | tr '\\0' 'x' | fold -w 1000 >&2
            printf '<defTextVector device="Native" name="P" state="Idle" perm="ro"><defText name="t">x</defText></defTextVector>'
            head -c 1 > /dev/null
            printf '<message device="Native" message="got input"/>'
            cat > /dev/null
            """;

    private INDIServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = new INDIServer(freePort());
    }

    @AfterEach
    void stopServer() {
        server.stopServer();
    }

    @Test
    void talksToTheDriverAndStopsItWithTheServer(@TempDir Path dir) throws Exception {
        // a path with a space, it must not be split into arguments
        Path driver = dir.resolve("fake driver/fake_driver.sh");
        Files.createDirectories(driver.getParent());
        Files.writeString(driver, DRIVER_SCRIPT);
        assertTrue(driver.toFile().setExecutable(true));

        try (Client client = new Client(server.createConnection())) {
            client.send(new GetProperties().setVersion("1.7"));
            server.loadNativeDriver(driver.toString());

            client.expect(DefTextVector.class, m -> "Native".equals(m.getDevice()));
            client.expect(Message.class, m -> "got input".equals(m.getMessage()));

            Path pidFile = driver.resolveSibling("pid");
            long pid = Long.parseLong(Files.readString(pidFile).trim());
            assertTrue(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false), "driver running");

            server.stopServer();
            client.expect(DelProperty.class, m -> "Native".equals(m.getDevice()));
            await("driver process ended", () -> !ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
        }
    }
}
