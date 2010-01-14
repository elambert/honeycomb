package com.sun.dtf.cluster.snapshot;

public class SimpleLogger {
    public static void info(String message) { 
        System.out.println(message);
    }

    public static void error(String message) { 
        System.err.println(message);
    }

    public static void error(String message, Throwable t) { 
        System.err.println(message + ", cause:");
        t.printStackTrace(System.err);
    }
}
