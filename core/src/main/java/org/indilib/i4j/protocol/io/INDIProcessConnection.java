package org.indilib.i4j.protocol.io;

/*
 * #%L
 * INDI Protocol implementation
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

import org.indilib.i4j.protocol.api.INDIConnection;
import org.indilib.i4j.protocol.api.INDIInputStream;
import org.indilib.i4j.protocol.api.INDIOutputStream;
import org.indilib.i4j.protocol.url.INDIURLStreamHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URI;

/**
 * This class implements an indi protocol connector to an other process. the in
 * and out put stream are connected to the in an d output stream of the external
 * program.
 *
 * @author Richard van Nieuwenhoven
 */
public class INDIProcessConnection implements INDIConnection {

    /**
     * the logger to log to.
     */
    private static final Logger LOG = LoggerFactory.getLogger(INDIProcessConnection.class);

    static {
        INDIURLStreamHandlerFactory.init();
    }

    /**
     * the process from with the in and output stream come.
     */
    private final Process process;
    /**
     * the input stream from the process deserialized as indi protocol objects.
     */
    private INDIInputStream inputStream;
    /**
     * the ouput stream from the process serialized from indi protocol objects.
     */
    private INDIOutputStream outputStream;

    /**
     * construct the indi connection around the process.
     *
     * @param process the process to use.
     */
    public INDIProcessConnection(Process process) {
        this.process = process;
        Thread errorReader = new Thread(this::logErrorStream, "process stderr " + process.pid());
        errorReader.setDaemon(true);
        errorReader.start();
    }

    /**
     * log the error stream of the process, if nobody reads it the process
     * blocks as soon as the pipe buffer is full.
     */
    private void logErrorStream() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                LOG.info("[" + process.pid() + "] " + line);
            }
        } catch (IOException e) {
            LOG.debug("error stream of process " + process.pid() + " closed", e);
        }
    }

    @Override
    public synchronized INDIInputStream getINDIInputStream() throws IOException {
        if (inputStream == null) {
            inputStream = INDIProtocolFactory.createINDIInputStream(process.getInputStream());
        }
        return inputStream;
    }

    @Override
    public synchronized INDIOutputStream getINDIOutputStream() throws IOException {
        if (outputStream == null) {
            outputStream = INDIProtocolFactory.createINDIOutputStream(process.getOutputStream());
        }
        return outputStream;
    }

    @Override
    public void close() throws IOException {
        // closing stdin signals the end of input to the process, closing stdout
        // ends the reader.
        try {
            process.getOutputStream().close();
        } finally {
            process.getInputStream().close();
        }
    }

    @Override
    public URL getURL() {
        try {
            return URI.create("indi:///?process=" + process.pid()).toURL();
        } catch (MalformedURLException e) {
            LOG.error("illegal std url, should never happen!", e);
            return null;
        }
    }
}
