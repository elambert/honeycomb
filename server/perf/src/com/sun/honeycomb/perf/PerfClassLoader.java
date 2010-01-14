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



package com.sun.honeycomb.perf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;

import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.classfile.ConstantUtf8;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;
import java.io.InputStream;
import org.apache.bcel.classfile.ConstantClass;
import org.apache.bcel.classfile.ClassParser;
import java.util.logging.Logger;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.ARETURN;
import org.apache.bcel.generic.ACONST_NULL;
import org.apache.bcel.classfile.Attribute;
import org.apache.bcel.classfile.InnerClasses;
import org.apache.bcel.classfile.Constant;

public class PerfClassLoader
    extends ClassLoader {

    private static final Logger LOG = Logger.getLogger(PerfClassLoader.class.getName());
    private static final String PERF_PATH = "perf.path";
    private static final String PERF_SPECFILE = "perf.specfile";

    private static class MonitorEntry {
        private String original;
        private String monitor;

        private MonitorEntry(String[] fields) {
            original = fields[0];
            monitor = fields[1];
        }
    }

    private HashMap originals;
    private HashMap monitors;
    private JarFile[] jars;

    private void readSpecFile() {
        String perfFile = System.getProperty(PERF_SPECFILE);
        if (perfFile == null) {
            throw new RuntimeException("Property "+PERF_SPECFILE+" has not been defined");
        }

        File specFile = new File(perfFile);
        if (!specFile.exists()) {
            throw new RuntimeException("Spec file not found ["+
                                       specFile.getAbsolutePath());
        }

        BufferedReader reader = null;
        originals = new HashMap();
        monitors = new HashMap();

        try {
            reader = new BufferedReader(new FileReader(specFile));
            String line = null;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",");
                MonitorEntry entry = new MonitorEntry(fields);
                originals.put(entry.original, entry);
                monitors.put(entry.monitor, entry);
            }
        } catch (IOException e) {
            RuntimeException newe = new RuntimeException("Failed to read the spec file ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void initJars() {
        String perfPath = System.getProperty(PERF_PATH);
        if (perfPath == null) {
            throw new RuntimeException("The "+PERF_PATH+" property has not been defined");
        }

        try {
            String[] fileNames = perfPath.split(":");
            jars = new JarFile[fileNames.length];

            for (int i=0; i<fileNames.length; i++) {
                jars[i] = new JarFile(fileNames[i]);
            }
        } catch (IOException e) {
            RuntimeException newe = new RuntimeException("Failed to load the PerfClassLoader jar files ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }
    }

    private void init() {
        readSpecFile();
        initJars();
    }

    public PerfClassLoader() {
        init();
    }

    public PerfClassLoader(ClassLoader parent) {
        super(parent);
        init();
    }

    private byte[] loadCode(String name) 
        throws IOException, ClassNotFoundException {

        int jarIndex;
        JarEntry entry = null;

        String modifiedName = name.replace('.', '/') + ".class";
        for (jarIndex=0; jarIndex<jars.length; jarIndex++) {
            entry = jars[jarIndex].getJarEntry(modifiedName);
            if (entry != null) {
                break;
            }
        }

        if (entry == null) {
            // Class no found
            throw new ClassNotFoundException();
        }
        
        byte[] classBytes;

        InputStream stream = jars[jarIndex].getInputStream(entry);
        int size = (int)entry.getSize();
        classBytes = new byte[size];
        int nbRead = 0;

        while (nbRead < size) {
            nbRead += stream.read(classBytes, nbRead, size-nbRead);
        }

        return(classBytes);
    }

    private void setString(ConstantPoolGen pool,
                           int index,
                           String string) {
        int utf8Index = ((ConstantClass)pool.getConstant(index)).getNameIndex();
        pool.setConstant(utf8Index, new ConstantUtf8(string));
    }

    private void checkGetInstance(ClassGen classGen,
                                  String name) {
        Method[] methods = classGen.getMethods();

//         for (int i=0; i<methods.length; i++) {
//             if (methods[i].getName().equals("getInstance")) {
//                 System.out.println(classGen.getConstantPool());
//                 System.out.println(methods[i].getCode().toString(true));
//                 break;
//             }
//         }

        for (int i=0; i<methods.length; i++) {
            if ((methods[i].isStatic())
                && (methods[i].getName().equals("getInstance"))) {
                classGen.removeMethod(methods[i]);
                break;
            }
        }
    }

    private void replaceAllStrings(ConstantPoolGen pool,
                                   String oldString,
                                   String newString) {
        int size = pool.getSize();
        for (int i=0; i<size; i++) {
            Constant constant = pool.getConstant(i);
            if (constant instanceof ConstantUtf8) {
                ConstantUtf8 in = (ConstantUtf8)constant;
                String inputString = in.getBytes();
                if (inputString.indexOf(oldString) != -1) {
                    String replacement = inputString.replaceAll(oldString, newString);
                    in.setBytes(replacement);
                }
            }
        }
    }

    protected Class findClass(String name)
        throws ClassNotFoundException {

        byte[] byteCode = null;
        
        String prefix = null;
        String suffix = null;
        int dIndex = name.indexOf('$');
        if (dIndex == -1 ) {
            prefix = name;
            suffix = "";
        } else {
            prefix = name.substring(0,  dIndex);
            suffix = name.substring(dIndex);
        }
                            
        boolean original = originals.containsKey(name);
        boolean monitor = monitors.containsKey(prefix);
        MonitorEntry entry = null;

        if (original) {
            entry = (MonitorEntry)originals.get(name);
        }
        if (monitor) {
            entry = (MonitorEntry)monitors.get(prefix);
        }

        try {
            if (original) {
                byteCode = loadCode(entry.monitor);
            } else if (monitor) {
                byteCode = loadCode(entry.original+suffix);
            } else {
                byteCode = loadCode(name);
            }
        } catch (IOException e) {
            ClassNotFoundException newe = new ClassNotFoundException("Failed to load the byteCode of "+
                                                                     name+" ["+e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }
        
        if ((!original) && (!monitor)) {
            return(defineClass(name, byteCode,
                               0, byteCode.length));
        }

        try {
            ClassParser parser = new ClassParser(new ByteArrayInputStream(byteCode),
                                                 "Performance bytecode");
            ClassGen classGen = new ClassGen(parser.parse());

            if (original) {
                LOG.info("PERF Patch class ["+name+"] (return the monitor one)");
                ConstantPoolGen pool = classGen.getConstantPool();
                setString(pool, classGen.getClassNameIndex(), entry.original.replace('.', '/'));
                setString(pool, classGen.getSuperclassNameIndex(), entry.monitor.replace('.', '/'));
            }
            if (monitor) {
                LOG.info("PERF Patch class ["+name+"] (return the original bytecode)");
                ConstantPoolGen pool = classGen.getConstantPool();
                replaceAllStrings(classGen.getConstantPool(),
                                  entry.original.replace('.', '/'),
                                  entry.monitor.replace('.', '/'));
                if (suffix.length() == 0) {
                    checkGetInstance(classGen, entry.monitor);
                }
            }

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            classGen.getJavaClass().dump(bytes);

            Class result = defineClass(name, bytes.toByteArray(), 0, bytes.size());

            return(result);
        } catch (IOException e) {
            ClassNotFoundException newe = new ClassNotFoundException(e.getMessage());
            newe.initCause(e);
            throw newe;
        }
    }
}