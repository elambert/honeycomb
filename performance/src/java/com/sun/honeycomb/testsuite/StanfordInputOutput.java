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




import java.util.StringTokenizer;

public class StanfordInputOutput
{   
    public long threadID;
    public long t0;
    public long t1;
    public long id;
    public long numobj;
    public long bytes;
    public String op;
    public boolean ok;

    public static final String STORE = "STR";
    public static final String RETRIEVE = "RTV";
    public static final String QUERY = "QRY";

    public static final String OK = "OK";
    public static final String ERR = "ERR";

    /*ThreadId is used to discriminate entries from different threads */
    /*numobj is the number of objects being stored/retrieved, bytes is the total bytes being stored/retrieved*/
    /*id may be DOuuid or uuid, depends on what kind of id the operation aims to*/
   
    public static void printLine(long threadID, long t0, long t1, long id, long numobj, long bytes, String op, boolean ok)
    {
        System.out.println(threadID + " " + t0 + " " + t1 + " " + id + " "  + numobj + " " + bytes + " " + op + " " + (ok ? OK : ERR));
    } 


    public static StanfordInputOutput readLine(String s) throws Throwable
    {
        StanfordInputOutput line = new StanfordInputOutput();
        StringTokenizer st = new StringTokenizer(s);
        line.threadID = Long.parseLong(st.nextToken());
        line.t0 = Long.parseLong(st.nextToken());
        line.t1 = Long.parseLong(st.nextToken());
        line.id = Long.parseLong(st.nextToken());
        line.numobj = Long.parseLong(st.nextToken());
        line.bytes = Long.parseLong(st.nextToken());
        line.op = st.nextToken();
        line.ok = st.nextToken().equals("OK");
        return line;
    }
}
