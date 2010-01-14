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



package com.sun.honeycomb.spreader;

import java.io.*;
import java.util.*;

import com.sun.honeycomb.time.Time;
import com.sun.honeycomb.util.Exec;
import com.sun.honeycomb.util.HttpClient;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Gets and parses the IRULES table from the switch. Much ugliness trying
 * to parse the horribly formatted human-readable [ha!] output from the
 * utilities on the switch.
 *
 * There are lots of constants here that all depend on the fact that
 * we only do IPv4, so addresses are 32 bits. Obviously a move to IPv6
 * (should we be so rash as to contemplate it) will require much
 * changing and debugging.
 *
 * @author Shamim Mohamed
 * @version $Id: SwitchRules.java 10855 2007-05-19 02:54:08Z bberndt $
 */
public class SwitchRules {
    private HashMap ports;
    private int maxHVal;
    private int maxPVal;

    private FSel[] fsels = null;

    private Map netMaskBits = null;   // for ACCEPT rules: index is fSel

    private int numMasks = 0;
    private int numRules = 0;

    private List rules = null;

    private String irules = null;
    private String imasks = null;

    private static final String iruleBanner =
        "i Mtr Action Ctr Vlan CTag DSCP TOS PRI EC RPORT O_Act ODSCP " +
        "OE UTg EPORT PFMT FSel";
    private static final String imaskBanner =
        "i  RST RSI  DATA_OFFSETS 1-8   NACT DSCP TOS_P PRI RPORT       " +
        "UTg EPORT PFMT";

    private static final Logger logger =
        Logger.getLogger(SwitchRules.class.getName());

    /**
     * The various rules in the IRULES table of the switch.  (Reads
     * the IMASK table too, for offsets into IRULES.)
     *
     * @param maxHashVal the largest src addr hash value
     */
    SwitchRules(int maxHVal, int maxPVal) throws MalformedOutputException {
        this.maxHVal = maxHVal;
        this.maxPVal = maxPVal;

        rules = new LinkedList();

        irules = imasks = "";

        readIMaskTable();
        readIRuleTable();

        if (logger.isLoggable(Level.FINE))
            Collections.sort(rules);

        // If a dump is required for debugging....
        if (logger.isLoggable(Level.FINE)) {
            dumpRules(Level.FINE);
            if (logger.isLoggable(Level.FINER))
                dumpOutput(Level.FINER);
        }
    }

    /**
     * Returns the rules currently on the switch
     *
     * @return all the rules (as a {@link LinkedList} of {@link Rule}s)
     */
    List getRules() {
        return rules;
    }

    /**
     * Returns the rules currently on the switch. Each rule is
     * converted to a string in a way suitable for zrule.
     *
     * @return all the rules (as a {@link LinkedList} of Strings
     */
    List getRulesAsStrings() {
        LinkedList s = new LinkedList();
        for (Iterator i = rules.iterator(); i.hasNext(); )
            s.add(((Rule)i.next()).toString());
        return s;
    }

    /***********************************************************
     * Useful for unit tests, since this method can be overridden to
     * read from pickled zirule output.
     **/
    protected BufferedReader getIRuleReader() throws MalformedOutputException {
        String url = "http://10.123.45.1/http/cgi-bin/zirule";
        BufferedReader f;
        try {
            f = HttpClient.getHttp(url, logger);
        } catch (IOException e) {
            logger.severe("Reading zirule http output: " + e);
            throw new MalformedOutputException("\"" + url + "\": " + e);
        }
        if (f == null) {
            throw new RuntimeException("Couldn't GET " + url);
        }
        return f;
    }

    /**
     * Reads the IRULES table from the switch and initializes <tt>rules</tt>
     *
     * @throws MalformedOutputException on error output from switch
     */
    private void readIRuleTable() throws MalformedOutputException {
        int lineNo = 0;
        rules = new LinkedList();

        // zirule exhibits a particularly deranged bit of brain
        // damage: the goddamn thing splits lines into two pieces
        // each around 60 chars. Not just that, the header -- which
        // repeats at frequent intervals -- is followed by a line of
        // dashes. With all the 26 fields forming a big mess it's not
        // fit for man or beast. We try to work around it the best we
        // can.

        try {
            BufferedReader f = getIRuleReader();

            String line1, line2;
            int ruleNo = 0;

            while ((line1 = f.readLine()) != null) {
                lineNo++;
                if (line1.startsWith("------"))
                    continue;
                if (line1.startsWith("Idx ")) { // it's a header
                    // Read one more line
                    if (f.readLine() == null)
                        throw new MalformedOutputException("EOF at line no. "+
                                                       lineNo);
                    continue;
                }

                if ((line2 = f.readLine()) == null)
                    throw new MalformedOutputException("EOF at line no. " +
                                                       lineNo);
                lineNo++;

                String line = line1.trim() + " " + line2.trim();

                irules += "\n" + line;

                Rule r = null;
                try {
                    r = parseIrule(line, lineNo, ruleNo);
                }
                catch (Exception e) {
                    logger.log(Level.WARNING,
                               "Bad rule " + ruleNo + ": \"" + line + "\"", e);
                }
                if (r != null) {
                    rules.add(r);
                    ruleNo++;
                }
            }
        }
        catch (IOException e) {
            logger.severe("Reading zirule http output: " + e);
            throw new MalformedOutputException("zirule output: " + e);
        }
        logger.info("Read " + lineNo + " zirule lines from the switch");
    }

    private Rule parseIrule(String line, int linesRead, int ruleNo)
        throws MalformedOutputException {

        if (ruleNo >= numRules) {
            logger.info("Ignoring rule " + ruleNo + " (only " + numRules +
                        " in the table)");
            return null;
        }

        /* Sample line:
0         1         2         3         4         5         6         7         8         9         10        11        12        13        14        15
0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789
Idx Mtr Action Ctr Vlan CTag DSCP TOS PRI EC RPORT O_Act ODSCP OE UTg EPORT PFMT  FSEL  Filter Data
  0   0   28a0   0    0 0000   0   0   0   0  0-10    0    0    0  0   0- 0   0    0  00000000 00000000 00000806 0a07e1ca 00000000 00000000 00010000 00000000
  1   0   28a0   1    0 0000   0   0   0   0  0- 1    0    0    0  0   0- 0   0    1  00000000 0a07e1ca 00001f90 00000006 08000000 00000000 00000000 00000000
  2   0   28a0   2    0 0000   0   0   0   0  0- 3    0    0    0  0   0- 0   0    1  00000000 0a07e1ca 00001f90 00000006 08000000 00000000 00000001 00000000
  3   0   28a0   3    0 0000   0   0   0   0  0- 0    0    0    0  0   0- 0   0    1  00000000 0a07e1ca 00011f90 00000006 08000000 00000000 00000000 00000000
  4   0   28a0   4    0 0000   0   0   0   0  0- 2    0    0    0  0   0-12   0    1  00000000 0a07e1ca 00011f90 00000006 08000000 00000000 00000001 00000000
  5   0   28a0  17    0 0000   0   0   0   0  0- 1    0    0    0  0   0- 0   0    2  00000000 00000000 00000806 0a07e02a 00000000 00000000 00010000 00000000
        */

        String[] fields = line.trim().split("[- \t]+");

        // (src and dest ports) and the seventh (hval).
        String action = fields[2];

        int ffp = Integer.parseInt(fields[10].substring(0, 1));
        int ffPort = Integer.parseInt(fields[11]);
        int oPort = ffp * 12 + ffPort;

        if ((oPort % 2) == 0) {
            oPort += 2;
        }

        if (action.equals("2088") || action.equals("28a8"))
            oPort = ZNetlink2Message.CPUPORT;
        else if (action.equals("90"))
            oPort = ZNetlink2Message.NULLPORT;

        int maskNo = Integer.parseInt(fields[19], 16);

        logger.fine("rule #" + ruleNo + ": action \"" + action + "\", oPort=" +
                    oPort + ", fSel=" + maskNo);

        String[] values = new String[8];
        for (int i = 0; i < values.length; i++)
            values[i] = fields[i + 20];

        // Fields:
        //     ARP -- v[3] is dest, v[2] is ethertype, v[6] is ARP opcode
        //     TCP -- v[1] is dest, v[2] is ports, v[3] is protocol,
        //            v[4] is ethertype, v[6] is src

        FSel fs = fsels[maskNo];
        if (fs == null) {
            logger.severe("Inconsistent table: no fSel " + maskNo);
            return null;
        }
        int ruleType = fs.type();
        if (ruleNo < fs.start() || ruleNo > fs.last()) {
            logger.warning("Rule " + ruleNo +
                           " (" + linesRead + " lines read) is outside " + fs);
            return null;
        }

        if (ruleType == FSel.ARP) {
            String dest = toDottedOctets(values[3]);
            return new Rule(dest, "arp_request", oPort);
        }

        String vSrc = values[6];
        String vSrcDestPorts = values[2];
        String vDest = values[1];
        String vProto = values[3];

        // Find the src & dest IP address of the redirect
        String dest = toDottedOctets(vDest);
        String src = toDottedOctets(vSrc);

        int n = 0;
        Integer nBits = (Integer) netMaskBits.get(new Integer(maskNo));
        if (nBits != null)
            n = nBits.intValue();
        if (n != 32)
            src += "/" + n;

        int destPort = Integer.parseInt(vSrcDestPorts.substring(4), 16);
        int srcPort = Integer.parseInt(vSrcDestPorts.substring(0, 4), 16);
        int srcAddr = Integer.parseInt(vSrc.substring(6), 16);
        int proto = Integer.parseInt(vProto.substring(6), 16);

        if (ruleType == FSel.DEFAULT && (destPort != 0 || srcAddr != 0)) {
            String msg = "destPort or srcAddr set for Default Rule";
            throw new MalformedOutputException(msg);
        }

        int hMask = 0, pMask = 0;
        if (ruleType == FSel.DATA) {
            hMask = maxHVal;
            pMask = maxPVal;
        }

        Rule newRule;

        switch (ruleType) {
        case FSel.DROP:
        case FSel.ACCEPT:
            newRule = new Rule(dest, oPort);
            break;

        case FSel.OUTBOUND:
            if(srcPort == Time.NTP_PORT) {
                newRule = new Rule(dest, srcPort, "udp", oPort);
            } else {
                newRule = new Rule(dest, srcPort, "tcp", oPort);
            }
            break;

        case FSel.DEFAULT:
            if (proto == 0x01)
                newRule = new Rule(dest, "icmp", oPort);
            else
                newRule = new Rule(dest, oPort);
            break;

        // Switch can be programmed either by port or by proto
        // here we return rule by port.
        // all data rules are added BY_PORT while NTP and NDMP OUTBOUND rules
        // are added BY_PROTO.
        case FSel.INBOUND:
            newRule = new Rule(dest, destPort, oPort);
            break;

        case FSel.ADMIN:
            oPort =  ZNetlink2Message.CPUPORT;
            /*FALLTHROUGH*/
        default:                // Fsel.DATA
            newRule = new Rule(dest, destPort, srcAddr, hMask,
                               srcPort, pMask, oPort);
        }
        return newRule;
    }

    /***********************************************************
     * Useful for unit tests, since this method can be overridden to
     * read from pickled zimask output.
     **/
    protected BufferedReader getIMaskReader() throws MalformedOutputException {
        String url = "http://10.123.45.1/http/cgi-bin/zimask";
        BufferedReader f;
        try {
            f = HttpClient.getHttp(url, logger);
        } catch (IOException e) {
            logger.severe("Reading zimask http output: " + e);
            throw new MalformedOutputException("\"" + url + "\": " + e);
        }
        if (f == null) {
            throw new RuntimeException("Couldn't GET " + url);
        }
        return f;
    }

    /**
     * Reads the IMASK table and gets the fSel values, as well as
     * start index for the fSel's rules in IRULES and the number of
     * rules it owns
     *
     * @throws MalformedOutputException on error in reading rules
     */
    private void readIMaskTable() throws MalformedOutputException {
        // zimask: also deranged, splits each line into two.

        int lineNo = 0;

        fsels = new FSel[FSel.NUM_TYPES];
        netMaskBits = new HashMap();

        try {
            BufferedReader f = getIMaskReader();
            String line1;
            int ruleNo = 0;

            while ((line1 = f.readLine()) != null) {
                lineNo++;
                if (line1.startsWith("------"))
                    continue;
                if (line1.startsWith("INDEX ")) { // it's a header
                    // Read one more line
                    if (f.readLine() == null)
                        throw new MalformedOutputException("EOF at line " +
                                                           lineNo);
                    continue;
                }

                String line2;
                if ((line2 = f.readLine()) == null)
                    throw new MalformedOutputException("EOF at line no. " +
                                                       lineNo);
                lineNo++;

                String line = line1.trim() + " " + line2.trim();

                imasks += "\n" + line;

                FSel fs = parseImask(lineNo, ruleNo, netMaskBits, line);
                if (fs != null)
                    fsels[ruleNo++] = fs;
            }
        }
        catch(IOException e) {
            logger.severe("Reading zimask http output: " + e);
            throw new MalformedOutputException("zimask output: " + e);
        }

        if (logger.isLoggable(Level.INFO)) {
            String msg = "Read " + lineNo + " zimask lines; ";
            for (int i = 0; i < fsels.length; i++)
                if (fsels[i] != null)
                    msg += " " + fsels[i];
            logger.info(msg);
        }
    }

    private FSel parseImask(int linesRead, int maskNo, Map netMaskBits,
                            String line)
        throws MalformedOutputException {

        /* Sample line (~160 chars):
0         1         2         3         4         5         6         7         8         9         10        11        12        13        14        15
123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789
I  RST RSI  DATA_OFFSETS 1-8   NACT DSCP TOS_P PRI RPORT       UTg EPORT PFMT
0    0   1  3 5 5 3 0 5 2 3    000    0    0    0   0- 0        0   0- 0   0 00000000 ffffffff 00000000 000000ff ffff0000 00000000 00000000 00000000
1    1  12  3 5 5 3 0 5 2 3    000    0    0    0   0- 0        0   0- 0   0 00000000 ffffffff 0001ffff 000000ff ffff0000 00000000 00000001 00000000
2   13   1  2 2 2 5 1 3 0 0    000    0    0    0   0- 0        0   0- 0   0 00000000 00000000 0000ffff ffffffff 00000000 00000000 ffff0000 00000000
        */

        String[] fields = line.trim().split("[- \t]+");

        int rStart = Integer.parseInt(fields[1]);
        int rSize = Integer.parseInt(fields[2]);

        numMasks++;
        if (rStart + rSize > numRules)
            numRules = rStart + rSize;

        String dValues = line.substring(12, 28).trim();

        if (dValues.equals("2 2 2 5 1 3 0 0")) {
            return new FSel(maskNo, FSel.ARP, rStart, rSize);
        }

        if (dValues.equals("3 5 5 3 0 5 2 3")) {

            String[] masks = new String[8];
            try {
                for (int i = 0; i < masks.length; i++)
                    masks[i] = fields[21 + i];
            }
            catch (ArrayIndexOutOfBoundsException e) {
                // Not enough fields in line
                String msg = "Check imask line " + linesRead + ": \"" +
                    line + "\"";
                throw new MalformedOutputException(msg);
            }

            // Masks: m1 is dest; m2 is ports; m3 is protocol type;
            // m4 is ethertype; m6 is src

            String srcMask = masks[6];
            String destMask = masks[1];
            String portMask = masks[2];
            String protoMask = masks[3];

            boolean src = !srcMask.equals("00000000");
            boolean dest = !destMask.equals("00000000");
            boolean destPort = !portMask.substring(4).equals("0000");
            boolean srcPort = !portMask.substring(0, 4).equals("0000");
            boolean proto = !protoMask.substring(6).equals("00");

            // NTP and NDMP OUTBOUND FSel
            if(!destPort && srcPort && proto) {
               return new FSel(maskNo, FSel.OUTBOUND, rStart, rSize); 
            }
            
            if (!srcPort && !destPort && !src && proto) {
                // We don't care about src or dest port, or src addr:
                // it's a default rule or a drop rule or an ICMP rule
                return new FSel(maskNo, FSel.DEFAULT, rStart, rSize);
            }

            if (!srcPort && !destPort && src && !proto) {
                // We don't care about src or dest ports, but we do
                // look at src addr: ACCEPT/DROP rules
                Integer nBits = new Integer(0);
                try {
                    nBits = getNumBits(srcMask);
                } catch (RuntimeException e) {
                    logger.severe("imask line " + linesRead +
                                  ": not a netmask: " + srcMask + e);
                }
                netMaskBits.put(new Integer(maskNo), nBits);
                return new FSel(maskNo, FSel.ACCEPT, rStart, rSize);
            }

            // The host address mask has to be maxHVal or, if
            // authorized_clients is being used (since netmasks start
            // on the left), starts with ff. (This is a heuristic and
            // is possibly dangerous.)
            if (srcMask.startsWith("ff") ||
                (Integer.parseInt(srcMask, 16) == maxHVal)) {

                int p = Integer.parseInt(portMask.substring(0, 4), 16);
                if (destPort && dest && (maxPVal == p))
                    // We look at dest port as well as dest addr, and src
                    // and srcport masks are as expected: this is for data
                    // rules
                    return new FSel(maskNo, FSel.DATA, rStart, rSize);
            }

            // NDMP INBOUND TRAFFIC
            if(!srcPort && destPort && proto) {
                return new FSel(maskNo, FSel.INBOUND, rStart, rSize); 
            }
        }

        logger.warning("Unable to parse \"" + line + "\"");
        return null;
    }


    private Integer getNumBits(String sMask) {
        // mask needs to be some number of 1s followed by some number of 0s
        if (sMask.length() > 8)
            throw new RuntimeException("Mask " + sMask + " too large");
        long mask = Long.parseLong(sMask, 16);
        if (mask == 0)
            return new Integer(0);

        int n;

        // shift right until the lsb is 1
        for (n = 0; mask % 2 == 0; n++)
            mask >>= 1;

        // Now it should be of the form 000....0111....1 i.e. 2^k - 1 where
        // k = 32 - n
        mask++;
        int k = 32 - n;
        if ((1L << k) != mask)
            throw new RuntimeException("Not a netmask: " + sMask + 
                                       " (after right shifting, we have 0x" +
                                       Long.toHexString(mask) + 
                                       ", which is not a power of 2)");
        logger.fine("Mask " + sMask + " => /" + k);
        return new Integer(k);
    }

    private String toDottedOctets(String s) {
        // Convert s into dotted octets
        String ret = "";
        for (int i = 0; i < 8; i += 2)
            ret += "." + Integer.parseInt(s.substring(i, i + 2), 16);
        return ret.substring(1);
    }

    /**
     * Prints out all the data structures to the log.
     */
    public void dumpRules(Level level) {

        logger.log(level, "Rules:");
        List r = getRulesAsStrings();
        for (Iterator i = r.iterator(); i.hasNext(); )
            logger.log(level, (String)i.next());

    }

    /**
     * Prints out raw output from the switch to the log
     */
    public void dumpOutput(Level level) {

        logger.log(level, "zimask output:\n" + imaskBanner + imasks);
        logger.log(level, "zirule output:\n" + iruleBanner + irules);
    }

    public static void main(String[] args) {
        SwitchRules sr = null;

        try {
            sr = new SwitchRules(1, 1);
        }
        catch (Exception e) {
            System.err.println("Exception " + e);
            System.exit(1);
        }
        List rules = sr.getRules();
        for (Iterator i = rules.iterator(); i.hasNext(); ) {
            Rule r = (Rule) i.next();
            System.out.println(r);
        }
    }

}
