package org.indilib.i4j.server;

/*
 * #%L
 * INDI for Java Server Library
 * %%
 * Copyright (C) 2012 - 2014 indiforjava
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Utility class for classpath and url funktions.
 *
 * @author Richard van Nieuwenhoven
 */
public final class Util {

    /**
     * Logger to log to.
     */
    private static final Logger LOG = LoggerFactory.getLogger(Util.class);
    /**
     * the scheme for urls to files.
     */
    private static final String SCHEME_FILE = "file";
    /**
     * the class loader for driver jars and directories added at runtime. Since
     * java 9 the system class loader is no URLClassLoader any more, so it can
     * not be extended.
     */
    private static final DriverClassLoader DRIVER_CLASS_LOADER = new DriverClassLoader(Util.class.getClassLoader());
    /**
     * the jandex indexes of the jars and directories added at runtime.
     */
    private static final List<IndexView> EXTENSION_INDEXES = new ArrayList<>();
    /**
     * the classpath jandex index.
     */
    private static IndexView classPathIndex;

    /**
     * private accessor for utility class.
     */
    private Util() {
    }

    /**
     * Returns whether the given URI refers to a local file system URI.
     *
     * @param uri The URI to check
     * @return <code>true</code> if the URI is a local file system location, and
     * <code>false</code> otherwise
     */
    public static boolean isFileURI(URI uri) {
        return SCHEME_FILE.equalsIgnoreCase(uri.getScheme());
    }

    /**
     * Returns the URI as a local file, or <code>null</code> if the given URI
     * does not represent a local file.
     *
     * @param uri The URI to return the file for
     * @return The local file corresponding to the given URI, or
     * <code>null</code>
     */
    public static File toFile(URI uri) {
        if (!isFileURI(uri)) {
            return null;
        }
        // assume all illegal characters have been properly encoded, so use URI
        // class to unencode
        return new File(uri.getSchemeSpecificPart());
    }

    /**
     * @return the class loader that loads the classpath and all jars or
     * directories added with {@link #extendClasspath(File)}.
     */
    protected static ClassLoader driverClassLoader() {
        return DRIVER_CLASS_LOADER;
    }

    /**
     * @return the jandex index of the classpath and all jars or directories
     * added with {@link #extendClasspath(File)}.
     */
    protected static synchronized IndexView classPathIndex() {
        if (classPathIndex == null) {
            List<IndexView> indexes = new ArrayList<>();
            for (File entry : classPathEntries(System.getProperty("java.class.path", ""))) {
                try {
                    indexes.add(index(entry));
                } catch (IOException e) {
                    LOG.warn("could not index classpath entry " + entry, e);
                }
            }
            classPathIndex = CompositeIndex.create(indexes);
        }
        if (EXTENSION_INDEXES.isEmpty()) {
            return classPathIndex;
        }
        List<IndexView> indexes = new ArrayList<>(EXTENSION_INDEXES);
        indexes.add(classPathIndex);
        return CompositeIndex.create(indexes);
    }

    /**
     * @param classPath a classpath in the format of java.class.path.
     * @return its entries, including the manifest Class-Path entries of its
     * jars.
     */
    static Set<File> classPathEntries(String classPath) {
        Set<File> entries = new LinkedHashSet<>();
        for (String entry : classPath.split(File.pathSeparator)) {
            if (!entry.isEmpty()) {
                addClassPathEntry(new File(entry), entries);
            }
        }
        return entries;
    }

    /**
     * add a classpath entry and, for jars, the entries of their manifest
     * Class-Path, as used by "java -jar" and manifest-only jars.
     *
     * @param entry   the jar or directory.
     * @param entries the entries found so far.
     */
    private static void addClassPathEntry(File entry, Set<File> entries) {
        if (!entries.add(entry) || !entry.isFile() || !entry.getName().endsWith(".jar")) {
            return;
        }
        try (JarFile jarFile = new JarFile(entry)) {
            Manifest manifest = jarFile.getManifest();
            String classPath = manifest == null ? null : manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
            if (classPath == null) {
                return;
            }
            URI base = entry.getParentFile() == null ? new File(".").toURI() : entry.getParentFile().toURI();
            for (String reference : classPath.trim().split("\\s+")) {
                if (!reference.isEmpty()) {
                    File referenced = toFile(base.resolve(reference));
                    if (referenced != null) {
                        addClassPathEntry(referenced, entries);
                    }
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            LOG.warn("could not read the manifest class path of " + entry, e);
        }
    }

    /**
     * extend the driver class loader with one jar file or a directory with
     * classes.
     *
     * @param dirOrJar the directory or jar file
     * @return the index of the jar/directory file.
     */
    protected static synchronized IndexView extendClasspath(File dirOrJar) {
        try {
            URL url = dirOrJar.toURI().toURL();
            Index index = index(dirOrJar);
            for (URL existing : DRIVER_CLASS_LOADER.getURLs()) {
                if (existing.toString().equalsIgnoreCase(url.toString())) {
                    return index;
                }
            }
            DRIVER_CLASS_LOADER.addDriverURL(url);
            EXTENSION_INDEXES.add(index);
            return index;
        } catch (IOException e) {
            throw new IllegalStateException("Could not include " + dirOrJar + " in the driver classpath!", e);
        }
    }

    /**
     * index one jar file or directory, in memory.
     *
     * @param file the jar file or directory to index.
     * @return the index, empty if the file is neither a jar nor a directory.
     * @throws IOException if the jar could not be read.
     */
    private static Index index(File file) throws IOException {
        Indexer indexer = new Indexer();
        if (file.isDirectory()) {
            indexDirectory(file, indexer);
        } else if (file.isFile() && file.getName().endsWith(".jar")) {
            try (JarFile jarFile = new JarFile(file)) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().endsWith(".class")) {
                        try (InputStream in = jarFile.getInputStream(entry)) {
                            indexer.index(in);
                        } catch (Exception e) {
                            LOG.debug("could not scan " + entry.getName() + " in " + file.getAbsolutePath(), e);
                        }
                    }
                }
            }
        }
        return indexer.complete();
    }

    /**
     * index the class files of a directory recursively using the specified
     * indexer.
     *
     * @param file    the file or directory to index.
     * @param indexer the indexer to use.
     */
    private static void indexDirectory(File file, Indexer indexer) {
        if (file.isFile() && file.getName().endsWith(".class")) {
            try (InputStream in = new FileInputStream(file)) {
                indexer.index(in);
            } catch (Exception e) {
                LOG.error("could not scan " + file.getAbsolutePath());
            }
        } else if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    indexDirectory(child, indexer);
                }
            }
        }
    }

    /**
     * class loader that can be extended with driver jars at runtime.
     */
    private static final class DriverClassLoader extends URLClassLoader {

        static {
            ClassLoader.registerAsParallelCapable();
        }

        /**
         * @param parent the parent class loader.
         */
        DriverClassLoader(ClassLoader parent) {
            super(new URL[0], parent);
        }

        /**
         * @param url the jar or directory url to add.
         */
        void addDriverURL(URL url) {
            addURL(url);
        }
    }
}
