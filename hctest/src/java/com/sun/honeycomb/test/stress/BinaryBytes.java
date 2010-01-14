package com.sun.honeycomb.test.stress;

import java.util.Random;

public class BinaryBytes
{
    public static byte [] bytes = null;
    static {
        bytes = new byte[1048583]; // prime-size near 1024*1024
        Random r = new Random(0);
        r.nextBytes(bytes);
    }

    public static void main(String [] args) {
        int [] counts = new int[256];
        for (int i=0; i < bytes.length; i++) {
            int b = (int) bytes[i];
            if (b < 0) b += 256;
            counts[b]++;
        }
        for (int j=0; j < counts.length; j++) {
            System.out.println(j + ": " + counts[j]);
        }
    }
    
}
