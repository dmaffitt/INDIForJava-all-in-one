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

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;

/**
 * Removes xml declarations (&lt;?xml ...?&gt;) wherever they appear. Drivers
 * of the C INDI library write one before every message, but the protocol
 * stream is read as one xml document, where a declaration is only allowed at
 * the very start.
 *
 * @author Richard van Nieuwenhoven
 */
final class XmlDeclarationFilterReader extends FilterReader {

    /**
     * the start of an xml declaration, matched case insensitive.
     */
    private static final String DECLARATION = "<?xml";

    /**
     * the minimal number of characters to read from the underlying reader.
     */
    private static final int MIN_CHUNK = 256;

    /**
     * characters that may be the start of a declaration.
     */
    private final StringBuilder pending = new StringBuilder();

    /**
     * filtered characters ready to be returned.
     */
    private final StringBuilder ready = new StringBuilder();

    /**
     * true while skipping a declaration.
     */
    private boolean inDeclaration;

    /**
     * true when the previous skipped character was a question mark.
     */
    private boolean questionMark;

    /**
     * true when the underlying reader is at its end.
     */
    private boolean end;

    /**
     * @param in the reader to filter.
     */
    XmlDeclarationFilterReader(Reader in) {
        super(in);
    }

    @Override
    public int read(char[] buffer, int offset, int length) throws IOException {
        if (length == 0) {
            return 0;
        }
        while (ready.length() == 0) {
            if (end) {
                return -1;
            }
            char[] chunk = new char[Math.max(length, MIN_CHUNK)];
            int count = in.read(chunk, 0, chunk.length);
            if (count < 0) {
                end = true;
                ready.append(pending);
                pending.setLength(0);
            } else {
                for (int index = 0; index < count; index++) {
                    accept(chunk[index]);
                }
            }
        }
        int count = Math.min(length, ready.length());
        ready.getChars(0, count, buffer, offset);
        ready.delete(0, count);
        return count;
    }

    /**
     * filter one character.
     *
     * @param c the character.
     */
    private void accept(char c) {
        if (inDeclaration) {
            if (questionMark && c == '>') {
                inDeclaration = false;
            }
            questionMark = c == '?';
            return;
        }
        pending.append(c);
        int index = pending.length() - 1;
        if (Character.toLowerCase(c) == DECLARATION.charAt(index)) {
            if (pending.length() == DECLARATION.length()) {
                pending.setLength(0);
                inDeclaration = true;
                questionMark = false;
            }
            return;
        }
        // not a declaration, but a '<' may start the next one
        if (c == '<') {
            ready.append(pending, 0, index);
            pending.setLength(0);
            pending.append(c);
        } else {
            ready.append(pending);
            pending.setLength(0);
        }
    }

    @Override
    public int read() throws IOException {
        char[] one = new char[1];
        return read(one, 0, 1) < 0 ? -1 : one[0];
    }

    @Override
    public long skip(long n) throws IOException {
        char[] buffer = new char[(int) Math.min(n, MIN_CHUNK)];
        long skipped = 0;
        while (skipped < n) {
            int count = read(buffer, 0, (int) Math.min(buffer.length, n - skipped));
            if (count < 0) {
                break;
            }
            skipped += count;
        }
        return skipped;
    }

    @Override
    public boolean ready() throws IOException {
        return ready.length() > 0 || in.ready();
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public void mark(int readAheadLimit) throws IOException {
        throw new IOException("mark not supported");
    }

    @Override
    public void reset() throws IOException {
        throw new IOException("reset not supported");
    }
}
