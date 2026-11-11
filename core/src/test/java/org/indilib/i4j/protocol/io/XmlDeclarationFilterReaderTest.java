package org.indilib.i4j.protocol.io;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XmlDeclarationFilterReaderTest {

    /**
     * returns one character per read, like a slow network connection.
     */
    private static final class OneCharReader extends Reader {

        private final Reader in;

        OneCharReader(String text) {
            in = new StringReader(text);
        }

        @Override
        public int read(char[] buffer, int offset, int length) throws IOException {
            return in.read(buffer, offset, Math.min(1, length));
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }

    private static String filter(Reader in) throws IOException {
        StringBuilder out = new StringBuilder();
        try (Reader reader = new XmlDeclarationFilterReader(in)) {
            char[] buffer = new char[7];
            for (int count = reader.read(buffer); count >= 0; count = reader.read(buffer)) {
                out.append(buffer, 0, count);
            }
        }
        return out.toString();
    }

    private static void assertFiltered(String expected, String input) throws IOException {
        assertEquals(expected, filter(new StringReader(input)), "in one piece");
        assertEquals(expected, filter(new OneCharReader(input)), "one character at a time");
    }

    @Test
    void removesDeclarationsEverywhere() throws IOException {
        assertFiltered("<a/>\n<b/>", "<?xml version='1.0'?><a/>\n<?xml version=\"1.0\" encoding=\"UTF-8\"?><b/>");
    }

    @Test
    void isCaseInsensitive() throws IOException {
        assertFiltered("<a/>", "<?XML version='1.0'?><a/>");
    }

    @Test
    void keepsEverythingElse() throws IOException {
        assertFiltered("<x a='?'>1 < 2 ?> <?x</x><<a/>", "<x a='?'>1 < 2 ?> <?x</x><<?xml?><a/>");
        assertFiltered("<", "<");
        assertFiltered("<?xm", "<?xm");
        assertFiltered("", "");
    }

    @Test
    void handlesQuestionMarksInsideTheDeclaration() throws IOException {
        assertFiltered("<a/>", "<?xml version='1.0?' standalone='?'?><a/>");
    }
}
