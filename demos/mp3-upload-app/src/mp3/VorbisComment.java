/*
 * Copyright © 2008, Sun Microsystems, Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 *    * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 *    * Neither the name of Sun Microsystems, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */



package mp3;

import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharacterCodingException;

/**
 * Read an Ogg Vorbis file and extract the metadata
 * Mon Dec 19 19:42:15 PST 2005
 *
 * @author Shamim Mohamed
 */
public class VorbisComment {

    private static final int BUFSIZE = 2000;

    private File file = null;
    private Map comments = null;

    public VorbisComment(File f) {
        this.file = f;
    }

    public Map getComments() {
        init();
        return comments;
    }

    public String get(String name) {
        init();
        try {
            return (String) comments.get(name.toLowerCase());
        }
        catch (Exception ignored) {}

        return null;
    }

    private synchronized void init() {
        if (comments == null)
            parseComments(getCommentBuf(file.getAbsolutePath()));
    }

    private void parseComments(ByteBuffer buf) {
        comments = new HashMap();

        int pos = buf.position();

        // Strange extra 7 bytes: 0x3 "vorbis" XXX
        buf.position(buf.position() + 7);

        // The buffer is pointing to a vector of strings, each [except
        // the first] of the form name=value. Each string is preceded
        // by its length (32 bits) in bytes. The first string is the
        // vendor string.

        String s = getString(buf); // vendor string
        int numComments = getInt(buf);

        for (int i = 0; i < numComments; i++) {
            if ((s = getString(buf)) == null)
                break;

            String[] words = s.split("=");
            comments.put(words[0].toLowerCase(), words[1]);
        }
    }

    private ByteBuffer getCommentBuf(String filename) {
        ByteBuffer buf = null;

        try {
            buf = readF(filename);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        if (buf == null)
            return null;
        
        // Skip the first header
        getPage(buf);

        return getPage(buf);
    }

    private ByteBuffer getPage(ByteBuffer buf) {
        int pos = buf.position();

        if (!getString(buf, 4).equals("OggS"))
            throw new RuntimeException("At buffer position " +
                                       buf.position() + ": not a page");
        
        // Read length of header: seek 2 bytes and read 1 byte. This
        // is the number of segments. Read that many more bytes, those
        // are segment lengths. Add them all up, that's the size of
        // the payload in the page. Read that many bytes and return
        // those in a bytebuffer; the original byte buffer is now
        // pointing at the next page.

        buf.position(buf.position() + 22);

        int numSeg = getUint8(buf.get());
        
        String l = "    " + numSeg + " segment(s) {";

        int pLen = 0;
        for (int i = 0; i < numSeg; i++) {
            int len = getUint8(buf.get());
            pLen += len;
            l += " " + len;
        }

        if (pLen > buf.remaining())
            pLen = buf.remaining();

        return slice(buf, pLen);
    }

    private static ByteBuffer readF(String filename) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(BUFSIZE);
        RandomAccessFile file = new RandomAccessFile(filename, "r");
        FileChannel channel = file.getChannel();

        channel.read(buf);
        channel.close();

        buf.flip();
        return buf;
    }

    private String getString(ByteBuffer buf) {
        int size = getInt(buf);
        return getString(buf, size);
    }

    private String getString(ByteBuffer buf, int size) {
        if (size > buf.remaining())
            size = buf.remaining();

        ByteBuffer strBytes = slice(buf, size);
        String sbuf = print(strBytes);
        String ret = Charset.forName("UTF-8").decode(strBytes).toString();

        return ret;
    }

    private ByteBuffer slice(ByteBuffer src, int length) {
        if (length > src.remaining())
            length = src.remaining();

        ByteBuffer subBytes = src.slice();
        src.position(src.position() + length);
        subBytes.limit(length);
        return subBytes;
    }

    private int getUint8(byte b) {
        return b & 0xff;
    }

    private int getInt(ByteBuffer buf) {
        int ret = 0;
        for (int i = 0; i < 4; i++) 
            ret |= (buf.get() & 0xff);
        return ret;
    }

    private String print(ByteBuffer b) {
        b.mark();

        String s = "";
        String trailer = "";

        int n = b.remaining();
        if (n > 32) {
            n = 32;
            trailer = "...";
        }

        for (int i = 0; i < n; i++)
            s += hex(b.get());

        b.reset();
        return s + trailer;
    }
    private String hex(byte b) {
        String s = Long.toHexString(getUint8(b));
        if (s.length() < 2)
            s = "0" + s;
        return s;
    }

    public static void main(String[] args) {
        for (int j = 0; j < args.length; j++)
            try {
                File f = new File(args[j]);
                Map comments = new VorbisComment(f).getComments();
                System.out.println(f.getAbsolutePath() + ":");

                for (Iterator i = comments.keySet().iterator(); i.hasNext();){
                    String name = (String) i.next();
                    String value = (String) comments.get(name);
                    System.out.println("    \"" +
                                       name + "\" = \"" + value + "\"");
                }
                System.out.println("");
            } catch (Exception e) {
                e.printStackTrace();
            }
    }
}
