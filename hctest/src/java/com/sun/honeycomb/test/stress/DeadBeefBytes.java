package com.sun.honeycomb.test.stress;

public class DeadBeefBytes
{
    private static final String PATTERN = "DEADBEEF BYTES! ";

    public static byte [] bytes;
    static {
        bytes = new byte[4*1024];
        byte [] pattern = PATTERN.getBytes();
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = pattern[i % pattern.length];
        }
    }
}
