package com.sun.honeycomb.util;

import java.net.ServerSocket;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.Socket;


/**
 * Use this class as a forwarding proxy between your honeycomb client 
 * and server and it will echo the communication to standard output.
 *
 * How to use: 
 * java com.sun.honeycomb.util.TraceProxy cluster-data proxy-port
 *
 * With  the proxy running, connect to it from the client just as if 
 * it were the cluster's data vip
 */
public class TraceProxy{


    static class PortTracer extends Thread {
        private int port;
        private String cluster;
        private int clusterPort;
        private String label;

        PortTracer (int port, String cluster, int clusterPort, String label){
            this.port = port;
            this.cluster = cluster;
            this.clusterPort = clusterPort;
            this.label = label;
        }

	public void run() {

	    try {
		ServerSocket ss = new ServerSocket(port);
		for (;;){
		    try{
			TraceProxy proxy = new TraceProxy(ss.accept(), new Socket(cluster, clusterPort), label);
			proxy.run();
		    }
		    catch (java.net.ConnectException ce){
			System.out.println("port " + clusterPort + " " + ce);
		    }
		
                }
            }
            catch (Exception e){
                System.err.println("Error listening on port " + port);
                e.printStackTrace();
            }
        }
    }

    public static void main (String[] argv) throws Exception{
	if (argv.length == 0){
	    System.out.println("Usage: java TraceProxy clusterDataVIP localProxyPort");
	}
	else {
	    int port = (argv.length > 1) ? Integer.parseInt( argv[1]) : 8082;
	    int clusterPort = (argv.length > 2) ? Integer.parseInt( argv[2]) : 8080;
	    String host = argv[0];
            System.err.println ("Proxy for " + host + ": " + clusterPort + 
                                ", listening on port " + port + 
                                " (and " + (port-1) + " for power-of-two)");
            new PortTracer(port, host, clusterPort, "").start();
            new PortTracer(port-1, host, clusterPort-1, "PowerOfTwo").start();
        }
    }

    private Socket client;
    private Socket server;
    private String label;

    private TraceProxy(Socket client, Socket server, String label) throws java.net.SocketException{
	System.err.println("Accepted " + client);
	server.setKeepAlive(true);
	client.setKeepAlive(true);
	this.client = client;
	this.server = server;
	this.label = label;
    }

    public void run() {
	try{
	    new copier(label + " Client", client.getInputStream(), server.getOutputStream()).start();
	    new copier(label + " Server", server.getInputStream(), client.getOutputStream()).start();
	    // 	    new monitor(server).start();
// 	    new monitor(client).start();
	}
	catch (java.net.ConnectException ce){
	    System.out.println(label + " " + ce);
	}
	catch (IOException ioe){
	    System.out.println(ioe);
	}
    }

    private static String current = null;


    class monitor extends Thread {
	Socket s;
	monitor (Socket s){ this.s=s;}
	public void run() {
	    try{
		for(;;){
		    Thread.sleep(1000);
		    System.out.println(s + ": " + s.isOutputShutdown()) ;
		}
	    }
	    catch (Exception e){ System.out.println(e);}
	}
    }

    class copier extends Thread {
	String name;
	InputStream in; 
	OutputStream out;
	
	copier(String name, InputStream in, OutputStream out){
	    this.name = name;
	    this.in = in;
	    this.out = out;
	}

	public void run() {
	    try{
		int c;
		while ((c = in.read()) != -1){
		    if (!name.equals(current)){
			// Grab semaphore if the other diection was the last to write output...
			current = name;
			System.out.println("\n" + name);
		    }
            if ((c > 31 && c < 127) || 
                c == (int)'\r' || c== (int)'\n')  {
                System.out.print((char)c);
            } else {
                System.out.print("\\");
                String hex = "00" + Integer.toHexString(c);
                System.out.print(hex.substring(hex.length() - 2, 
                                               hex.length()));

            }
		    out.write(c);
		}
	    }
	    catch (Throwable ioe){
		System.out.println(ioe);
	    }
            try{in.close();}catch (IOException ioe){}
            try{out.close();}catch (IOException ioe){}
	    System.out.print("\n" + name + " finished\n");
	}
    }

}


