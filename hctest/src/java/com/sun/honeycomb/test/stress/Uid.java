package com.sun.honeycomb.test.stress;

import java.net.InetAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Uid
{
    private static int numInstances = 0;
    private static String hostname;
    static {
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        }
        catch (Throwable t) {
            t.printStackTrace();
            System.err.println("error: unable to determine local hostname.");
            System.err.println("(abort)");
            System.exit(-1);
        }
    }

    public long instantiationTime;
    public int id;
    public long counter;
    public int channelPattern;

    public Uid () {
        this.instantiationTime = System.currentTimeMillis();
        synchronized (Uid.class) {
            this.id = numInstances++;
        }
        this.counter = 0;
        this.channelPattern = ChannelPatterns.DEADBEEF;
    }

    private static final Pattern uidRegex = Pattern.compile("^Stress-(.+)\\.([0-9]+)-([0-9]+)\\.([0-9]+)(\\.(binary|beef))?$");
    public Uid (String spec) throws Throwable {
        this();

        Matcher m = uidRegex.matcher(spec);
        if (!m.matches()) {
            throw new Throwable("invalid uid: " + spec);
        }
        else {
            this.hostname = m.group(1);
            this.id = Integer.parseInt(m.group(2));
            this.instantiationTime = Long.parseLong(m.group(3));
            this.counter = Long.parseLong(m.group(4));

            this.channelPattern = ChannelPatterns.DEADBEEF;
            String patternSpec = null;
            if ((patternSpec = m.group(6)) != null && patternSpec.equals("binary")) {
                this.channelPattern = ChannelPatterns.BINARY;
            }

        }
    }

    private static String channelPatternString(int pattern) {
        String s = null;
        switch (pattern) {
          case ChannelPatterns.DEADBEEF:
            s = "beef"; 
            break;
          case ChannelPatterns.BINARY:
            s = "binary";
            break;
          default:
        }
        return s;
    }

    public String toString() {
        return "Stress-" + hostname + "." + id + "-" + instantiationTime + "." + counter + "." + channelPatternString(channelPattern);
    }

    public void next() {
        counter++;
    }
    
    /* The store and retreive ops use a seed value to determine an offset
     * into the BinaryByte Array. We use this offset as the start point
     * from which to grab data out of the array.
     */
    public long generateSeed () {
	return Math.abs((instantiationTime + counter + 
		(long) id) % BinaryBytes.bytes.length);
    }

    public static void main(String [] argv) throws Throwable {
        Uid uid = new Uid(argv[0]);
        System.out.println("hostname: " + uid.hostname);
        System.out.println("instantiationTime: " + uid.instantiationTime);
        System.out.println("id: " + uid.id);
        System.out.println("counter: " + uid.counter);
        System.out.println("channelPattern: " + channelPatternString(uid.channelPattern));
    }

}
