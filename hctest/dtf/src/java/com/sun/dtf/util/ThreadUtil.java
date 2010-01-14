package com.sun.dtf.util;

public abstract class ThreadUtil {
    public static void pause(long millis) { 
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) { /* ignore */ }
    }
}
