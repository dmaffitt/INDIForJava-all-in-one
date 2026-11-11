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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

import org.indilib.i4j.protocol.DefBlob;
import org.indilib.i4j.protocol.DefBlobVector;
import org.indilib.i4j.protocol.DefElement;
import org.indilib.i4j.protocol.DefLight;
import org.indilib.i4j.protocol.DefLightVector;
import org.indilib.i4j.protocol.DefNumber;
import org.indilib.i4j.protocol.DefNumberVector;
import org.indilib.i4j.protocol.DefSwitch;
import org.indilib.i4j.protocol.DefSwitchVector;
import org.indilib.i4j.protocol.DefText;
import org.indilib.i4j.protocol.DefTextVector;
import org.indilib.i4j.protocol.DefVector;
import org.indilib.i4j.protocol.DelProperty;
import org.indilib.i4j.protocol.EnableBLOB;
import org.indilib.i4j.protocol.GetProperties;
import org.indilib.i4j.protocol.INDIProtocol;
import org.indilib.i4j.protocol.Message;
import org.indilib.i4j.protocol.NewBlobVector;
import org.indilib.i4j.protocol.NewLightVector;
import org.indilib.i4j.protocol.NewNumberVector;
import org.indilib.i4j.protocol.NewSwitchVector;
import org.indilib.i4j.protocol.NewTextVector;
import org.indilib.i4j.protocol.NewVector;
import org.indilib.i4j.protocol.OneBlob;
import org.indilib.i4j.protocol.OneElement;
import org.indilib.i4j.protocol.OneLight;
import org.indilib.i4j.protocol.OneNumber;
import org.indilib.i4j.protocol.OneSwitch;
import org.indilib.i4j.protocol.OneText;
import org.indilib.i4j.protocol.SetBlobVector;
import org.indilib.i4j.protocol.SetLightVector;
import org.indilib.i4j.protocol.SetNumberVector;
import org.indilib.i4j.protocol.SetSwitchVector;
import org.indilib.i4j.protocol.SetTextVector;
import org.indilib.i4j.protocol.SetVector;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The mapping between the INDI XML protocol and the protocol classes: element
 * names, attributes and text content, in both directions. Only the classes
 * listed here are ever created from XML.
 *
 * @author Richard van Nieuwenhoven
 */
final class INDIProtocolXml {

    /**
     * the top level messages by element name.
     */
    private static final Map<String, Supplier<INDIProtocol<?>>> MESSAGES = new HashMap<>();

    /**
     * the elements of definition vectors by element name.
     */
    private static final Map<String, Supplier<DefElement<?>>> DEF_ELEMENTS = new HashMap<>();

    /**
     * the elements of new and set vectors by element name.
     */
    private static final Map<String, Supplier<OneElement<?>>> ONE_ELEMENTS = new HashMap<>();

    /**
     * the element names by class.
     */
    private static final Map<Class<?>, String> NAMES = new HashMap<>();

    static {
        message("defTextVector", DefTextVector.class, DefTextVector::new);
        message("defNumberVector", DefNumberVector.class, DefNumberVector::new);
        message("defSwitchVector", DefSwitchVector.class, DefSwitchVector::new);
        message("defLightVector", DefLightVector.class, DefLightVector::new);
        message("defBLOBVector", DefBlobVector.class, DefBlobVector::new);
        message("newTextVector", NewTextVector.class, NewTextVector::new);
        message("newNumberVector", NewNumberVector.class, NewNumberVector::new);
        message("newSwitchVector", NewSwitchVector.class, NewSwitchVector::new);
        message("newLightVector", NewLightVector.class, NewLightVector::new);
        message("newBLOBVector", NewBlobVector.class, NewBlobVector::new);
        message("setTextVector", SetTextVector.class, SetTextVector::new);
        message("setNumberVector", SetNumberVector.class, SetNumberVector::new);
        message("setSwitchVector", SetSwitchVector.class, SetSwitchVector::new);
        message("setLightVector", SetLightVector.class, SetLightVector::new);
        message("setBLOBVector", SetBlobVector.class, SetBlobVector::new);
        message("message", Message.class, Message::new);
        message("delProperty", DelProperty.class, DelProperty::new);
        message("getProperties", GetProperties.class, GetProperties::new);
        message("enableBLOB", EnableBLOB.class, EnableBLOB::new);

        defElement("defText", DefText.class, DefText::new);
        defElement("defNumber", DefNumber.class, DefNumber::new);
        defElement("defSwitch", DefSwitch.class, DefSwitch::new);
        defElement("defLight", DefLight.class, DefLight::new);
        defElement("defBLOB", DefBlob.class, DefBlob::new);

        oneElement("oneText", OneText.class, OneText::new);
        oneElement("oneNumber", OneNumber.class, OneNumber::new);
        oneElement("oneSwitch", OneSwitch.class, OneSwitch::new);
        oneElement("oneLight", OneLight.class, OneLight::new);
        oneElement("oneBLOB", OneBlob.class, OneBlob::new);
    }

    /**
     * utility class.
     */
    private INDIProtocolXml() {
    }

    private static void message(String name, Class<?> type, Supplier<INDIProtocol<?>> factory) {
        MESSAGES.put(name, factory);
        NAMES.put(type, name);
    }

    private static void defElement(String name, Class<?> type, Supplier<DefElement<?>> factory) {
        DEF_ELEMENTS.put(name, factory);
        NAMES.put(type, name);
    }

    private static void oneElement(String name, Class<?> type, Supplier<OneElement<?>> factory) {
        ONE_ELEMENTS.put(name, factory);
        NAMES.put(type, name);
    }

    /**
     * @param name the element name.
     * @return a new message for a top level element, or null if the element is
     * not part of the protocol.
     */
    static INDIProtocol<?> newMessage(String name) {
        Supplier<INDIProtocol<?>> factory = MESSAGES.get(name);
        return factory == null ? null : factory.get();
    }

    /**
     * create a child element and add it to its vector.
     *
     * @param parent the vector.
     * @param name   the element name of the child.
     * @return the new child, or null if the parent can not contain such an
     * element.
     */
    static INDIProtocol<?> addChild(INDIProtocol<?> parent, String name) {
        if (parent instanceof DefVector<?> vector) {
            Supplier<DefElement<?>> factory = DEF_ELEMENTS.get(name);
            if (factory != null) {
                DefElement<?> element = factory.get();
                vector.getElements().add(element);
                return element;
            }
        } else if (parent instanceof NewVector<?> || parent instanceof SetVector<?>) {
            Supplier<OneElement<?>> factory = ONE_ELEMENTS.get(name);
            if (factory != null) {
                OneElement<?> element = factory.get();
                if (parent instanceof NewVector<?> vector) {
                    vector.addElement(element);
                } else {
                    ((SetVector<?>) parent).addElement(element);
                }
                return element;
            }
        }
        return null;
    }

    /**
     * @param protocol a protocol object.
     * @return true if the element carries text content.
     */
    static boolean hasText(INDIProtocol<?> protocol) {
        return protocol instanceof DefElement<?> || protocol instanceof OneElement<?> || protocol instanceof EnableBLOB;
    }

    /**
     * set an attribute, unknown attributes are ignored.
     *
     * @param protocol the protocol object.
     * @param name     the attribute name.
     * @param value    the attribute value.
     */
    static void setAttribute(INDIProtocol<?> protocol, String name, String value) {
        switch (name) {
            case "device" -> protocol.setDevice(value);
            case "name" -> protocol.setName(value);
            case "message" -> protocol.setMessage(value);
            case "timestamp" -> protocol.setTimestamp(value);
            default -> setSpecificAttribute(protocol, name, value);
        }
    }

    private static void setSpecificAttribute(INDIProtocol<?> protocol, String name, String value) {
        switch (protocol) {
            case DefVector<?> vector -> {
                switch (name) {
                    case "group" -> vector.setGroup(value);
                    case "label" -> vector.setLabel(value);
                    case "perm" -> vector.setPerm(value);
                    case "state" -> vector.setState(value);
                    case "timeout" -> vector.setTimeout(value);
                    case "rule" -> {
                        if (vector instanceof DefSwitchVector switchVector) {
                            switchVector.setRule(value);
                        }
                    }
                    default -> {
                    }
                }
            }
            case NewVector<?> vector -> {
                switch (name) {
                    case "state" -> vector.setState(value);
                    case "timeout" -> vector.setTimeout(value);
                    case "rule" -> {
                        if (vector instanceof NewSwitchVector switchVector) {
                            switchVector.setRule(value);
                        }
                    }
                    default -> {
                    }
                }
            }
            case SetVector<?> vector -> {
                switch (name) {
                    case "state" -> vector.setState(value);
                    case "timeout" -> vector.setTimeout(value);
                    case "rule" -> {
                        if (vector instanceof SetSwitchVector switchVector) {
                            switchVector.setRule(value);
                        }
                    }
                    default -> {
                    }
                }
            }
            case DefNumber number -> {
                switch (name) {
                    case "label" -> number.setLabel(value);
                    case "format" -> number.setFormat(value);
                    case "min" -> number.setMin(value);
                    case "max" -> number.setMax(value);
                    case "step" -> number.setStep(value);
                    default -> {
                    }
                }
            }
            case DefElement<?> element -> {
                if ("label".equals(name)) {
                    element.setLabel(value);
                }
            }
            case OneNumber number -> {
                switch (name) {
                    case "min" -> number.setMin(value);
                    case "max" -> number.setMax(value);
                    default -> {
                    }
                }
            }
            case OneBlob blob -> {
                switch (name) {
                    case "format" -> blob.setFormat(value);
                    case "size" -> blob.setSize(value);
                    default -> {
                    }
                }
            }
            case GetProperties getProperties -> {
                switch (name) {
                    case "version" -> getProperties.setVersion(value);
                    case "property" -> getProperties.setProperty(value);
                    default -> {
                    }
                }
            }
            case DelProperty delProperty -> {
                if ("version".equals(name)) {
                    delProperty.setVersion(value);
                }
            }
            default -> {
            }
        }
    }

    /**
     * set the text content of an element.
     *
     * @param protocol the protocol object.
     * @param text     the text, as it is in the xml.
     */
    static void setText(INDIProtocol<?> protocol, String text) {
        switch (protocol) {
            case OneBlob blob -> {
                // the size attribute may differ from the byte count (compression), keep it.
                String size = blob.getSize();
                blob.setByteContent(Base64.getMimeDecoder().decode(text));
                if (size != null) {
                    blob.setSize(size);
                }
            }
            case DefElement<?> element -> element.setTextContent(text);
            case OneElement<?> element -> element.setTextContent(text);
            case EnableBLOB enableBLOB -> enableBLOB.setTextContent(text);
            default -> {
            }
        }
    }

    /**
     * @param protocol the protocol object.
     * @return the attributes to write, in order, without the null ones.
     */
    private static List<String[]> attributes(INDIProtocol<?> protocol) {
        List<String[]> attributes = new ArrayList<>();
        add(attributes, "device", protocol.getDevice());
        add(attributes, "name", protocol.getName());
        add(attributes, "message", protocol.getMessage());
        add(attributes, "timestamp", protocol.getTimestamp());
        switch (protocol) {
            case DefVector<?> vector -> {
                add(attributes, "group", vector.getGroup());
                add(attributes, "label", vector.getLabel());
                add(attributes, "perm", vector.getPerm());
                add(attributes, "state", vector.getState());
                add(attributes, "timeout", vector.getTimeout());
                if (vector instanceof DefSwitchVector switchVector) {
                    add(attributes, "rule", switchVector.getRule());
                }
            }
            case NewVector<?> vector -> {
                add(attributes, "state", vector.getState());
                add(attributes, "timeout", vector.getTimeout());
                if (vector instanceof NewSwitchVector switchVector) {
                    add(attributes, "rule", switchVector.getRule());
                }
            }
            case SetVector<?> vector -> {
                add(attributes, "state", vector.getState());
                add(attributes, "timeout", vector.getTimeout());
                if (vector instanceof SetSwitchVector switchVector) {
                    add(attributes, "rule", switchVector.getRule());
                }
            }
            case DefElement<?> element -> {
                add(attributes, "label", element.getLabel());
                if (element instanceof DefNumber number) {
                    add(attributes, "format", number.getFormat());
                    add(attributes, "min", number.getMin());
                    add(attributes, "max", number.getMax());
                    add(attributes, "step", number.getStep());
                }
            }
            case OneNumber number -> {
                add(attributes, "min", number.getMin());
                add(attributes, "max", number.getMax());
            }
            case OneBlob blob -> {
                add(attributes, "format", blob.getFormat());
                add(attributes, "size", blob.getSize());
            }
            case GetProperties getProperties -> {
                // these getters return "" for unset values
                addIfNotEmpty(attributes, "version", getProperties.getVersion());
                addIfNotEmpty(attributes, "property", getProperties.getProperty());
            }
            case DelProperty delProperty -> add(attributes, "version", delProperty.getVersion());
            default -> {
            }
        }
        return attributes;
    }

    private static void add(List<String[]> attributes, String name, String value) {
        if (value != null) {
            attributes.add(new String[]{name, value});
        }
    }

    private static void addIfNotEmpty(List<String[]> attributes, String name, String value) {
        if (value != null && !value.isEmpty()) {
            attributes.add(new String[]{name, value});
        }
    }

    /**
     * @param protocol the protocol object.
     * @return the text content to write, or null if there is none.
     */
    private static String text(INDIProtocol<?> protocol) {
        return switch (protocol) {
            case OneBlob blob -> blob.getByteContent() == null ? null : Base64.getEncoder().encodeToString(blob.getByteContent());
            case DefElement<?> element -> element.getTextContent();
            case OneElement<?> element -> element.getTextContent();
            case EnableBLOB enableBLOB -> enableBLOB.getTextContent();
            default -> null;
        };
    }

    /**
     * @param protocol the protocol object.
     * @return the child elements to write.
     */
    private static List<? extends INDIProtocol<?>> children(INDIProtocol<?> protocol) {
        return switch (protocol) {
            case DefVector<?> vector -> vector.getElements();
            case NewVector<?> vector -> vector.getElements();
            case SetVector<?> vector -> vector.getElements();
            default -> List.of();
        };
    }

    /**
     * write a protocol object as xml. Every element gets an end tag, even
     * empty ones, because other INDI implementations expect them.
     *
     * @param protocol the protocol object.
     * @param out      where to write the xml.
     */
    static void write(INDIProtocol<?> protocol, StringBuilder out) {
        String name = null;
        for (Class<?> type = protocol.getClass(); name == null && type != null; type = type.getSuperclass()) {
            name = NAMES.get(type);
        }
        if (name == null) {
            throw new IllegalArgumentException("not an INDI protocol element: " + protocol.getClass().getName());
        }
        out.append('<').append(name);
        for (String[] attribute : attributes(protocol)) {
            out.append(' ').append(attribute[0]).append("=\"");
            escape(attribute[1], true, out);
            out.append('"');
        }
        out.append('>');
        String text = text(protocol);
        if (text != null) {
            escape(text, false, out);
        }
        for (INDIProtocol<?> child : children(protocol)) {
            write(child, out);
        }
        out.append("</").append(name).append('>');
    }

    /**
     * escape text for xml.
     *
     * @param value     the text.
     * @param attribute true for attribute values, where quotes and line breaks
     *                  must be escaped too.
     * @param out       where to write the escaped text.
     */
    private static void escape(String value, boolean attribute, StringBuilder out) {
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append(attribute ? "&quot;" : "\"");
                case '\n', '\r', '\t' -> {
                    if (attribute) {
                        out.append("&#").append((int) c).append(';');
                    } else {
                        out.append(c);
                    }
                }
                default -> {
                    // other control characters are not allowed in xml 1.0
                    if (c >= ' ') {
                        out.append(c);
                    }
                }
            }
        }
    }
}
