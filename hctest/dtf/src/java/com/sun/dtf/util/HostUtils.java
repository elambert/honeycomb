package com.sun.dtf.util;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class HostUtils {

    public static String getHostname() {
        try {
            Enumeration networks = NetworkInterface.getNetworkInterfaces();
            InetAddress iAddress = null;
           
            /*
             * Find the first available interface that isn't the loopback one. 
             */
            while (networks.hasMoreElements()) { 
                NetworkInterface network = (NetworkInterface)networks.nextElement();
                
                Enumeration addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) { 
                    iAddress = (InetAddress) addresses.nextElement();
                    if (!iAddress.isLoopbackAddress() && 
                         iAddress.getHostAddress().indexOf(":") == -1)
                        return iAddress.getHostAddress();
                }
            }
        } catch (SocketException e) { }
       
        // default to localhost
        return "localhost";
    }
}
