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
import org.indilib.i4j.protocol.api.INDIInputStream;
import org.indilib.i4j.protocol.api.INDIOutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.util.Enumeration;

/**
 * Factory for the streams of the indi xml protocol.
 *
 * @author Richard van Nieuwenhoven
 */
public final class INDIProtocolFactory {

    /**
     * buffer size to use for the in and output stream.
     */
    private static final int BUFFER_SIZE = 16 * 1024;

    /**
     * The INDI protocol has no root element, the input is wrapped in this one.
     */
    private static final byte[] OPEN_BYTES = "<X>".getBytes();

    /**
     * the end of the root element.
     */
    private static final byte[] CLOSE_BYTES = "</X>".getBytes();

    /**
     * utility class.
     */
    private INDIProtocolFactory() {
    }

    /**
     * create an indi protocol input stream around an input stream.
     *
     * @param in the underlaying input stream where the xml will be read.
     * @return the resultung indi input stream
     * @throws IOException when something went wrong with the underlaying intput stream.
     */
    public static INDIInputStream createINDIInputStream(InputStream in) throws IOException {
        return new INDIInputStreamImpl(inputStreamWithRootTag(new BufferedInputStream(new MinimalBlockinInputStream(in), BUFFER_SIZE)));
    }

    /**
     * create an indi protocol output stream around an output stream.
     *
     * @param out the underlaying output stream where the xml will be written.
     * @return the resultung indi output stream
     * @throws IOException when something went wrong with the underlaying output stream.
     */
    public static INDIOutputStream createINDIOutputStream(OutputStream out) throws IOException {
        return new INDIOutputStreamImpl(new BufferedOutputStream(out, BUFFER_SIZE));
    }

    /**
     * wrap the input stream in a root element.
     *
     * @param in the input stream
     * @return the input stream between a root start and end tag.
     */
    private static InputStream inputStreamWithRootTag(final InputStream in) {
        Enumeration<InputStream> streamEnum = new Enumeration<InputStream>() {

            private final InputStream[] streams = {
                    new ByteArrayInputStream(OPEN_BYTES), in,
                    new ByteArrayInputStream(CLOSE_BYTES)};
            private int index = 0;

            @Override
            public boolean hasMoreElements() {
                return index < streams.length;
            }

            @Override
            public InputStream nextElement() {
                return streams[index++];
            }
        };
        return new SequenceInputStream(streamEnum);
    }

    /**
     * @param protocol the protocol object.
     * @return the class name and the xml of the protocol object, for logging.
     */
    public static String toString(INDIProtocol<?> protocol) {
        return protocol.getClass().getSimpleName() + " " + toXml(protocol);
    }

    /**
     * @param protocol the protocol object.
     * @return the xml of the protocol object.
     */
    public static String toXml(INDIProtocol<?> protocol) {
        StringBuilder xml = new StringBuilder();
        INDIProtocolXml.write(protocol, xml);
        return xml.toString();
    }
}
