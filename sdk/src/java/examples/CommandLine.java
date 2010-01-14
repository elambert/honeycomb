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
import java.util.StringTokenizer;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;


import com.sun.honeycomb.common.Getopt;


public class CommandLine{
 
    private int expectedArgs;
    private String [] arguments;
    private String[] parsedArgs;
    private Class root;
    private int count = 0;
    private int extraArgs = 0;
    private boolean variableArgs = false;
    private boolean helpMode = false;

    private HashMap switches = new HashMap();
    private HashMap switchValues = new HashMap();

    private final static Object NOVALUE = new Object();
    private final static Object ONEVALUE = new Object();
    private final static Object RECURRING = new Object();
    private final static Object RECURRING_NAME_VALUE = new Object();

    public boolean helpMode(){
        return helpMode;
    }

    public void acceptFlag (String name){
        acceptFlag (name, false);
    }
    public void acceptFlag (String name, boolean hasValue){
        acceptFlag (name, hasValue, false);
    }

    public void acceptFlag (String name, boolean hasValue, boolean canReccur){
        if (canReccur){
            if (hasValue)
                switches.put(name, RECURRING_NAME_VALUE);
            else
                switches.put(name, RECURRING);
        }
        else if (hasValue)
            switches.put(name, ONEVALUE);
        else
            switches.put(name, NOVALUE);
    }

    public String getOrderedArg(int n){
        return parsedArgs[n];
    }

    public boolean flagPresent (String flag){
        return switchValues.get(flag) != null;
    }

    public String getSingleValue (String flag){
        Object o = switchValues.get(flag);
        if (o == null)
            return null;
        else
            return (String) switchValues.get(flag);
    }

    public String[] getMultipleValues (String flag){
        Map map = (Map) switchValues.get(flag);
        String[] values = new String[map.size()];
        Iterator iter = map.keySet().iterator();
        int i = 0;
        while(iter.hasNext())
            values[i++] = (String) iter.next();
        return values;
    }

    public Map getNameValuePairs (String flag, String separator){
        return (Map) switchValues.get(flag);
    }


    /**
     * Take a the command line specified by <code>arguments</code> and 
     * parse it, storing the results. Returns an <code>boolean</code> 
     * specifying an error.
     */
    CommandLine (Class requestor, int expectedArgs, boolean variableArgs){
        this (requestor, expectedArgs);
        this.variableArgs = variableArgs;
    }

    CommandLine (Class requestor, int expectedArgs){
        this (requestor, expectedArgs, 0);
    }


    CommandLine (Class requestor, int expectedArgs, int extraArgs){
        root = requestor;
        this.expectedArgs = expectedArgs;
        this.extraArgs = extraArgs;
        parsedArgs = new String[expectedArgs + extraArgs];
    }


    boolean parse (String [] arguments) throws IOException {
        StringBuffer sb = new StringBuffer("h");
        Iterator iter = switches.keySet().iterator();
        while (iter.hasNext()){
            String flag = (String) iter.next();
            sb.append("|");
            sb.append(flag);
            if (switches.get(flag) != NOVALUE){
                sb.append(":");
                if (switches.get(flag) == RECURRING || switches.get(flag) == RECURRING_NAME_VALUE){
                    switchValues.put(flag, new HashMap());
                }
            }
        }
        Getopt options = new Getopt(arguments, sb.toString());
        // Loop through the entire commandline.
        while (options.hasMore()) 
            {
                Getopt.Option option = options.next();

                if (option.noSwitch()){

                    // An ordered (non-flag) argument
                    count ++;
                    if (count > expectedArgs + extraArgs){
                        if (extraArgs == 0)
                            printUsage("Expected " + expectedArgs + 
                                       " args, got " + count);
                        else
                            printUsage("Expected " + expectedArgs + " to " +
                                   (expectedArgs + extraArgs) + " args, got " + count);
                        return false;
                    } else{
                        parsedArgs[count-1] = option.value();
                    }
                } else { // switch

                    // always recognize help flag
                    if (option.name() == 'h'){
                        printUsage("Help");
			helpMode = true;
                        return false;
                    }    
            
                    String switchName = Character.toString(option.name());
                    Object switchType = switches.get(switchName);

                    if (switchType == NOVALUE){
                        switchValues.put(switchName, NOVALUE);
                    } else if (switchType == ONEVALUE){
                        switchValues.put(switchName, option.value());
                        
                    } else if (switchType == RECURRING_NAME_VALUE){
                        parseNameValuePair(option.value(), (Map) switchValues.get(switchName));
                    } else if (switchType == RECURRING){
                        ((Map) switchValues.get(switchName)).put(option.value(), option.value());
                    } else{
                        printUsage("Unhandled switch: " + switchName);
                        return false;
                    }  
                }
            }   // while

        if (count < expectedArgs || count > expectedArgs + extraArgs){
            if (extraArgs == 0)
                printUsage("Expected " + expectedArgs + " args, got " + count);
            else
                printUsage("Expected " + expectedArgs + " to " +
                       (expectedArgs + extraArgs) + " args, got " + count);
            return false;
        }
        else{
            // We found all required parameters
            return true;
        }
    }


    private void printUsage(String msg) throws IOException{
        System.out.println(msg);
        printUsage();
    }
    
    // Write canonically named help file to STDOUT
    private void printUsage() throws IOException{
        printUsage(root);
    }

    private static void printUsage(Class c) {
        try{
            InputStream is = c.getResourceAsStream(c.getName() + ".txt");
            BufferedReader reader = 
                                 new BufferedReader(new InputStreamReader(is));
            String s = reader.readLine();
            while ((s = reader.readLine()) != null){
                System.out.println(s);
            }
            is.close();
        }
        catch (Exception e){
            System.out.println("No help file found");
        }
    }

    private void parseNameValuePair(String line, Map nameValuePairs){
        int i = line.indexOf("=");
        if (i < line.length() - 1 && i > 0){
            String name = line.substring(0, i);
            String value = line.substring(i+1);
            nameValuePairs.put(name, value);
        }
        else {
            throw new IllegalArgumentException("Invalid metadata spec: " + line);
        }
    }


    public static void main(String[] argv){
        printUsage(CommandLine.class);        
    }
}
