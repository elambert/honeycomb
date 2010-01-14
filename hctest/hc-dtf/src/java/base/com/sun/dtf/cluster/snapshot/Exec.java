package com.sun.dtf.cluster.snapshot;

import java.io.IOException;
import java.io.InputStream;

public class Exec {

    public static int executeCmd(String cmd, boolean output, boolean errput)
            throws IOException, InterruptedException {
        Process p = Runtime.getRuntime().exec(cmd);
        int rc = p.waitFor();

        if (errput)
            SimpleLogger.error(toString(p.getErrorStream()));
        else
            consumeStream(p.getErrorStream());

        if (output) 
            SimpleLogger.info(toString(p.getInputStream()));
        else 
            consumeStream(p.getInputStream());

        return rc;
    }
    
    public static String executeCmdWithOutput(String cmd) 
                 throws IOException, InterruptedException{ 
        Process p = Runtime.getRuntime().exec(cmd);
        int rc = p.waitFor();
        consumeStream(p.getErrorStream());
        return toString(p.getInputStream());
    }
    
    public static int executeCmd(String cmd) throws IOException, InterruptedException {
        return executeCmd(cmd,true,true);
    }
    
    private static void consumeStream(InputStream in) throws IOException { 
        byte[] tmp = new byte[16 * 1024];
        int i = in.read(tmp, 0, tmp.length);
        while (i != -1) 
            i = in.read(tmp, 0, i);
    }

    private static String toString(InputStream in) throws IOException {
        StringBuffer result = new StringBuffer();
        byte[] tmp = new byte[16 * 1024];

        int i = in.read(tmp, 0, tmp.length);
        while (i != -1) {
            result.append(new String(tmp, 0, i));
            i = in.read(tmp, 0, tmp.length);
        }

        return result.toString();
    }
}
