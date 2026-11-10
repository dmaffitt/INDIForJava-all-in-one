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
import org.indilib.i4j.protocol.url.INDIURLStreamHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Reads INDI protocol objects from a stream of xml elements, using the
 * streaming xml parser of the jdk. Elements that are not part of the protocol
 * are skipped.
 *
 * @author Richard van Nieuwenhoven
 */
public class INDIInputStreamImpl extends InputStream implements INDIInputStream {

    /**
     * The logger to log to.
     */
    private static final Logger LOG = LoggerFactory.getLogger(INDIInputStreamImpl.class);

    static {
        INDIURLStreamHandlerFactory.init();
    }

    /**
     * the xml input, the protocol elements wrapped in one root element.
     */
    private final InputStream in;

    /**
     * the xml parser, created on the first read so that creating the stream
     * never blocks.
     */
    private XMLStreamReader xml;

    /**
     * true when the root element was read.
     */
    private boolean rootRead;

    /**
     * create an indi input stream.
     *
     * @param in the xml input, with the protocol elements wrapped in one root
     *           element.
     */
    protected INDIInputStreamImpl(InputStream in) {
        this.in = in;
    }

    /**
     * @return a parser that does not resolve external entities or dtds, the
     * input comes from the network.
     */
    private static XMLInputFactory createInputFactory() {
        XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        return factory;
    }

    @Override
    public void close() throws IOException {
        if (xml != null) {
            try {
                xml.close();
            } catch (XMLStreamException e) {
                LOG.debug("could not close the xml parser", e);
            }
        }
        in.close();
    }

    @Override
    public int read() throws IOException {
        throw new IOException("not supported method");
    }

    @Override
    public synchronized INDIProtocol<?> readObject() throws IOException {
        try {
            if (xml == null) {
                xml = createInputFactory().createXMLStreamReader(new XmlDeclarationFilterReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
            while (xml.hasNext()) {
                int event = xml.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    if (!rootRead) {
                        rootRead = true;
                        continue;
                    }
                    INDIProtocol<?> message = INDIProtocolXml.newMessage(xml.getLocalName());
                    if (message == null) {
                        LOG.debug("skipping unknown element {}", xml.getLocalName());
                        skipElement();
                        continue;
                    }
                    readElement(message);
                    message.trim();
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("received indi object {}", message);
                    }
                    return message;
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    // the end of the root element: the end of the stream
                    return null;
                }
            }
            return null;
        } catch (XMLStreamException | IllegalArgumentException e) {
            throw new IOException("could not deserialize xml", e);
        }
    }

    /**
     * read the attributes, text and children of the current element.
     *
     * @param protocol the protocol object to fill.
     * @throws XMLStreamException if the xml is not well formed.
     */
    private void readElement(INDIProtocol<?> protocol) throws XMLStreamException {
        for (int index = 0; index < xml.getAttributeCount(); index++) {
            INDIProtocolXml.setAttribute(protocol, xml.getAttributeLocalName(index), xml.getAttributeValue(index));
        }
        StringBuilder text = new StringBuilder();
        while (true) {
            int event = xml.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> {
                    INDIProtocol<?> child = INDIProtocolXml.addChild(protocol, xml.getLocalName());
                    if (child == null) {
                        skipElement();
                    } else {
                        readElement(child);
                    }
                }
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA, XMLStreamConstants.SPACE -> text.append(xml.getText());
                case XMLStreamConstants.END_ELEMENT -> {
                    if (INDIProtocolXml.hasText(protocol)) {
                        INDIProtocolXml.setText(protocol, text.toString());
                    }
                    return;
                }
                default -> {
                    // comments and processing instructions
                }
            }
        }
    }

    /**
     * skip the current element with everything in it.
     *
     * @throws XMLStreamException if the xml is not well formed.
     */
    private void skipElement() throws XMLStreamException {
        int depth = 1;
        while (depth > 0) {
            int event = xml.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
    }
}
