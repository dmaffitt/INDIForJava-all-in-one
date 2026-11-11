package org.indilib.i4j.server;

import org.indilib.i4j.INDIException;
import org.indilib.i4j.driver.INDIDriver;
import org.indilib.i4j.protocol.api.INDIConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.indilib.i4j.server.TestSupport.await;
import static org.indilib.i4j.server.TestSupport.freePort;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(60)
class JavaDriverTest {

    public static class Foo extends INDIDriver {
        public Foo(INDIConnection connection) {
            super(connection);
        }

        @Override
        public String getName() {
            return "Foo";
        }
    }

    public static class FooBar extends INDIDriver {
        public FooBar(INDIConnection connection) {
            super(connection);
        }

        @Override
        public String getName() {
            return "FooBar";
        }
    }

    private INDIServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = new INDIServer(freePort());
    }

    @AfterEach
    void stopServer() {
        server.stopServer();
    }

    private List<String> deviceIdentifiers() {
        return server.getDevices().stream().map(d -> d.getDeviceIdentifier()).collect(Collectors.toList());
    }

    @Test
    void destroyingADriverLeavesDriversWithTheSamePrefix() throws Exception {
        server.loadJavaDriver(Foo.class);
        server.loadJavaDriver(FooBar.class);
        assertFalse(server.isAlreadyLoaded("class+-+" + Foo.class.getName().substring(0, Foo.class.getName().length() - 1)));

        server.destroyJavaDriver(Foo.class);
        await("Foo removed", () -> server.getDevices().size() == 1);
        assertTrue(deviceIdentifiers().get(0).contains(FooBar.class.getName()));
    }

    @Test
    void loadingADriverTwiceIsRefused() throws Exception {
        server.loadJavaDriver(Foo.class);
        assertThrows(INDIException.class, () -> server.loadJavaDriver(Foo.class));
        assertEquals(1, server.getDevices().size());
    }

    @Test
    void findsDriversOnTheClasspath() {
        assertTrue(server.getAvailableDevices().contains(Foo.class.getName()), server.getAvailableDevices().toString());
    }

    @Test
    void loadsDriversFromAJarThatIsNotOnTheClasspath(@TempDir Path dir) throws Exception {
        Path jar = compileDriverJar(dir);
        assertThrows(ClassNotFoundException.class, () -> Class.forName("jartest.JarDriver", false, getClass().getClassLoader()));

        server.loadJavaDriversFromJAR(jar.toString());

        assertEquals(1, server.getDevices().size());
        assertTrue(deviceIdentifiers().get(0).contains("jartest.JarDriver"));
        assertTrue(server.getAvailableDevices().contains("jartest.JarDriver"));
        // drivers look up services with the context class loader, it must see the driver's jar
        Class<?> driverClass = Class.forName("jartest.JarDriver", false, Util.driverClassLoader());
        assertSame(driverClass.getClassLoader(), driverClass.getField("contextLoader").get(null));

        // loading the same jar again skips the loaded driver
        server.loadJavaDriversFromJAR(jar.toString());
        assertEquals(1, server.getDevices().size());
    }

    private static Path compileDriverJar(Path dir) throws IOException, URISyntaxException {
        Path source = dir.resolve("src/jartest/JarDriver.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package jartest;

                public class JarDriver extends org.indilib.i4j.driver.INDIDriver {
                    public static volatile ClassLoader contextLoader;

                    public JarDriver(org.indilib.i4j.protocol.api.INDIConnection connection) {
                        super(connection);
                        contextLoader = Thread.currentThread().getContextClassLoader();
                    }

                    @Override
                    public String getName() {
                        return "JarDriver";
                    }
                }
                """);
        Path classes = Files.createDirectories(dir.resolve("classes"));
        String classPath = String.join(File.pathSeparator, location(INDIDriver.class), location(INDIConnection.class), location(Logger.class));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertEquals(0, compiler.run(null, null, null, "-d", classes.toString(), "-cp", classPath, source.toString()), "compile");

        Path jar = dir.resolve("drivers/jar-driver.jar");
        Files.createDirectories(jar.getParent());
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar)); Stream<Path> files = Files.walk(classes)) {
            for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                out.putNextEntry(new JarEntry(classes.relativize(file).toString().replace(File.separatorChar, '/')));
                Files.copy(file, out);
                out.closeEntry();
            }
        }
        return jar;
    }

    private static String location(Class<?> type) throws URISyntaxException {
        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
    }
}
