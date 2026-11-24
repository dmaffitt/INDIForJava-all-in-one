package org.indilib.i4j.client;

import org.indilib.i4j.protocol.INDIProtocol;
import org.indilib.i4j.protocol.api.INDIInputStream;
import org.indilib.i4j.protocol.io.INDIProtocolFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * How a connection takes a driver's delProperty: a property by its name; the whole device when
 * there is no name; nothing for a name given but empty (a driver's unnamed property, as
 * indi_pegasus_ppba deletes one when it disconnects from its box), and nothing for a device the
 * connection does not have (it used to delete every device, or fail when there were none).
 */
class DeletePropertyTest {

    private static final String DEFS = "<defNumberVector device='Box' name='POWER_SENSORS' label='POWER_SENSORS' group='Main' state='Ok' perm='ro'>"
            + "<defNumber name='SENSOR_VOLTAGE' label='SENSOR_VOLTAGE' format='%g' min='0' max='20' step='0'>12.4</defNumber></defNumberVector>"
            + "<defSwitchVector device='Box' name='CONNECTION' label='CONNECTION' group='Main' state='Ok' perm='rw' rule='OneOfMany'>"
            + "<defSwitch name='CONNECT' label='CONNECT'>On</defSwitch><defSwitch name='DISCONNECT' label='DISCONNECT'>Off</defSwitch></defSwitchVector>"
            + "<defNumberVector device='Camera' name='CCD_TEMPERATURE' label='CCD_TEMPERATURE' group='Main' state='Ok' perm='rw'>"
            + "<defNumber name='CCD_TEMPERATURE_VALUE' label='CCD_TEMPERATURE_VALUE' format='%g' min='-50' max='50' step='0'>20</defNumber></defNumberVector>";

    /** The messages in this XML, parsed as a connection reads them from its server. */
    private static List<INDIProtocol<?>> parse(String xml) throws IOException {
        INDIInputStream in = INDIProtocolFactory.createINDIInputStream(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        List<INDIProtocol<?>> messages = new ArrayList<>();
        for (INDIProtocol<?> m; (m = in.readObject()) != null; ) {
            messages.add(m);
        }
        return messages;
    }

    private static INDIServerConnection connection(String xml) throws IOException {
        INDIServerConnection c = new INDIServerConnection("localhost", 7624);
        for (INDIProtocol<?> m : parse(DEFS + xml)) {
            c.processProtocolMessage(m);
        }
        return c;
    }

    @Test
    void aPropertyIsDeletedByItsName() throws IOException {
        INDIServerConnection c = connection("<delProperty device='Box' name='POWER_SENSORS'></delProperty>");
        assertNull(c.getDevice("Box").getProperty("POWER_SENSORS"));
        assertNotNull(c.getDevice("Box").getProperty("CONNECTION"));
    }

    @Test
    void noNameDeletesTheDevice() throws IOException {
        INDIServerConnection c = connection("<delProperty device='Box'/>");
        assertNull(c.getDevice("Box"));
        assertNotNull(c.getDevice("Camera"), "only that device");
    }

    @Test
    void anEmptyNameDeletesNothing() throws IOException {
        INDIServerConnection c = connection("<delProperty device='Box' name=''></delProperty>"
                + "<delProperty device='Box' name='POWER_SENSORS'></delProperty>");
        assertNotNull(c.getDevice("Box"), "the device stays");
        assertNotNull(c.getDevice("Box").getProperty("CONNECTION"), "with what was not deleted");
        assertNull(c.getDevice("Box").getProperty("POWER_SENSORS"), "and the driver's next deletion still deletes its property");
    }

    @Test
    void aDeviceTheConnectionDoesNotHaveDeletesNothing() throws IOException {
        INDIServerConnection c = connection("<delProperty device='Gone' name='POWER_SENSORS'></delProperty>");
        assertEquals(2, c.getDevicesAsList().size(), "the others stay");
        for (INDIProtocol<?> m : parse("<delProperty device='Box'/><delProperty device='Camera'/>"
                + "<delProperty device='Box' name='CONNECTION'></delProperty>")) {
            c.processProtocolMessage(m);
        }
        assertEquals(0, c.getDevicesAsList().size(), "none left, and no failure for a device then unknown");
        for (INDIProtocol<?> m : parse(DEFS)) {
            c.processProtocolMessage(m);
        }
        assertNotNull(c.getDevice("Box").getProperty("CONNECTION"), "defined again");
    }
}
