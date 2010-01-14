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



import java.io.*;
import java.util.*;
import java.lang.*;
import java.text.*;
import java.util.regex.*;

public class LoadSpreaderSimulator {
    static final int PORT1= 8080; 
    static final int PORT2 = 7979;
    static final int mask = 0x1;
    static final int nodeIdx = 3; // fixed value for now as a 16 node cluster 2 bits for host & 2 bits for port 
    static int nodes = 0; 
    static int down = 0;
 
    public static int getSrcHost (String srcAddr) { 
       //System.out.println ("srcAddr " +srcAddr); 
       int lastOctet=0 ;
       try { 
          String[] tokens = srcAddr.split ("\\.");
       //for (int i=0; i<tokens.length; i++)  
       //   System.out.println ("IPAddr tokens: " +tokens[i]);
       lastOctet = Integer.parseInt (tokens[3].trim());
       } catch (PatternSyntaxException e) {
          System.err.println ("token split exception: " +e.getMessage());
          System.exit (1);
       } 
       return (lastOctet & 0x3); 
    }
    
    public static int getSrcPort (String srcPort) {
       return (Integer.parseInt(srcPort) & 0x3); 
    }

    public static int getSwitchPort (int srcHost, int srcPort, int destPort) { 
       int idx = (int) (Math.log((double)nodes)/Math.log(2.0));
       StringBuffer sb = new StringBuffer (idx); 
       String hostStr = new String (Integer.toBinaryString(srcHost));
       String portStr = new String (Integer.toBinaryString(srcPort));
       
       switch (idx) {
          case 2:
           srcHost &= 0x1;
           sb.append (Integer.toBinaryString(srcHost));
           srcPort &= 0x1; 
           sb.append (Integer.toBinaryString(srcPort));
           break; 
          case 3:
           if (hostStr.length() == 1) { 
              sb.append ("0"); 
              sb.append (hostStr);
           }
           else 
              sb.append (hostStr); 
           srcPort &= 0x1;  
           portStr = Integer.toBinaryString(srcPort);
           sb.append (portStr);
           break; 
          case 4: 
           if (hostStr.length() == 1) { 
              sb.append ("0"); 
              sb.append (hostStr);
           }
           else 
              sb.append (hostStr); 
           if (portStr.length() == 1) { 
              sb.append ("0"); 
              sb.append (portStr);
           }
           else 
              sb.append (portStr); 
           break; 
          default: System.err.println ("Cluster nodes incorrect, specify [4,8,16]"); System.exit(1); 
       }
       return (Integer.parseInt (sb.toString(),2)); 
    }

    /** 
  
     */     
    public static void createSwitchPortList (String iFile, String oFile)
     throws IOException { 
       PrintWriter out = null;
       BufferedReader in = null;
 
       try {   
          out = new PrintWriter (new FileWriter (oFile));
       } catch (IOException e) {
          System.err.println ("ioexception: " +e.getMessage()); 
          System.exit (1);
       }
       try {
         in = new BufferedReader (new FileReader (iFile));  
       } catch (IOException e) {
          System.err.println ("ioexception: " +e.getMessage()); 
          System.exit (1);
       }
      
       String line = null; 
       String[] tokens = new String[4]; 
       while ((line=in.readLine()) != null) { 
         try {
            tokens = line.split(":"); 
         //for (int i=0; i<tokens.length; i++) 
         //   System.out.println ("ifile tokens: " +tokens[i]); 
         } catch (PatternSyntaxException e) {
            System.err.println ("Check " +iFile +" file for correct arguments");
            System.err.println ("Format =><IP>:<SRC PORT>:[DEST PORT]"); 
            System.err.println ("patternmatchexception: " +e.getMessage());
            System.exit (1);
         } 
       String ip = new String (tokens[0].trim());
       String port = new String (tokens[1].trim()); 
       int srcHost = getSrcHost (ip);
       int srcPort = getSrcPort (port); 
       //System.out.println ("source Port :" +srcPort);
       int destPort = tokens.length == 2 ? 8080 : Integer.parseInt (tokens[2].trim());
       int switchPort = getSwitchPort (srcHost, srcPort, destPort);
       int node = switchPort+1+100;
       out.println (ip +" " +port +" " +destPort +" " +node);
      }
      out.close();
      in.close (); 
    }
  
    /** in: rFile - Filename where rules will be created 
     *  in: nodes - total nodes in the cluster
     *  in: nodesDown - # of nodes down in the cluster   
     */
    public static void createRulesFile (String rFile, int nodes, int down) 
     throws IOException {
       PrintWriter out = null; 
       try {   
          out = new PrintWriter (new FileWriter (rFile));
       } catch (IOException e) {
          System.out.println ("error: " +e.getMessage());
       }
 
       int totalRules = 2*nodes; 
       HashMap<Integer, ArrayList> ruleMap = new HashMap<Integer, ArrayList> ();
       for (int i=1,j=0,k=0,node=1; i<=totalRules; i+=2,j=0,k=0,node++) {  
         ArrayList<String> list1 = new ArrayList<String> (); 
         list1.add (j++, new String (Integer.toString (mask))); 
         list1.add (j++, new String (Integer.toBinaryString ((node-1)))); 
         list1.add (j++, new String (Integer.toString (PORT1))); 
         list1.add (j++, new String (Integer.toString (node))); 
         ruleMap.put (i, list1);
 
         ArrayList<String> list2 = new ArrayList<String> (); 
         list2.add (k++, new String (Integer.toString (mask))); 
         list2.add (k++, new String (Integer.toBinaryString ((node-1)))); 
         list2.add (k++, new String (Integer.toString (PORT2))); 
         list2.add (k++, new String (Integer.toString (node))); 
         ruleMap.put (i+1, list2); 
       } 

       // Pick Random down nodes
       if (down>0) { 
          int[] nodesDown = new int[down];
          Random random = new Random (System.currentTimeMillis()); 
          for (int i=0; i<down; i++) {
             nodesDown[i] = random.nextInt(nodes-1) + 1;         
             System.out.print (" " +nodesDown[i]); 
          }

          System.out.println (); 
          // remove nodes from the ruleMap 
          for (int k=0; k<2; k++) { 
          for (int i=0; i<down; i++) {
             Iterator itr = ruleMap.keySet().iterator(); 
             while (itr.hasNext()) {  
                Integer key = (Integer) itr.next(); 
                ArrayList t = (ArrayList) ruleMap.get(key); 
                if (Integer.parseInt ((String)t.get(nodeIdx)) == nodesDown[i]) { 
                   ruleMap.remove (key); 
                   break;
                } 
             } 
          } 
          }
       }
 
       out.println ("[Mask, Src Addr+Port bits, DestPort, SwitchPort]"); 
       for (Iterator itr = ruleMap.keySet().iterator(); itr.hasNext();) {  
          ArrayList t = (ArrayList) ruleMap.get(itr.next());
          out.println (t); 
       }
  
       out.close();
    }
     
    public static void main (String[] args)
     throws IOException {
      if (args.length < 4) {
         System.out.println ("Usage: LoadSpreaderSimulator <rules File>"); 
         System.out.println ("                             <input File>");
         System.out.println ("                             <out File>");
         System.out.println ("                             <no. of nodes>");
         System.out.println ("                             [nodes that are DOWN]");
         System.exit (1);
      }
      String rFile = args[0];
      String iFile = args[1];
      String oFile = args[2];

      nodes = Integer.parseInt (args[3]); 
      down = (args.length == 5) ? Integer.parseInt(args[4]): 0;

      createRulesFile(rFile, nodes , down);
      createSwitchPortList(iFile, oFile); 
   }
}


