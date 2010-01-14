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



/*
 * Base class to handle all the reflective APIs.
 */


import java.util.List;
import java.math.BigInteger;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import java.io.*;


abstract public class HCCmd {

    protected String    destination = "http://127.0.0.1:9000";
    protected String    commandName = "unknown";
    protected int       nbLoops = 1;
    protected String [] args = null;


    public static void main(String[] arg) {
        HCCellCmd test = new HCCellCmd(arg);
    }

    
    abstract protected String getClassName();

    protected HCCmd(String prefix, String [] arg) {

        parse(arg);
        System.out.print(prefix + ": destination = " + destination +
                         ", nb loops = " + nbLoops +
                         ", cmd = " + commandName);
        if (args != null) {
            System.out.print(", arguments = ");
            for (int i = 0; i < args.length; i++) {
                System.out.print(args[i] + " ");     
            }
        }
        System.out.println("");
    }

    protected Object run(Object obj, String commandName, String [] args) {

        Class claz = null;
        Method [] methods = null;
        Object res = null;

        try {
            claz = Class.forName(getClassName());
        } catch (Exception e) {
            System.err.println("cannot get class " + getClassName());
            System.exit(1);
        }
        System.out.println("STEPH : get class " + getClassName());
        try {
            methods = claz.getMethods();
        } catch (Exception e) {
            System.err.println("cannot get method for class" + getClassName());
            System.exit(1);
        }


        Method M  = null;
        for (int j = 0; j < methods.length; j++) {
            if (methods[j].getName().equals(commandName)) {   
                System.out.println("STEPH : found method " + commandName);
                M = methods[j];
            }
        }
        if (M == null) {
            System.err.println("cannot get method " + 
                               commandName + " for class" + getClassName());
            System.exit(1);
        }

        long cumulTime = 0;

        for (int k = 0; k < nbLoops; k++) {
            System.out.println("Loop " + (k + 1) + 
                               ": execute cmd: " + commandName + 
                               " " + args);
            
            Object [] arguments = null;
            Class [] parameterTypes = M.getParameterTypes();
            arguments = buildArguments(parameterTypes);
            long initTime = System.currentTimeMillis();
            res = executeCommand(obj, M, arguments);
            cumulTime = System.currentTimeMillis() - initTime;
        }
        double avg = (double) cumulTime / (double) nbLoops;
        System.out.println("average time for op = " + avg);

        int result = decodeResult(res);
        System.out.println("call " + commandName + " returns " + result);
        return result;
    }

    private int decodeResult(Object res) {
        if (res instanceof Byte) {
            return ((Byte) res).intValue();            
        } else if (res instanceof BigInteger) {
            return ((BigInteger) res).intValue();
        } else {
            System.err.println("unexpected result type");
            System.exit(-1);
            return -1;
        }
    }
    private Document createDummyDocument() {
        try {
            File f = new File("dummy.xml");
            if (!f.exists()) {
                FileOutputStream out = new FileOutputStream(f);
                PrintWriter wout = new PrintWriter(out);
                wout.println("<dummy_xml>");
                wout.println("  <dummy_el>\"This is a dummy xml file\"</dummy_el> ");
                wout.println("</dummy_xml>");
                wout.flush();
                wout.close();
                out.close();
            }
        } catch (Exception e) {
            System.err.println("cannot gcreate dummy file" + e);
            System.exit(1);
        }
        return getDocument("dummy.xml");
    }

    private Object [] buildArguments(Class [] parameterTypes) {

        if ((parameterTypes == null) || 
            (parameterTypes.length == 0)) {
            return null;
        }
        if (args != null) {
            if (parameterTypes.length != args.length) {
                System.err.println("invalid number of arguments, " +
                                   "args.length = " + 
                                   args.length + 
                                   ", parameterTypes.length = " + 
                                   parameterTypes.length);
                System.exit(1);
            }
        }

        Object [] arguments = new Object[parameterTypes.length];
        for (int k = 0; k < parameterTypes.length; k++) {
            Class curParam = parameterTypes[k];

            // HCCell methods only take byte and String as arguments.
            String strTest = "dummy";
            Byte byteTest = new Byte("1");
            Document xml = createDummyDocument();

            if (curParam.isInstance(strTest)) {
                arguments[k] = args[k];
            } else if (curParam.isInstance(byteTest)) {
                arguments[k] = new Byte(args[k]);
            } else if (curParam.isInstance(xml)) {
                xml = getDocument(args[k]);
                arguments[k] = xml;            
            }
        }
        return arguments;
    }

    Document getDocument(String fileName) { 

        Document xml = null;

        try {
            File f = new File(fileName);
            DocumentBuilder docBuilder = 
                DocumentBuilderFactory.newInstance().newDocumentBuilder();

            xml = docBuilder.parse(f);
            
        } catch (SAXException saxE) {
            System.err.println("can 't parse file " + 
                               fileName + ": " + saxE);
            System.exit(1);
        } catch (IOException ioe) {
            System.err.println("can 't parse file " + 
                               fileName + ": " + ioe);
            System.exit(1);                    
        } catch (ParserConfigurationException pExc) {
            System.err.println("can 't parse file " + 
                               fileName + ": " + pExc);
            System.exit(1);                    
        }
        return xml;
    }


    private Object executeCommand(Object obj, Method method,
                                  Object [] arguments) {

        Object res = null;

        try {
            res = method.invoke(obj, arguments);
        } catch (IllegalAccessException ile) {
            System.err.println("failed to invoke method " + 
                               commandName +
                               " " + ile);
            System.exit(1);
        } catch (IllegalArgumentException ila) {
            System.err.println("failed to invoke method " + 
                               commandName +
                               " " + ila);
            System.exit(1);
        } catch (InvocationTargetException ite) {
            Throwable th = ite.getTargetException();
            System.err.println("failed to invoke method " + 
                               commandName +
                               " " + ite);
            System.err.println("Message : " + th.getMessage());
            System.err.println("Statck trace : " + th.getStackTrace());
            System.exit(1);
        } catch (ExceptionInInitializerError eie) {
            System.err.println("failed to invoke method " + 
                               commandName +
                               " " + eie);
            System.exit(1);
        } catch (Exception exc) {
            System.err.println("failed to invoke method " + 
                               commandName +
                               " " + exc);
            System.exit(1);
        }
        return res;
    }

    private void usage() {
        System.err.println("java -cp . <HCCellxx> (-a <dest>) (-l <loops>) -c <cmdName> (<arg1> <arg2> ...)");
        System.err.println("[make sure you end the command with the -c cmdCname args, the parser is not very smart...]");
        System.exit(-1);
    }

    private void parse(String[] argv) {

        int position = 0;
        int argPosition = 0;
        while (position < argv.length) {

            String curArg = argv[position];
            if (curArg.startsWith("-a")) {
                position++;
                destination = argv[position];
                position++;
                continue; 
            } else if (curArg.startsWith("-l")) {
                position++;
                nbLoops = Integer.parseInt(argv[position]);
                position++;
                continue; 
            } else if (curArg.startsWith("-c")) {
                position++;
                commandName = argv[position];
                position++;
                if (argv.length - position > 0) {
                    args = new String[argv.length - position];
                }
                continue;
            } else if (args != null) {
                args[argPosition++] = argv[position++];
            } else {
                usage();
            }
        }
    }
}
