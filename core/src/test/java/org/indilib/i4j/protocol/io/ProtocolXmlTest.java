package org.indilib.i4j.protocol.io;

import org.indilib.i4j.protocol.DefNumber;
import org.indilib.i4j.protocol.DefNumberVector;
import org.indilib.i4j.protocol.DefSwitch;
import org.indilib.i4j.protocol.DefSwitchVector;
import org.indilib.i4j.protocol.DefText;
import org.indilib.i4j.protocol.DefTextVector;
import org.indilib.i4j.protocol.DelProperty;
import org.indilib.i4j.protocol.EnableBLOB;
import org.indilib.i4j.protocol.GetProperties;
import org.indilib.i4j.protocol.INDIProtocol;
import org.indilib.i4j.protocol.Message;
import org.indilib.i4j.protocol.NewNumberVector;
import org.indilib.i4j.protocol.NewSwitchVector;
import org.indilib.i4j.protocol.NewTextVector;
import org.indilib.i4j.protocol.OneBlob;
import org.indilib.i4j.protocol.OneNumber;
import org.indilib.i4j.protocol.OneSwitch;
import org.indilib.i4j.protocol.OneText;
import org.indilib.i4j.protocol.SetBlobVector;
import org.indilib.i4j.protocol.SetNumberVector;
import org.indilib.i4j.protocol.SetSwitchVector;
import org.indilib.i4j.protocol.SetTextVector;
import org.indilib.i4j.protocol.api.INDIInputStream;
import org.indilib.i4j.protocol.api.INDIOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading and writing the INDI XML protocol, as other INDI implementations
 * produce and expect it.
 */
class ProtocolXmlTest {

    static List<INDIProtocol<?>> parse(String xml) throws IOException {
        INDIInputStream in = INDIProtocolFactory.createINDIInputStream(new ByteArrayInputStream(xml.getBytes(UTF_8)));
        List<INDIProtocol<?>> result = new ArrayList<>();
        for (INDIProtocol<?> message = in.readObject(); message != null; message = in.readObject()) {
            result.add(message);
        }
        return result;
    }

    static INDIProtocol<?> parseOne(String xml) throws IOException {
        List<INDIProtocol<?>> messages = parse(xml);
        assertEquals(1, messages.size(), "messages in " + xml);
        return messages.get(0);
    }

    static String write(INDIProtocol<?>... messages) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        INDIOutputStream out = INDIProtocolFactory.createINDIOutputStream(bytes);
        for (INDIProtocol<?> message : messages) {
            out.writeObject(message);
        }
        return bytes.toString(UTF_8);
    }

    @Test
    void readsNumberVectorAsSentByIndiserver() throws IOException {
        DefNumberVector vector = assertInstanceOf(DefNumberVector.class, parseOne("""
                <defNumberVector device="Telescope Simulator" name="EQUATORIAL_EOD_COORD" label="Eq. Coordinates"
                        group="Main Control" state="Idle" perm="rw" timeout="60" timestamp="2026-11-09T12:34:56">
                    <defNumber name="RA" label="RA (hh:mm:ss)" format="%010.6m" min="0" max="24" step="0">
                5.5
                    </defNumber>
                    <defNumber name="DEC" label="DEC (dd:mm:ss)" format="%010.6m" min="-90" max="90" step="0">
                -12.25
                    </defNumber>
                </defNumberVector>
                """));
        assertEquals("Telescope Simulator", vector.getDevice());
        assertEquals("EQUATORIAL_EOD_COORD", vector.getName());
        assertEquals("Eq. Coordinates", vector.getLabel());
        assertEquals("Main Control", vector.getGroup());
        assertEquals("Idle", vector.getState());
        assertEquals("rw", vector.getPerm());
        assertEquals("60", vector.getTimeout());
        assertEquals("2026-11-09T12:34:56", vector.getTimestamp());
        assertEquals(2, vector.getElements().size());
        DefNumber ra = assertInstanceOf(DefNumber.class, vector.getElements().get(0));
        assertEquals("RA", ra.getName());
        assertEquals("%010.6m", ra.getFormat());
        assertEquals("0", ra.getMin());
        assertEquals("24", ra.getMax());
        assertEquals("5.5", ra.getTextContent().trim());
        assertEquals("-12.25", vector.getElements().get(1).getTextContent().trim());
    }

    @Test
    void readsSeveralMessagesFromOneStream() throws IOException {
        List<INDIProtocol<?>> messages = parse("""
                <defSwitchVector device="CCD" name="CONNECTION" state="Ok" perm="rw" rule="OneOfMany">
                    <defSwitch name="CONNECT" label="Connect">On</defSwitch>
                    <defSwitch name="DISCONNECT" label="Disconnect">Off</defSwitch>
                </defSwitchVector>
                <setSwitchVector device="CCD" name="CONNECTION" state="Busy">
                    <oneSwitch name="CONNECT">Off</oneSwitch>
                </setSwitchVector>
                <delProperty device="CCD" name="CONNECTION"/>
                """);
        assertEquals(3, messages.size());
        DefSwitchVector def = assertInstanceOf(DefSwitchVector.class, messages.get(0));
        assertEquals("OneOfMany", def.getRule());
        assertEquals("On", def.getElements().get(0).getTextContent().trim());
        assertInstanceOf(DefSwitch.class, def.getElements().get(1));
        SetSwitchVector set = assertInstanceOf(SetSwitchVector.class, messages.get(1));
        assertEquals("Busy", set.getState());
        assertInstanceOf(OneSwitch.class, set.getElements().get(0));
        DelProperty del = assertInstanceOf(DelProperty.class, messages.get(2));
        assertEquals("CCD", del.getDevice());
        assertEquals("CONNECTION", del.getName());
    }

    @Test
    void messageWithoutDeviceIsGlobal() throws IOException {
        Message message = assertInstanceOf(Message.class, parseOne("<message timestamp=\"2026-11-09T01:02:03\" message=\"hello all\"/>"));
        assertNull(message.getDevice());
        assertEquals(false, message.hasDevice());
        assertEquals("hello all", message.getMessage());
    }

    @Test
    void readsClientRequests() throws IOException {
        List<INDIProtocol<?>> messages = parse("""
                <getProperties version="1.7" device="CCD" name="CCD1"/>
                <enableBLOB device="CCD">Also</enableBLOB>
                <newNumberVector device="CCD" name="CCD_EXPOSURE">
                    <oneNumber name="CCD_EXPOSURE_VALUE">1.5</oneNumber>
                </newNumberVector>
                """);
        GetProperties get = assertInstanceOf(GetProperties.class, messages.get(0));
        assertEquals("1.7", get.getVersion());
        assertEquals("CCD", get.getDevice());
        assertEquals("CCD1", get.getName());
        EnableBLOB enable = assertInstanceOf(EnableBLOB.class, messages.get(1));
        assertEquals("Also", enable.getTextContent().trim());
        NewNumberVector exposure = assertInstanceOf(NewNumberVector.class, messages.get(2));
        OneNumber value = assertInstanceOf(OneNumber.class, exposure.getElements().get(0));
        assertEquals("CCD_EXPOSURE_VALUE", value.getName());
        assertEquals("1.5", value.getTextContent().trim());
    }

    @Test
    void readsBase64Blobs() throws IOException {
        byte[] image = {0, 1, 2, (byte) 0xfe, (byte) 0xff, 42};
        SetBlobVector vector = assertInstanceOf(SetBlobVector.class, parseOne(
                "<setBLOBVector device=\"CCD\" name=\"CCD1\" state=\"Ok\"><oneBLOB name=\"CCD1\" size=\"6\" format=\".fits\">\n"
                        + Base64.getEncoder().encodeToString(image) + "\n</oneBLOB></setBLOBVector>"));
        OneBlob blob = assertInstanceOf(OneBlob.class, vector.getElements().get(0));
        assertEquals(".fits", blob.getFormat());
        assertEquals("6", blob.getSize());
        assertArrayEquals(image, blob.getByteContent());
    }

    @Test
    void writesEndTagsForEmptyElements() throws IOException {
        // other INDI implementations expect <x></x> instead of <x/>
        assertEquals("<getProperties version=\"1.7\"></getProperties>", write(new GetProperties().setVersion("1.7")));
    }

    @Test
    void writesAttributesAndText() throws IOException {
        String xml = write(new SetNumberVector().setDevice("Focuser").setName("ABS_POSITION").setState("Ok")
                .addElement(new OneNumber().setName("POSITION").setTextContent("1234")));
        assertTrue(xml.startsWith("<setNumberVector "), xml);
        assertTrue(xml.contains("device=\"Focuser\""), xml);
        assertTrue(xml.contains("state=\"Ok\""), xml);
        assertTrue(xml.contains("<oneNumber name=\"POSITION\">1234</oneNumber>"), xml);
    }

    @Test
    void roundTripsEveryMessageFamily() throws IOException {
        byte[] bytes = "binary\u0000data".getBytes(UTF_8);
        DefTextVector defText = new DefTextVector().setDevice("D").setName("T").setState("Idle").setPerm("ro").setGroup("G");
        defText.getElements().add(new DefText().setName("t1").setLabel("Text").setTextContent("value & <escaped>"));
        List<INDIProtocol<?>> read = parse(write(
                defText,
                new NewTextVector().setDevice("D").setName("T").addElement(new OneText().setName("t1").setTextContent("new")),
                new NewSwitchVector().setDevice("D").setName("S").addElement(new OneSwitch().setName("s1").setTextContent("On")),
                new SetBlobVector().setDevice("D").setName("B").setState("Ok")
                        .addElement(new OneBlob().setName("b1").setFormat(".bin").setByteContent(bytes)),
                new Message().setDevice("D").setMessage("done").setTimestamp("2026-11-09T00:00:00.000"),
                new DelProperty().setDevice("D")));
        assertEquals(6, read.size());
        DefTextVector text = assertInstanceOf(DefTextVector.class, read.get(0));
        assertEquals("G", text.getGroup());
        assertEquals("value & <escaped>", text.getElements().get(0).getTextContent());
        assertEquals("new", assertInstanceOf(NewTextVector.class, read.get(1)).getElements().get(0).getTextContent());
        assertEquals("On", assertInstanceOf(NewSwitchVector.class, read.get(2)).getElements().get(0).getTextContent());
        OneBlob blob = assertInstanceOf(OneBlob.class, assertInstanceOf(SetBlobVector.class, read.get(3)).getElements().get(0));
        assertArrayEquals(bytes, blob.getByteContent());
        assertEquals(Integer.toString(bytes.length), blob.getSize());
        Message message = assertInstanceOf(Message.class, read.get(4));
        assertEquals("done", message.getMessage());
        assertEquals("2026-11-09T00:00:00.000", message.getTimestamp());
        assertEquals("D", assertInstanceOf(DelProperty.class, read.get(5)).getDevice());
    }

    @Test
    void readsEmptyElements() throws IOException {
        SetTextVector vector = assertInstanceOf(SetTextVector.class,
                parseOne("<setTextVector device=\"D\" name=\"T\"><oneText name=\"t\"></oneText><oneText name=\"u\"/></setTextVector>"));
        assertEquals(2, vector.getElements().size());
        assertEquals("", vector.getElements().get(0).getTextContent());
        assertEquals("u", vector.getElements().get(1).getName());
    }

    @Test
    void readsEscapedCharactersAndSingleQuotes() throws IOException {
        List<INDIProtocol<?>> messages = parse("\n  <message device='D' message=\"a &lt;b&gt; &amp; &quot;c&quot; &#233;\"/>\n"
                + "<setTextVector device='D' name='T'><oneText name='t'>x &lt; y &amp;&amp; y &gt; z</oneText></setTextVector>\n");
        assertEquals("a <b> & \"c\" é", messages.get(0).getMessage());
        assertEquals("x < y && y > z", assertInstanceOf(SetTextVector.class, messages.get(1)).getElements().get(0).getTextContent());
    }

    @Test
    void readsBlobsWithLineBreaksAndKeepsTheirSize() throws IOException {
        byte[] data = new byte[300];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        // compressed blobs carry the uncompressed size, which differs from the byte count
        SetBlobVector vector = assertInstanceOf(SetBlobVector.class, parseOne(
                "<setBLOBVector device=\"CCD\" name=\"CCD1\"><oneBLOB name=\"CCD1\" size=\"1000\" format=\".fits.z\">\n"
                        + Base64.getMimeEncoder().encodeToString(data) + "\n</oneBLOB></setBLOBVector>"));
        OneBlob blob = assertInstanceOf(OneBlob.class, vector.getElements().get(0));
        assertArrayEquals(data, blob.getByteContent());
        assertEquals("1000", blob.getSize());
    }

    @Test
    void writesEscapedCharacters() throws IOException {
        String xml = write(new Message().setDevice("D").setMessage("a <b> & \"c\""),
                new NewTextVector().setDevice("D").setName("T").addElement(new OneText().setName("t").setTextContent("x < y & z")));
        assertTrue(xml.contains("message=\"a &lt;b&gt; &amp; &quot;c&quot;\""), xml);
        assertTrue(xml.contains(">x &lt; y &amp; z</oneText>"), xml);
    }

    @Test
    void toStringShowsTheXml() {
        assertEquals("GetProperties <getProperties version=\"1.7\"></getProperties>", new GetProperties().setVersion("1.7").toString());
    }

    @Test
    void refusesClassesOutsideTheProtocol() {
        // the parser reads untrusted network input, it must not instantiate arbitrary classes
        assertThrows(IOException.class, () -> parse("<java.io.File><path>/tmp</path></java.io.File>"));
        assertThrows(IOException.class, () -> parse("<java.lang.ProcessBuilder><command><string>id</string></command></java.lang.ProcessBuilder>"));
    }
}
