package com.sun.honeycomb.testcmd.cmd;

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



import com.sun.honeycomb.testcmd.common.HoneycombTestCmd;
import com.sun.honeycomb.testcmd.common.HoneycombTestException;

//import com.sun.honeycomb.errsrv.common.*;
import com.sun.honeycomb.errsrv.clnt.*;

import java.util.*;
import java.io.*;

public class CorruptFragments extends HoneycombTestCmd {

    boolean debuggingEnabled = false;
    private ErrInjectorClnt eclnt = null;

    // filesizes
    final long small_sizes[] = {
                        1,
                        getBlockSize(), // 64k * n
                        getBlockSize()+1,
                        getBlockSize()-1,
                        getBlockSize() * 64
                };

    final long big_sizes[] = {
                        1024 * 1024 * 1024, // 1G
                        1024 * 1024 * 1024 + 1
                };

    String host = null;
    long extra_sizes[] = null;

    boolean small = false;
    boolean big = false;
    boolean extra = false;
    int metadata = 0;
    boolean delete = false;
    boolean delete2 = false;
    boolean restore = false;
    boolean restore2 = false;
    boolean wait = false;
    ArrayList events = new ArrayList();

    private void usage() {
        System.err.println("Usage: java CorruptFragments -h data-vip [-S] [-B] [-A] [-bytes <size1> <size2> ..] [-rRw] -f <corruptScript> -P");
        System.err.println("");
        System.err.println("    -h:      host ip or name");
        System.err.println("");
        System.err.println("File size(s): >= 1 required:");
        System.err.println("    -S:      1..blocksize*64  (" + small_sizes.length +
							" cases, ~5 min)");
        System.err.println("    -B:      ~1G  (" + big_sizes.length + " cases, ~60 min)");
        System.err.println("    -A:      {S+B}");
        System.err.println("    -bytes:  1 2k 3M 100g 2f 30b 5E ..");
        System.err.println("             [k, m, g = kbytes, mbytes, gbytes]");
        System.err.println("             [f, b, e = fragsz, blocksz, extentsz]");
        System.err.println("");
        System.err.println("Behavior (foreach file, foreach corruption;");
        System.err.println("default -r):");
        System.err.println("    -r       store, corrupt, retrieve");
        System.err.println("    -R       store, retrieve, corrupt, retrieve");
        System.err.println("    -w       store/corrupt all, watch until fixed/deleted");
        System.err.println("");
        System.err.println("Repeating sections of:");
        System.err.println("    -m:      hex bitmask (0xff = flip all bits)");
        System.err.println("             or 'random'");
        System.err.println("    -t:      truncate at (% ok)");
        System.err.println("             [non-m/t default is delete frag(s)]");
        System.err.println("    -f       fragment numbers or");
        System.err.println("             M = all parity, N = all data");
        System.err.println("");
        System.err.println("Summary:");
        System.err.println("    -P:      print all sizes/actions and relevant");
        System.err.println("             cluster config, and exit");
        System.err.println("");
        System.exit(1);
    }

    public static void main(String args[]) {
        new CorruptFragments(args);
    }
        
    public CorruptFragments(String args[]) {

        parseArgs(args);

        initHCClient(host);
        initErrSrvClient(host);
/*
        if (small)
            doSizes(small_sizes);
        if (big)
            doSizes(big_sizes);
        if (extra)
            doSizes(extra_sizes);
*/
    }

    private void parseArgs(String args[]) {

        if (args.length == 0) {
            usage();
        }
        boolean gotFileArg = false;
        ArrayList extras = new ArrayList();

        boolean print = false;

        for (int i=0; i<args.length; i++) {

            if (args[i].equals("-h")) {
                if (i+1 >= args.length)
                    usage();
                // trim in case someone uses quotes :-)
                host = args[i+1].trim(); 
                i++;
            } else if (args[i].equals("-S")) {
                small = true;
            } else if (args[i].equals("-B")) {
                big = true;
            } else if (args[i].equals("-A")) {
                small = true;
                big = true;
            } else if (args[i].equals("-bytes")) {
                extra = true;
                int j;
                for (j=i+1; j<args.length; j++) {
		    try {
		        long lsize = parseSize(args[j].trim());
			if (lsize < 1) {
                            System.err.println("Unexpected: " + args[j]);
                            usage();
			}
                        extras.add(new Long(lsize));
                    } catch (NumberFormatException nfe) {
                        break;
                    }
                }
                i = j - 1;
            } else if (args[i].equals("-r")) {
                restore = true;
            } else if (args[i].equals("-R")) {
                restore2 = true;
            } else if (args[i].equals("-w")) {
                wait = true;
            } else if (args[i].equals("-f")) {
                if (gotFileArg) {
                    System.err.println("Only 1 -f allowed");
                    usage();
                }
                gotFileArg = true;
                if (i == args.length - 1) {
                    System.err.println("-f: expected 'random' or filename");
                    usage();
                }
                i++;
                parseFile(args[i]);
/*
                String arg = args[i];
                if (arg.equals("random")) {
                    randomBits = true;
                } else {
                    parseFile(arg);
                }
*/
            } else if (args[i].equals("-P")) {
                print = true;
            } else {
                System.err.println("Unexpected: " + args[i]);
                usage();
            }
        }

        if (extra) {
            if (extras.size() == 0) {
                System.err.println("-bytes: must give number(s)");
                usage();
            }
            extra_sizes = new long[extras.size()];
            for (int i=0; i<extras.size(); i++) {
                Long l = (Long) extras.get(i);
                extra_sizes[i] = l.longValue();
            }
        }
        if (print) {
            StringBuffer sb = new StringBuffer();
            if (small) {
                sb.append("small:  ");
                for (int i=0; i<small_sizes.length; i++) {
                    if (i > 0)
                        sb.append(", ");
                    sb.append(small_sizes[i]);
                }
                sb.append("\n");
            }
            if (big) {
                sb.append("big:    ");
                for (int i=0; i<big_sizes.length; i++) {
                    if (i > 0)
                        sb.append(", ");
                    sb.append(big_sizes[i]);
                }
                sb.append("\n");
            }
            if (extra) {
                sb.append("custom: ");
                for (int i=0; i<extra_sizes.length; i++) {
                    if (i > 0)
                        sb.append(", ");
                    sb.append(extra_sizes[i]);
                }
            }
	    System.out.println("sizes:");
            System.out.println(sb);
            describeTests();
            System.exit(0);
        }
        if (host == null) {
            System.err.println("Need -h");
            usage();
        }
    }

    private void parseError(String err) {
        System.out.println("Parsing file: " + err);
        System.exit(1);
    }
    private void parseFile(String fname) {
        LineReader lr = null;
        try {
            lr = new LineReader(fname);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            usage();
        }
        String line;
        while ((line = lr.getline()) != null) {
            if (!line.equals("event"))
                parseError("expected 'event'");
            TestEvent event = new TestEvent(lr);
            if (event.error != null)
                parseError("event: " + event.error);
            events.add(event);
        }
    } 
    void describeTests() {
        for (int i=0; i<events.size(); i++)
	    System.out.println(((TestEvent)events.get(i)).toString());
    }
    class TestEvent {
        String error = null;
        ArrayList actions = new ArrayList();

        TestEvent(LineReader lr) {
            String line;
            while ((line = lr.getline()) != null) {
                if (line.equals("/event")) {
                    // XXX check event
                    break;
                }
                Action a = new Action(line, lr);
                if (a.error != null) {
                    error = "'" + line + "': " + a.error;
                    return;
                }
                actions.add(a);
            }
            if (line == null)
                error = "unclosed 'event'";
        }
	public String toString() {
	    StringBuffer sb = new StringBuffer();
	    sb.append("TestEvent:\n");
	    for (int i=0; i<actions.size(); i++) {
	        sb.append(((Action)actions.get(i)).toString());
	    }
	    return sb.toString();
	}
    }
    class Action {
        String error = null;
        static final int DELETE = 1;
        static final int CORRUPT = 2;
        static final int TRUNC = 3;
        int type;
	StringBuffer desc = new StringBuffer();
        int frags[];
	int blocks[];
	boolean md = false;
	boolean percent; // for TRUNC length
        byte mask;
        long bytes[];
        long len;

        Action(String line, LineReader lr) {
            // System.out.println("line: [" + line + "]");
            String[] ss = line.split(" ");

	    if (!ss[0].equals("action")  ||  ss.length < 2) {
                error = "action: expected 'action' plus 'delete' 'corrupt' or 'trunc': " + 
								line;
                return;
	    }
	    if (ss.length > 2) {
	        if (ss.length > 3) {
		    error = "action: expected action <action> [md]: " + line;
		    return;
		}
		if (ss[2].equals("md")) {
		    md = true;
		} else {
		    error = "action: expected action <action> [md]: " + line;
		    return;
		}
	    }
	    line = ss[1];
	    desc.append("\taction: ").append(line);
	    if (md)
	        desc.append(" md\n");
	    else
	        desc.append("\n");

            if (line.equals("delete")) {
                type = DELETE;
                if (getFrags(lr.getline()))
		    return;
            } else if (line.equals("corrupt")) {
                type = CORRUPT;
                if (getMask(lr.getline()))
                    return;
                if (getBytes(lr.getline()))
                    return;
                if (getFrags(lr.getline()))
		    return;
            } else if (line.equals("trunc")) {
                type = TRUNC;
                if (getLen(lr.getline()))
                    return;
                if (getFrags(lr.getline()))
		    return;
            } else {
                error = "action: expected ''delete' 'corrupt' or 'trunc': " + 
								line;
                return;
            }
            line = lr.getline();
            if (line == null  ||  !line.equals("/action")) {
                error = "unclosed 'action': " + line;
            }
        }
	public String toString() {
	    return desc.toString();
	}
        private boolean getFrags(String line) {
            if (line == null) {
                error = "expected 'frags'";
                return true;
            }
            ArrayList l = new ArrayList();
            ArrayList l2 = new ArrayList();
            try {
                String[] ss = line.split(" ");
                if (!ss[0].equals("frags")) {
                    error = "expected 'frags'";
                    return true;
                }
                if (ss.length < 2) {
                    error = "expected frag numbers";
                    return true;
                }
		desc.append("\t\tfrags: ");
                try {
                    for (int i=1; i<ss.length; i++) {
		        int frag = -1;
			int block = -1;
		        if (ss[i].indexOf(".") == -1) {
			    frag = Integer.parseInt(ss[i]);
			    block = 0;
			} else {
			    String sss[] = ss[i].split("\\.");
			    if (sss.length != 2) {
                                error = "expected frag or block.frag: " + ss[i];
                                return true;
			    }
			    block = Integer.parseInt(sss[0]);
			    frag = Integer.parseInt(sss[1]);
			}
                        if (frag < 0) {
                            error = "fragment numbers must be >= 0: " + ss[i];
                            return true;
                        }
		        if (block < 0) {
		            error = "block numbers must be >= 0: " + ss[i];
			    return true;
		        }
		        if (md  &&  block > 0) {
		            error = "md block numbers must be 0: " + ss[i];
			    return true;
		        }
                        l.add(new Integer(frag));
			l2.add(new Integer(block));
			desc.append(ss[i]).append(" ");
		    }
		    desc.append("\n");
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
                frags = new int[l.size()];
		blocks = new int[l.size()];
                for (int i=0; i<frags.length; i++) {
                    Integer ii = (Integer) l.get(i);
                    frags[i] = ii.intValue();
		    ii = (Integer) l2.get(i);
		    blocks[i] = ii.intValue();
                }
            } catch (Exception e) {
                error = "parsing fragments: " + e.getMessage();
		return true;
            }
	    return false;
        }
        private boolean getMask(String line) {
            if (line == null) {
                error = "expected 'mask'";
                return true;
            }
            try {
                String[] ss = line.split(" ");
                if (!ss[0].equals("mask")) {
                    error = "expected 'mask'";
                    return true;
                }
                if (ss.length != 2) {
                    error = "expected byte mask";
                    return true;
                }
		desc.append("\t\tmask: ").append(ss[1]).append("\n");
                Byte b = Byte.decode(ss[1]);
                mask = b.byteValue();
            } catch (Exception e) {
                error = "parsing byte mask: " + e.getMessage();
                return true;
            }
            return false;
        }
        private boolean getBytes(String line) {
            if (line == null) {
                error = "expected 'bytes'";
                return true;
            }
            ArrayList l = new ArrayList();
            try {
                String[] ss = line.split(" ");
                if (!ss[0].equals("bytes")) {
                    error = "expected 'bytes'";
                    return true;
                }
                if (ss.length < 2) {
                    error = "expected byte numbers";
                    return true;
                }
		desc.append("\t\tbytes: ");
                try {
                    for (int i=1; i<ss.length; i++) {
                        l.add(Long.valueOf(ss[i]));
			desc.append(ss[i]).append(" ");
		    }
		    desc.append("\n");
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
                bytes = new long[l.size()];
                for (int i=0; i<bytes.length; i++) {
                    Long ll = (Long) l.get(i);
                    bytes[i] = ll.longValue();
                    if (bytes[i] < 0) {
                        error = "byte numbers must be > 0";
                        return true;
                    }
                }
            } catch (Exception e) {
                error = "parsing byte numbers: " + e.getMessage();
                return true;
            }
            return false;
        }
        private boolean getLen(String line) {
            if (line == null) {
                error = "expected 'length'";
                return true;
            }
            try {
                String[] ss = line.split(" ");
                if (!ss[0].equals("length")) {
                    error = "expected 'length'";
                    return true;
                }
                if (ss.length != 2) {
                    error = "expected length";
                    return true;
                }
		desc.append("\t\tlength: ").append(ss[1]).append("\n");
		if (ss[1].endsWith("%")) {
		    percent = true;
		    ss[1] = ss[1].substring(0, ss[1].length()-1);
		}
                Long l = Long.decode(ss[1]);
                len = l.longValue();
                if (len < 0) {
                    error = "length cannot be < 0";
                    return true;
                }
            } catch (Exception e) {
                error = "parsing length: " + e.getMessage();
                return true;
            }
            return false;
        }
    }

    private void initErrSrvClient(String host) {
        try {
            eclnt = new ErrInjectorClnt(host);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void doSizes(long[] sizes) {

        long times[] = new long[2 * sizes.length];
        Arrays.fill(times, -1L);

        String oids[] = new String[sizes.length];
        String shas[] = new String[sizes.length];
        Arrays.fill(oids, null);

        for (int i=0; i<sizes.length; i++) {

            String[] names = null;
            String retrieved = null;

            String action = null;

            try {
                action = "create/store";
                names = createFileAndStore(sizes[i], true, metadata, null,
								times, i*2);
                if (delete) {
                    action = "delete";
                    delete(names[1]);
                    log.log("deleted object: retrieve should fail");
                }
                action = "retrieve";
                retrieved = retrieve(names[1], times, i*2+1);
                action = "compare";
                String cmp = shell.cmp(names[0], retrieved);
                if (cmp != null) {
                    log.log("Comparing files: " + cmp);
		}
		// prepare for fragment ops
		List md_frags = eclnt.getFragmentPaths(names[1], false);
		String data_oid = eclnt.getDataOID(names[1]);
		List data_frags =  eclnt.getFragmentPaths(data_oid, true);
//%%
                if (delete2) {
                    action = "delete";
                    delete(names[1]);
                    log.log("deleted object: retrieve should fail");
                    action = "retrieve";
                    retrieved = retrieve(names[1], times, i*2+1);
                    log.log("Error: retrieve succeeded on deleted object");
                    cmp = shell.cmp(names[0], retrieved);
                    if (cmp == null) {
                        log.log("(Same data was retrieved)");
                    } else {
                        log.log("Different data retrieved: " + cmp);
                    }
                }
            } catch (HoneycombTestException e) {
                log.log(action + " problem: " + e.getMessage());
                //e.printStackTrace();
            } catch (Throwable t) {
                log.log(action + " got throwable");
                t.printStackTrace();
            }
            if (names != null) {
                deleteFile(names[0]);
                oids[i] = names[1];
		shas[i] = names[2];
            }
            deleteFile(retrieved);
        }
        log.log("size          store    retrv    oid");
        //       1234567890123456789012345678901234567890
        StringBuffer sb = new StringBuffer();
        for (int i=0; i<sizes.length; i++) {
            sb.setLength(0);
            sb.append(sizes[i]);
            while (sb.length() < 14)
                sb.append(' ');
            if (times[i*2] == -1) {
                sb.append("N/A");
            } else {
                sb.append(times[i*2]);

                while (sb.length() < 23)
                    sb.append(' ');
                if (times[i*2+1] == -1) {
                    sb.append("N/A");
                } else {
                    sb.append(times[i*2+1]);
                    if (oids[i] != null) {
                        while (sb.length() < 32)
                            sb.append(' ');
                        sb.append(oids[i]);
                    }
                }
            }
            log.log(sb.toString());
        }
    }
}
