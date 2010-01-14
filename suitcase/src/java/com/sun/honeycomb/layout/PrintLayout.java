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




package com.sun.honeycomb.layout;
import java.text.NumberFormat;
import java.util.Set;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.ListIterator;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.layout.*;
import java.util.Date;
import java.util.regex.*;


//
// TODO: fix agument order booshite
// TODO: support < 16 nodes by listing as disabled disks
//
// Print the layout for given mapId, with optional failed disk list.
// Also displays the first few rows of the corresponding layout map,
// annotated to show which disks are being used in the layout.
class PrintLayout {
    public String startDate = null;
    public int numNodes = 16;
    public boolean printDiskList=false;
    public boolean printMapOnly=false;
    public boolean printAllLayouts=false;
    public boolean csvFormat=false;
    public String failedDiskList=new String("");
    public static boolean realCluster;
    private ArrayList useLayouts=null;

    private static String shortName =
        PrintLayout.class.getName().replaceAll("[A-Za-z_0-9]*[.]","");

    static final String USAGE =
        "Usage: "+shortName+" [-h] [-n numNodes] [-i] [-a] [-c] [-e] [-m] [-l filename] [mapId] [failedDiskList]\n"+
        "When run from a Honeycomb node, prints the current layout.\n"+
        "For testing, use failedDiskList to simulate DiskMask offline disks.\n"+
        "Example failedDiskList: 101:3  116:0\n" +
        "  -h this info\n" +
        "  -a dump all layouts (10,000 of them!)"+
        "  -n numNodes (defaults to 16) {currently not working}\n"+
        "  -i inspects and reports failed disk list\n" +
        "  -l loads a file of layout ids, and prints them. Disables mapId argument.\n" +
        "  -c prints in CSV format for database insertion\n" +
        "  -m prints the disk map, no layout\n" +
        " --- NOTE: due to programmer lazyness, arguments have to be in specifed order\n";                  


    public static void printUsage(String error) {
        if(error != null) {
            System.out.println(error+"\n");
        }
        System.out.println(USAGE);
        
    }
    public static void main(String[] args) {
        int offset=0;
        int layout=-1;
        PrintLayout layoutPrinter=new PrintLayout();

        if(args.length  < 1) {
            printUsage( null );
            System.exit(1);
        }
        
        if (args[offset].equals("-h")) {
            printUsage(null);
            System.exit(0);
        }


        if (offset < args.length &&args[offset].equals("-n")) {
            offset++;
            layoutPrinter.numNodes =  Integer.decode(args[offset]).intValue();
            layoutPrinter.setupNumNodes();
            offset++;
        }


        if (offset < args.length &&args[offset].equals("-i")) {
            offset++;            
            layoutPrinter.printDiskList=true;
        }


        if (offset < args.length && args[offset].equals("-a")) {
            offset++;
            layoutPrinter.printAllLayouts=true;
        }



        if (offset < args.length && args[offset].equals("-c")) {
            offset++;
            layoutPrinter.csvFormat=true;
        }
 

        if (offset < args.length && args[offset].equals("-m")) {
            offset++;
            layoutPrinter.printMapOnly=true;
        }

        if (offset < args.length && args[offset].equals("-l")) {
            offset++;
            layoutPrinter.loadLayoutList(args[offset]);
            offset++;
        }

        if (args[offset].equals("-d")) {
            offset++;
            layoutPrinter.startDate = args[offset];
            layoutPrinter.startDate += " ";
            offset++;
            layoutPrinter.startDate += args[offset];
            offset++;
            System.err.println("Got start date: " + layoutPrinter.startDate);
        }
        if(layoutPrinter.startDate==null && layoutPrinter.csvFormat==true) {
            System.err.println("Date must be set to use CSV output.");
            printUsage(null);
            System.exit(1);
        }


        if((!layoutPrinter.printAllLayouts) && (!layoutPrinter.printDiskList)) {
            if(offset >=args.length) {
                
                printUsage("Specify a layout map to print");
                System.exit(1);
            }
            if(layoutPrinter.useLayouts == null) {
                layout=Integer.decode(args[offset]).intValue();
                offset++;
            }
        }
        for (; offset < args.length; offset++) {
            layoutPrinter.failedDiskList += args[offset] + " ";
        }
        
        layoutPrinter.printLayout(layout);
        Date now = new Date();
        System.err.println("Done @" + now.toString());
    }

    public void setupNumNodes() {
        if(numNodes >= 16) 
            return;
        //failedDiskList
        for(int i=numNodes+100;i<=116;i++) {
            failedDiskList += " " + i+":0";
            failedDiskList += " " + i+":1";
            failedDiskList += " " + i+":2";
            failedDiskList += " " + i+":3";
        }
        System.out.println("failed disk list: " +failedDiskList);
    }

    public boolean isRealCluster() {
        boolean realCluster = true;
        String localhost = "";
        try {
            localhost = InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            System.out.println("Failed to get hostname!");
        }
        //
        // This is slightly cheezy. Maybe there's a better way.
        // we're dependent on the name including HCB. Could lie
        // if we were on some system that contained hcb as a
        // substring.
        //
        if (!localhost.regionMatches(0, "hcb", 0, 3)) {
            return false;
        } else {
            return true;
        }
    }


    public void loadLayoutList(String layoutFilename) {
        useLayouts = new ArrayList();
        boolean value;
        value=true;
        try {
            File layoutFile = new File (layoutFilename);
            BufferedReader bfr = new BufferedReader(new FileReader(layoutFile));

            String line;
            while ((line = bfr.readLine()) != null) {
                //                System.out.println("loading keyfile item:\"" +line+"\"");
                useLayouts.add(line);

            }
        } catch (Throwable t) {
            System.out.println("Error loading layout file: " + layoutFilename + " " + t.toString());
        }
                
    }


    public void printDiskList(DiskIdList failedList) {
        System.out.println("print disk list happens!");
        if( realCluster) {
            String failedDisks = LayoutProxy.getCurrentDiskMask().utOfflineDiskIds().toString();
            failedDisks = failedDisks.substring(1,failedDisks.length()-1);
            System.out.println(failedDisks);
            
        }else {
            String failedDisks = failedList.toString();
            failedDisks = failedDisks.substring(1,failedDisks.length()-1);
            System.out.println(failedDisks);
        }        
    }

    public void printLayout(int mapId) {
        realCluster = isRealCluster();        
        if(failedDiskList.length()> 0 && realCluster) {
            System.err.println("Can't specify a failed disk list if you're on a real cluster.");            
        }


        // create the simulated disk mask if we're not on a real cluster

        DiskMask mask = new DiskMask();
        DiskIdList failedList = null;
        if(!realCluster) {
            mask.utAllOnline();
            failedList = new DiskIdList(failedDiskList);
            for (int j=0; j < failedList.size(); j++) {
                mask.setOffline((DiskId)failedList.get(j));
            }
        }

        if(printDiskList) {
            printDiskList(failedList);
        } else if(printAllLayouts) {
            for(int i=0;i<10000;i++) {
                printSingleLayout(i,mask);
            }
        } else if(useLayouts!=null) {
            ListIterator li = useLayouts.listIterator();           
            while (li.hasNext()) {
                int next =  Integer.decode((String)li.next()).intValue();
                printSingleLayout(next,mask);
            }
        } else {            
            printSingleLayout(mapId,mask);
        }

    }

    void printSingleLayout (int mapId,DiskMask mask) {
        LayoutClient lc = LayoutClient.getInstance();
        // get layout, either based on given failures or real disk mask

        Layout layout;
        if (realCluster) {
            layout = lc.getLayoutForRetrieve(mapId);
        } else {
            layout = lc.utGetLayout(mapId, mask);
        }
        
        // show the layout map that generated this layout
        String output = new String("");
        if(!csvFormat)
            output  += mapId+" ";
        if(printMapOnly) {
            output += lc.mapToString(mapId, layout);
        } else {          
            String diskList=layout.toString();
            Pattern p = Pattern.compile("^.*\\[(.*)\\]$");
            Matcher m = p.matcher(diskList);
            if (m.matches()) {
                diskList = m.group(1);
            }
            else {
                throw new RuntimeException("unable to parse layout: \"" + layout + "\"");
            }
            output += diskList;
        }
        if(!csvFormat) {
            System.out.println(output);
        } else {
            Pattern p = Pattern.compile(" ");
            String [] splitStrings = p.split(output);
            for(int i=0;i<splitStrings.length;i++) {
                Pattern q = Pattern.compile(":");
                String [] nodeDisk = q.split(splitStrings[i]);                
                System.out.println(mapId+
                                   ","+
                                   ((Integer.decode(nodeDisk[0]).intValue())-100)+
                                   ","+
                                   nodeDisk[1]+
                                   ","+
                                   startDate);
            }
        }
            


    }

}
