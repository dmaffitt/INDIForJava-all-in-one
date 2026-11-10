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

import org.indilib.i4j.protocol.INDIProtocol;
import org.indilib.i4j.protocol.api.INDIOutputStream;
import org.indilib.i4j.protocol.url.INDIURLStreamHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * Writes INDI protocol objects as xml elements, one after the other.
 *
 * @author Richard van Nieuwenhoven
 */
public class INDIOutputStreamImpl extends OutputStream implements INDIOutputStream {

    /**
     * The logger to log to.
     */
    private static final Logger LOG = LoggerFactory.getLogger(INDIOutputStreamImpl.class);

    static {
        INDIURLStreamHandlerFactory.init();
    }

    /**
     * the writer for the xml.
     */
    private final Writer out;

    /**
     * create an indi output stream.
     *
     * @param out the stream to write the xml to.
     */
    protected INDIOutputStreamImpl(OutputStream out) {
        this.out = new OutputStreamWriter(out, StandardCharsets.UTF_8);
    }

    @Override
    public synchronized void close() throws IOException {
        out.close();
    }

    @Override
    public void write(int b) throws IOException {
        throw new IOException("not supported method");
    }

    @Override
    public synchronized void writeObject(INDIProtocol<?> element) throws IOException {
        StringBuilder xml = new StringBuilder();
        INDIProtocolXml.write(element, xml);
        if (LOG.isTraceEnabled()) {
            LOG.trace("sending indi object {} {}", element.getClass().getSimpleName(), xml);
        }
        out.write(xml.toString());
        out.flush();
    }
}
