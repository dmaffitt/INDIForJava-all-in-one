package org.indilib.i4j.server;

import org.indilib.i4j.protocol.DefBlobVector;
import org.indilib.i4j.protocol.DefNumberVector;
import org.indilib.i4j.protocol.DefSwitchVector;
import org.indilib.i4j.protocol.DefTextVector;
import org.indilib.i4j.protocol.EnableBLOB;
import org.indilib.i4j.protocol.GetProperties;
import org.indilib.i4j.protocol.NewNumberVector;
import org.indilib.i4j.protocol.NewSwitchVector;
import org.indilib.i4j.protocol.OneBlob;
import org.indilib.i4j.protocol.OneNumber;
import org.indilib.i4j.protocol.OneSwitch;
import org.indilib.i4j.protocol.SetBlobVector;
import org.indilib.i4j.protocol.SetSwitchVector;
import org.indilib.i4j.protocol.io.INDISocketConnection;
import org.indilib.i4j.server.TestSupport.Client;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import static org.indilib.i4j.server.TestSupport.await;
import static org.indilib.i4j.server.TestSupport.canConnect;
import static org.indilib.i4j.server.TestSupport.freePort;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Compatibility with the C INDI implementation. Runs when indiserver and the
 * simulator drivers are found: in the directory INDI_BIN_DIR, on the PATH, or
 * in a KStars installation on macOS. Skipped otherwise.
 */
@Timeout(120)
class IndiserverInteropTest {

    private static final String TELESCOPE = "Telescope Simulator";

    private static final String CCD = "CCD Simulator";

    private static Optional<Path> indiBinary(String name) {
        Stream<String> directories = Stream.concat(
                Stream.of(System.getenv("INDI_BIN_DIR"), "/Applications/kstars.app/Contents/MacOS"),
                Stream.of(System.getenv().getOrDefault("PATH", "").split(File.pathSeparator)));
        return directories.filter(d -> d != null && !d.isEmpty())
                .map(d -> Path.of(d, name))
                .filter(Files::isExecutable)
                .findFirst();
    }

    private static Path requireBinary(String name) {
        Optional<Path> binary = indiBinary(name);
        assumeTrue(binary.isPresent(), name + " not found, skipping the compatibility test");
        return binary.get();
    }

    @Test
    void javaClientTalksToIndiserver() throws Exception {
        Path indiserver = requireBinary("indiserver");
        Path telescope = requireBinary("indi_simulator_telescope");
        Path ccd = requireBinary("indi_simulator_ccd");
        int port = freePort();
        Process process = new ProcessBuilder(indiserver.toString(), "-p", Integer.toString(port), telescope.toString(), ccd.toString())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        try {
            await("indiserver listening", () -> canConnect(port));
            try (Client client = new Client(new INDISocketConnection("localhost", port))) {
                client.send(new GetProperties().setVersion("1.7"));
                // before they are connected, drivers define only a few properties
                DefSwitchVector connection = client.expect(DefSwitchVector.class,
                        m -> TELESCOPE.equals(m.getDevice()) && "CONNECTION".equals(m.getName()));
                assertEquals("OneOfMany", connection.getRule());
                assertEquals(2, connection.getElements().size());
                client.expect(DefSwitchVector.class, m -> CCD.equals(m.getDevice()) && "CONNECTION".equals(m.getName()));

                // connect the ccd, which defines its other properties
                client.send(new NewSwitchVector().setDevice(CCD).setName("CONNECTION")
                        .addElement(new OneSwitch().setName("CONNECT").setTextContent("On"))
                        .addElement(new OneSwitch().setName("DISCONNECT").setTextContent("Off")));
                client.expect(SetSwitchVector.class, m -> CCD.equals(m.getDevice()) && "CONNECTION".equals(m.getName()) && "Ok".equals(m.getState()));
                client.expect(DefBlobVector.class, m -> CCD.equals(m.getDevice()) && "CCD1".equals(m.getName()));

                // take an exposure, the image comes back as a base64 blob
                client.send(new EnableBLOB().setDevice(CCD).setTextContent("Also"));
                client.send(new NewNumberVector().setDevice(CCD).setName("CCD_EXPOSURE")
                        .addElement(new OneNumber().setName("CCD_EXPOSURE_VALUE").setTextContent("0.1")));
                SetBlobVector image = client.expect(SetBlobVector.class, m -> CCD.equals(m.getDevice()) && "CCD1".equals(m.getName()));
                OneBlob blob = assertInstanceOf(OneBlob.class, image.getElements().get(0));
                assertTrue(blob.getByteContent().length > 1000, "image bytes " + blob.getByteContent().length);
                if (".fits".equals(blob.getFormat())) {
                    assertTrue(new String(blob.getByteContent(), 0, 6, StandardCharsets.US_ASCII).startsWith("SIMPLE"), "fits header");
                }
            }
        } finally {
            process.destroy();
        }
    }

    @Test
    void indiDriverRunsUnderTheJavaServer() throws Exception {
        Path telescope = requireBinary("indi_simulator_telescope");
        INDIServer server = new INDIServer(freePort());
        try (Client client = new Client(server.createConnection())) {
            client.send(new GetProperties().setVersion("1.7"));
            server.loadNativeDriver(telescope.toString());
            client.expect(DefTextVector.class, m -> TELESCOPE.equals(m.getDevice()) && "DRIVER_INFO".equals(m.getName()));

            // the C driver must understand the xml the java server writes
            client.send(new NewSwitchVector().setDevice(TELESCOPE).setName("CONNECTION")
                    .addElement(new OneSwitch().setName("CONNECT").setTextContent("On"))
                    .addElement(new OneSwitch().setName("DISCONNECT").setTextContent("Off")));
            client.expect(SetSwitchVector.class, m -> TELESCOPE.equals(m.getDevice()) && "CONNECTION".equals(m.getName()) && "Ok".equals(m.getState()));
            client.expect(DefNumberVector.class, m -> TELESCOPE.equals(m.getDevice()) && "EQUATORIAL_EOD_COORD".equals(m.getName()));
        } finally {
            server.stopServer();
        }
    }
}
