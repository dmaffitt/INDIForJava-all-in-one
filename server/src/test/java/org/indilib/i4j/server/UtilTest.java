package org.indilib.i4j.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UtilTest {

    @Test
    void followsManifestClassPathEntries(@TempDir Path dir) throws IOException {
        // like "java -jar app.jar", where app.jar lists its dependencies in the manifest
        Path lib = Files.createDirectories(dir.resolve("lib"));
        Path classes = Files.createDirectories(dir.resolve("my classes"));
        Path nested = lib.resolve("nested.jar");
        writeJar(nested, null);
        Path dependency = lib.resolve("dependency.jar");
        writeJar(dependency, "nested.jar");
        Path app = dir.resolve("app.jar");
        writeJar(app, "lib/dependency.jar my%20classes/ lib/dependency.jar");

        assertEquals(List.of(app.toFile(), dependency.toFile(), nested.toFile(), classes.toFile()),
                List.copyOf(Util.classPathEntries(app.toString())));
    }

    @Test
    void keepsPlainClassPathEntries(@TempDir Path dir) throws IOException {
        Path first = Files.createDirectories(dir.resolve("a"));
        Path second = Files.createDirectories(dir.resolve("b"));
        assertEquals(List.of(first.toFile(), second.toFile()),
                List.copyOf(Util.classPathEntries(first + File.pathSeparator + second)));
    }

    private static void writeJar(Path jar, String classPath) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (classPath != null) {
            manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, classPath);
        }
        try (JarOutputStream ignored = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            // just the manifest
        }
    }
}
