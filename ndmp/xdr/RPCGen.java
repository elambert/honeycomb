package com.sun.honeycomb.ndmp;

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



import java.io.StreamTokenizer;
import java.io.IOException;
import java.io.File;
import java.io.Writer;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.StringWriter;
import java.io.FileInputStream;
import java.io.Reader;
import java.util.Stack;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ArrayList;


/** Generate marshalling/unmarshalling code and dispatching loop for NDMP requests 
 */
class RPCGen {

    StreamTokenizer st;

    HashMap substitutions = new HashMap();
    HashMap types = new HashMap();
    HashMap typedefs = new HashMap();

    ArrayList messages = new ArrayList();
    HashMap replies = new HashMap();
    HashMap requests = new HashMap();
    HashMap posts = new HashMap();
    HashMap constructors = new HashMap();
    FileWriter out;
    private boolean INCLUDE_DEBUGGING_READERS = true;
    private boolean INCLUDE_DEBUGGING_WRITERS = true;

    private RPCGen(Reader r, String dest) throws IOException{
        st = new StreamTokenizer(r);
        st.slashSlashComments(true);
        st.slashStarComments(true);
        st.eolIsSignificant(false);
        st.ordinaryChar(';');
        st.ordinaryChar('{');
        st.ordinaryChar('=');
        st.wordChars('_', '_');

        // ignore compiler macro
        st.commentChar('%');

        types.put("string", "String");
        types.put("ndmp_u_quad", "long");
        types.put("u_long", "long");
        types.put("u_short", "int");
        types.put("u_int", "int");
        types.put("short", "int");
        types.put("u_char", "int");
        types.put("opaque", "byte");

        out = new FileWriter(dest + File.separator + "NDMP.java");
        System.err.println("Writing to " + dest + File.separator + "NDMP.java");

        out.write("/**\n");
        out.write(" *\n");
        out.write(" * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.\n");
        out.write(" * Use is subject to license terms.\n");
        out.write(" *\n");
        out.write(" * Marshal/unmarshal NDMP XDR requests and responses\n");
        out.write(" * This class is automatically generated from ndmp.x\n");
        out.write(" *\n");
        out.write(" * This is a generated class -- DO NOT EDIT!\n");
        out.write(" *\n");
        out.write(" */\n");
        out.write("\n");
        out.write("package com.sun.honeycomb.ndmp;\n");


        out.write("\n");
        out.write("import java.util.HashMap;\n");
        out.write("import java.io.IOException;\n");
        out.write("import java.util.logging.Level;\n");
        out.write("\n");
        out.write("\n");
        out.write("abstract class NDMP extends BaseNDMP{\n");
        out.write("\n  ");
        out.write("\n  ");
        defineArrayReader("string");
        defineArrayReader("opaque");
        defineArrayWriter("opaque");
        defineArrayReader("u_short");
        defineArrayWriter("u_short");
        out.write("\n  ");
    }

    void defineArrayReader(String type) throws IOException{
        out.write("  " + types.get(type) + "[] read_" + type + "_array() throws IOException{\n");
        out.write("      int len = read_u_int();\n");
        out.write("      return read_" + type + "_array(len);\n");
        out.write("  }\n\n");

        out.write("  " + types.get(type) + "[] read_" + type + "_array(int len) throws IOException{\n");
        out.write("      " + types.get(type) + "[] a = new " + types.get(type) + "[len];\n");
        out.write("      for (int i=0; i<len; i++)\n");
        out.write("          a[i] = read_" + type + "();\n");
        out.write("      return a;\n");
        out.write("  }\n");
    }

    void defineArrayWriter(String type) throws IOException{
        out.write("  void write_" + type + "_array(" + types.get(type) + "[] o) throws IOException{\n");
        out.write("      int len = o.length;\n");
        out.write("      write_u_int(len);\n");
        out.write("      write_" + type + "_array(o, len);\n");
        out.write("  }\n\n");

        out.write("  void write_" + type + "_array(" + types.get(type) + "[] o, int len) throws IOException{\n");
        out.write("      for (int i=0; i<len; i++)\n");
        out.write("          write_" + type + "(o[i]);\n");
        out.write("  }\n");
    }

    private boolean addType(String from, String to) throws IOException{
        return addType(from, to, true);
    }

    private boolean addType(String from, String to, boolean defineArrayReader) throws IOException{
        if (types.get(from) == null){
            types.put(from, to);
            if (!to.endsWith("[]") && defineArrayReader){
                defineArrayReader(from);
                defineArrayWriter(from);
            }
            return false;
        }
        else{
            return true;
        }
    }

    void parse() throws IOException{
        int i = 0;
        while (st.nextToken() != StreamTokenizer.TT_EOF) {
            st.pushBack();
            readStatement();
        }
        writeDispatcherLoop();
        writeReadersForDebugging();
        out.write("\n}\n");
        out.flush();
    }

    class Statement{
        String type;
        String name;
        // Can be String/Statement/null
        Object value = null;
        public String toString(){return type + " " + name + " " + value;}
    }
    
    void readMacro(String name) throws IOException{
        st.eolIsSignificant(true);
        if (!name.equals("define"))
            throw new RuntimeException("Unknown macro " + name);
        substitutions.put(readString(), readString());
        while (StreamTokenizer.TT_EOL != st.nextToken())
            decode();
        st.eolIsSignificant(false);
    }

    
    void readStatement() throws IOException{
        final String type = readString();
        String name = readString();
        //LOGGER.info("Parsing " + type + " " + name);

        if (type.equals("#")){
            readMacro(name);
        }
        else if (type.equals("struct")){
            // Special case: represent ndmp_u_quad as a long
            if (true || !name.equals("ndmp_u_quad"))
                generateClass(name);
        }
        else if (type.equals("union")){
            generateUnion(name);
        }
        else if (type.equals("const")){
            generateConst(name);
        }
        else if (type.equals("enum")){
            generateEnum(name);
        }
        else if (type.equals("typedef")){
            typedef(name);
        }
        else{
            throw new RuntimeException("Unknown statement type " + type);
        }
    }

    void require(String want, String got){        
        if (!want.equals(got))
            throw new RuntimeException("Read " + got + ", expected " + want);
    }

    void typedef(String name) throws IOException{
        String alias = readString();
        String mappedType = (String) types.get(name);
        if (mappedType == null)
            throw new RuntimeException("Unknown type " + name);
        //System.err.println("typedef " + name + " " + alias);
        String termination = readString();
        boolean isArray = false;
        if (termination.equals("<")){
            require(">", readString());
            isArray = true;
            termination = readString();
        }
        typedefs.put(alias, name);
        if (isArray &&  alias.equals("string"))
            addType(alias, mappedType, true);
        else
            addType(alias, mappedType, false);
        require(";", termination);
    }

    static class EnumeratedConstant{
        String identifier;
        String number;
        EnumeratedConstant(String identifier, String number){
            this.number = number;
            this.identifier = identifier;
        }
    }

    void generateEnum(String name) throws IOException{
        addType(name, name);
        out.write("\n");
        out.write("\n");
        out.write("  static class " + name + " {\n");
        out.write("    String name;\n");
        out.write("    int id;\n");
        out.write("    " + name + "(int id, String name){\n");
        out.write("      this.name=name;\n");
        out.write("      this.id=id;\n");
        out.write("    }\n");
        out.write("    public boolean equals(Object o) {\n");
        out.write("      return o instanceof " + name + " && ((" + name + ")o).id == id;\n");
        out.write("    }\n");
        out.write("    public String toString() {return name;}\n");
        out.write("  }\n");
        out.write("\n");
        out.write("  static final HashMap " + name + " = new HashMap();\n");
        out.write("  static final HashMap reverse_" + name + " = new HashMap();\n");
        out.write("\n");

        require("{", readString());

        int i = 0;
        ArrayList constants = new ArrayList();

        for (;;){
            String var = readString(true);
            String token = readString(true);
            String val;

            if (token.equals("=")){
                // preserve supplied integer format (e.g., hexadecimal)
                val = readInt();
                token = readString(true);
            }
            else{
                val = Integer.toString(i++);
            }
            if (name.equals("ndmp_message"))
                messages.add(var);
            out.write("  static " + name + " " + var + " = new " + name + "(" + val +", \"" + var + "\");\n");
            constants.add(new EnumeratedConstant(var, val));

            if ("}".equals(token))
                break;
            else if (!",".equals(token))
                throw new RuntimeException("Expected '} or ',' read "  + token);
        }

        out.write("\n");
        out.write("  static {\n");
        for (int j = 0; j < constants.size(); j++){
            EnumeratedConstant ce = (EnumeratedConstant) constants.get(j);
            out.write("    " + name + ".put(new Integer(" + ce.number + "), " + ce.identifier + ");\n");
            out.write("    reverse_" + name + ".put(\"" + ce.identifier + "\", " + ce.identifier + ");\n");
        }

        require(";", readString());
        out.write("  }\n");
        out.write("\n");
        out.write("  static " + name + " lookup_" + name + "(int i) {\n");
        out.write("    Object o = " + name + ".get(new Integer(i));\n");
        out.write("    if (o == null) throw new RuntimeException(\"No such value: \" + i + \" in " + name + "\");\n");
        out.write("    return (" + name + ") o;\n");
        out.write("  }\n");
        out.write("\n");
        out.write("  static " + name + " lookup_" + name + "(String s) {\n");
        out.write("    Object o = reverse_" + name + ".get(s);\n");
        out.write("    if (o == null) throw new RuntimeException(\"No such value: \" + s + \" in " + name + "\");\n");
        out.write("    return (" + name + ") o;\n");
        out.write("  }\n");
        out.write("\n");
        out.write("  " + name + " read_" + name + "() throws IOException{\n");
        out.write("    int i = (int) controlInputStream.readUnsignedInt();\n");
        out.write("    return (" + name + ") " + name + ".get(new Integer(i));\n");
        out.write("  }\n");

        out.write("\n");
        out.write("  void write_" + name + "(" + name + " " + name + ") throws IOException{\n");
        out.write("    controlOutputStream.writeUnsignedInt((long)" + name + ".id);\n");
        out.write("  }\n");

        out.write("\n");
        out.write("\n");
    }



    void generateConst(String name) throws IOException{
        require("=", readString());
        out.write("\n  public static final int " + name + " = ");
        out.write(readInt());
        out.write(";\n");
        require(";", readString());
    }


    class ClassGenerator {
        String name;
        String comment;
        ArrayList slots = new ArrayList();

        ClassGenerator(String name, String comment){
            this.name = name;
            this.comment = comment;
        }

        class SimpleSlot{

            String name;
            String type;
            String mappedType;

            SimpleSlot(String type, String name){
                this.name = name;

                // override this one
                if (name.equals("protocol_version"))
                    type = "u_int";

                String defined = (String) typedefs.get(type);
                if (defined != null)
                    this.type = defined;
                else
                    this.type = type;

                mappedType = (String) types.get(type);
                if (mappedType == null)
                    throw new RuntimeException("Unknown type " + type);
            }

            String castType(){ return mappedType; }
            void declaration() throws IOException{
                out.write("    " + mappedType + " " + name + ";\n");
            }
            String reader() {
                return "read_" + type + "()";
            }
            void writer(String slot, String indent) throws IOException{
                out.write(indent + "write_" + type + "(" + slot + ");\n");
            }
            public String toString() {
                return name;
            }
        }

        class FixedArraySlot extends VariableArraySlot{
            int n;
            FixedArraySlot(String type, String name, int n){
                super(type, name);
                this.n = n;
            }
            void declaration() throws IOException{
                out.write("    " + mappedType + "[ /* " + n + " */ ] " + name + ";\n");
            }
            String reader(){
                return "read_" + type + "_array("+ n + ")";
            }
            void writer(String slot, String indent) throws IOException{
                out.write(indent + "write_" + type + "_array(" + slot + ", " + n + ");\n");
            }
        }


        class VariableArraySlot extends SimpleSlot{
            VariableArraySlot(String type, String name){
                super(type, name);
            }
            String castType(){ return mappedType + "[]"; }
            void declaration() throws IOException{
                out.write("    " + mappedType + "[] " + name + ";\n");
            }
            String reader() {
                return "read_" + type + "_array()";
            }
            void writer(String slot, String indent) throws IOException{
                out.write(indent + "write_" + type + "_array(" + slot + ");\n");
            }
            public String toString() {
                return "describeArray(" + name + ")";
            }
        }

        void writeClassDeclaration() throws IOException{
            out.write("\n\n  class " + name);
            if (name.endsWith("request"))
                out.write(" extends request ");
            else if (name.endsWith("reply"))
                out.write(" extends reply ");
            else if (name.endsWith("post"))
                out.write(" extends post ");
            out.write("{  //" + comment + "\n");
        }

        void writeClass() throws IOException{
            int n = slots.size();
            SimpleSlot[] s = new SimpleSlot[n];
            slots.toArray(s);

            // start class definition
            writeClassDeclaration();

            // declare slots
            for (int i = 0; i < n; i++)
                s[i].declaration();

            // define toString
            out.write("    public String toString(){\n");
            out.write("      return \"#<" + name + " \" + hashCode()");
            for (int i = 0; i < n; i++)
                out.write(" + \" " + s[i].name + "=\" + " + s[i].toString());
            out.write(" + \">\";\n");
            out.write("    }\n");

            // Constructor with all instance variables supplied
            if (true || !name.endsWith("request")){
                out.write("\n    ");
                StringBuffer sb = new StringBuffer(name);
                sb.append("(");
                for (int i = 0; i < n; i++){
                    sb.append(s[i].castType() + " " + s[i].name);
                    if (i < n - 1)
                        sb.append(", ");
                }
                sb.append(")");
                String c = sb.toString();
                constructors.put(name, c);
                out.write(c);
                out.write("{\n");
                for (int i = 0; i < n; i++)
                    out.write("      this." + s[i].name + " = " + s[i].name + ";\n");
                out.write("    }\n");
            }

            // Constructor with XDR input stream
            if (INCLUDE_DEBUGGING_READERS || !name.endsWith("reply")){
                out.write("\n    " + name + "() throws IOException{\n");
                out.write("      this(");
                for (int i = 0; i < n; i++){
                    if (i != 0)
                        out.write(", ");
                    out.write(s[i].reader());
                //out.write("      " + s[i].name + " = " + s[i].reader() + ";\n");
                }
                out.write(");\n");
                out.write("    }\n");
            }
        
            // Dynamic Writer
            if (name.endsWith("reply") || name.endsWith("post") ||
                (INCLUDE_DEBUGGING_WRITERS && name.endsWith("request"))){
                out.write("\n    void write() throws IOException{\n");
                for (int i = 0; i < n; i++){
                    out.write("  ");
                    s[i].writer(s[i].name, "    ");
                }
                out.write("    }\n");

                // close class definition
                out.write("  } //  " + name + "  " + comment);
                out.write("\n");
            }
            
            else{
                // close class definition
                out.write("  } //  " + name + "  " + comment);
                out.write("\n");

                out.write("\n  void " + 
                          "write_" + name + "(" + name + " " + name + ") throws IOException{\n");
                for (int i = 0; i < n; i++)
                    s[i].writer(name + "." + s[i].name, "    ");
                out.write("  }\n");
            }

            // Reader
            if (INCLUDE_DEBUGGING_READERS || !name.endsWith("reply")){
                out.write("\n  " + name +
                          " read_" + name + "() throws IOException{\n");
                out.write("    return new " + name + "();\n");
                out.write("  }\n");
            }
        }
        

        void addSlot (String type, String name){
            slots.add(new SimpleSlot(type, name));
        }
        void addSlot (String type, String name, boolean variable){
            // Special-case C String<>
            if (type.equals("string"))
                addSlot(type, name);
            else
                slots.add(new VariableArraySlot(type, name));
        }
        void addSlot (String type, String name, int n){
            slots.add(new FixedArraySlot(type, name, n));
        }
    }


    private boolean isMessage(String name){
        return name.endsWith("request") || name.endsWith("post") || name.endsWith("reply");
    }

    private void generateClass(String name) throws IOException{
        out.write("\n\n //////// " + name + "\n");

        if (name.endsWith("request"))
            requests.put(name, name);
        else if (name.endsWith("reply"))
            replies.put(name, name);
        else if (name.endsWith("post"))
            posts.put(name, name);

        boolean alreadyDefined = addType(name, name, !isMessage(name));

        require ("{", readString());

        ClassGenerator cg = new ClassGenerator(name, "struct");
        String token = readString();
        while (! token.equals("}")){
            String type = token;

            boolean isEnum = false;
            if (type.equals("enum")){
                isEnum = true;
                type = readString();
            }
            String identifier = readString();
            String termination = readString();

            if (termination.equals("[")){
                int n = Integer.parseInt(readInt());
                require("]", readString());
                termination = readString();
                cg.addSlot(type, identifier, n);
            }

            else if (termination.equals("<")){
                termination = readString();
                require(">", termination);
                termination = readString();
                cg.addSlot(type, identifier, true);
            }
            else{
                cg.addSlot(type, identifier);
            }

            require (";", termination);
            token = readString();
        }
        if (!alreadyDefined){
            cg.writeClass();
            out.flush();
        }
        require (";", readString());
    }


    private class UnionGenerator extends ClassGenerator{
        String instanceSwitch;
        UnionGenerator (String type, String name, String instanceSwitch){
            super(type, name);
            this.instanceSwitch = instanceSwitch;
        }
        class UnionSlot{
            String switchValue;
            SimpleSlot ss;
            UnionSlot (SimpleSlot ss, String switchValue){
                this.ss = ss;
                this.switchValue = switchValue;
            }
            String reader () {
                if (ss == null)
                    return("      ; // void\n");
                else{
                    return "        body = " +
                    ss.reader() +
                    ";      // " + ss.type + "\n";
                }
            }

            void writer () throws IOException{
                if (ss == null)
                    out.write("      ; // void\n");
                else
                    ss.writer("(" + ss.castType() + ")" + name + ".body",  "      ");
                    //out.write("      write_" + ss.type + "((" + ss.mappedType + ")" + name + ".body);\n");
            }
        }

        void writeClass() throws IOException{
            int n = slots.size();
            UnionSlot[] s = new UnionSlot[n];
            slots.toArray(s);

            // start class definition
            writeClassDeclaration();
            
            // declare slots
            out.write("    " + instanceSwitch + " " + instanceSwitch + ";\n");
            out.write("    Object body = null;\n");
            
            // define toString
            out.write("    public String toString(){\n");
            out.write("      return \"#<" + name + " \" + hashCode() + \" \" + " + instanceSwitch +
                      " + \" \" + describeSlot(body) + \">\";\n");
            out.write("    }\n");

            out.write("    " + name + "(" + instanceSwitch + " instanceSwitch, Object body){\n");
            out.write("      this." + instanceSwitch + " = instanceSwitch;\n");
            out.write("      this.body = body;\n");
            out.write("    }\n");

            out.write("    " + name + "() throws IOException{\n");
            out.write("      " + instanceSwitch + " = " + "read_" + instanceSwitch + "();\n");
            for (int i = 0; i < n; i++){
                out.write("      ");
                if (i != 0)
                    out.write("else ");
                if (s[i].switchValue.equals("default"))
                    out.write("\n      ");
                else
                    out.write("if (" + instanceSwitch + ".name.equals(\"" +  s[i].switchValue + "\"))\n");
                out.write(s[i].reader());
            }
            out.write("    }\n");            
            
            // close class definition
            out.write("  } //  " + name + "  " + comment);
            out.write("\n");
            
            // Writer
            out.write("\n  void " + 
                      "write_" + name + "(" + name + " " + name + ") throws IOException{\n");
            out.write("    write_" + instanceSwitch + "(" + name + "." + instanceSwitch + ");\n");
            for (int i = 0; i < n; i++){
                out.write("    ");
                if (i != 0)
                    out.write("else ");
                if (!s[i].switchValue.equals("default"))
                    out.write("if (" + name + "." + instanceSwitch + ".name.equals(\"" +  s[i].switchValue + "\"))\n");
                s[i].writer();
            }
            out.write("  }\n");



            // Reader
            out.write("\n  " + name +
                      " read_" + name + "() throws IOException{\n");
            out.write("      return new " + name + "();\n");
            out.write("    }\n");
        }

        // Void
        void addSlot (String switchVal){
            slots.add(new UnionSlot((SimpleSlot)null, switchVal));
        }
        void addSlot (String type, String name, String switchVal){
            slots.add(new UnionSlot(new SimpleSlot(type, name), switchVal));
        }
        void addSlot (String type, String name, boolean variable, String switchVal){
            slots.add(new UnionSlot(new VariableArraySlot(type, name), switchVal));
        }
        void addSlot (String type, String name, int n, String switchVal){
            slots.add(new UnionSlot(new FixedArraySlot(type, name, n), switchVal));
        }
    }


    void generateUnion(String name) throws IOException{
        addType(name, name, !isMessage(name));
        require("switch", readString());
        String token = readString();

        // parameter list
        require("(", token);
        String comment = "union";
        String type = readString();
        if (type.equals("enum")){
            type = readString();
            comment += " enum";
        }

        // Slots
        String id = readString();
        require(")", readString());
        require("{", readString());
        //skip("union", name);

        String keyword = readString();
        UnionGenerator ug = new UnionGenerator(name, comment, type);

        while (!keyword.equals("}")){
            
            if (!keyword.equals("default")){
                keyword = readString();
                //out.write("if (type.name.equals(\"" + caseType + "\"))");
            }
            require(":", readString());
            String varType = readString();
            if (varType.equals("struct"))
                varType = readString();

            String termination;
            if (varType.equals("void")){
                //out.write("\n      instance.body = null;\n");
                termination = readString();
                ug.addSlot(keyword);
            }
            else{
                String varName = readString();
                termination = readString();
                if (termination.equals("[")){
                    int n = Integer.parseInt(readInt());
                    require("]", readString());
                    termination = readString();
                    ug.addSlot(varType, varName, n, keyword);
                }

                else if (termination.equals("<")){
                    require(">", readString());
                    termination = readString();
                    ug.addSlot(varType, varName, true, keyword);
                }

                else{
                    ug.addSlot(varType, varName, keyword);
                }
            }
            out.flush();
            require(";", termination);
            keyword = readString();
        }
        ug.writeClass();
        require(";", readString());
    } 

    private String replace(String s, String p, String r){
	int n = s.indexOf(p);
	return s.substring(0, n) + r + s.substring(0, n + p.length());
    }

    private String requestName(int i){
        return ((String)messages.get(i)).toLowerCase() + "_request";
    }

    private String replyName(int i){
        return ((String)messages.get(i)).toLowerCase() + "_reply";
    }

    private String postName(int i){
        return ((String)messages.get(i)).toLowerCase() + "_post";
    }

    /**
     * When a request arrives, look at the ndmp_header's message_code and dispatch 
     * it to the appropriate handler. Default handlers are automatically generated
     * which reply with NDMP_NOT_SUPPORTED_ERR. The requests which are actually
     * implemented override the default handlers.
     */
    private void writeDefaultHandlers() throws IOException{
        out.write("\n");
        out.write("\n");
        out.write("  abstract void NDMP_NOT_SUPPORTED_ERR(ndmp_header header, String arg) throws IOException;");
        out.write("\n");

        out.write("      String composeMessageName(ndmp_header header){\n");
        out.write("          //System.err.println(\"\\n\\nDispatching \" + header);\n");
        out.write("          String message = header.message_code.name.toLowerCase();\n");
        out.write("          if (header.message_type.equals(NDMP_MESSAGE_REQUEST)){\n");
        out.write("              if (/* fromServer */ false)\n");
        out.write("                  message += \"_post\";\n");
        out.write("              else\n");
        out.write("                  message += \"_request\";\n");
        out.write("          }\n");
        out.write("          else {\n");
        out.write("              message += \"_reply\";\n");
        out.write("          }\n");
        out.write("          return message;\n");
        out.write("      }\n");

        for (int i = 0; i < messages.size(); i++){
            String message = (String) messages.get(i);
            String post = postName(i);

            if (posts.get(post) != null){
                // For tape server emulation
                // generate stub for unhandled posts. Override with real methods for cases of interest
                out.write("  void handle_" + post + "(" + post + " " + post + ", " +
                          "ndmp_header header) throws IOException{\n");         
                out.write("    NDMP_NOT_SUPPORTED_ERR(header, " + 
                          post + ".toString());\n");
                out.write("  }\n");
                out.write("\n");
            }
            else {
                String request = requestName(i);
                String reply = replyName(i);
                boolean hasArgs = requests.get(request) != null;
                
                // generate stub for unhandled requests. Override with real methods for cases of interest
                out.write("  void handle_" + request + "(");
                if (hasArgs)
                    out.write(request + " " + request + ", ");
                out.write("ndmp_header header) throws IOException{\n");
                out.write("    //respond(header, new " + constructors.get(reply) + " NDMP_NO_ERR));\n");
                out.write("    NDMP_NOT_SUPPORTED_ERR(header, " + 
                          ((hasArgs) ? request + ".toString()" : "\"" + request + "\"") + ");\n");
                out.write("  }\n");
                out.write("\n");
                
                // For tape server emulation
                // generate stub for unhandled replies. Override with real methods for cases of interest
                hasArgs = replies.get(reply) != null;
                out.write("  void handle_" + reply + "\n    (");
                if (hasArgs)
                    out.write(reply + " " + reply + ",\n    ");
                out.write("ndmp_header header) throws IOException\n  {\n");
                out.write("    System.err.println(");
                if (hasArgs)
                    out.write(reply + " + \" \" + ");
                out.write("header);\n");                      
                out.write("    //request(new _request(), );\n");
                out.write("    NDMP_NOT_SUPPORTED_ERR(header, " + 
                          ((hasArgs) ? reply + ".toString()" : "\"" + reply + "\"") + ");\n");
                out.write("  }\n");
                out.write("\n");
            }
        }

        // weird special case: handle_ndmp_config_get_butype_info_reply returns a ndmp_config_get_butype_attr_reply struct
        out.write("  void handle_ndmp_config_get_butype_info_reply\n    (");
            out.write("ndmp_config_get_butype_attr_reply ndmp_config_get_butype_attr_reply,\n    ");
        out.write("ndmp_header header) throws IOException\n  {\n");
        out.write("    NDMP_NOT_SUPPORTED_ERR(header, ndmp_config_get_butype_attr_reply.toString());\n");
        out.write("  }\n");
        out.write("\n");
    }


    private void writeDispatcherLoop() throws IOException{
        writeDefaultHandlers();

        out.write("\n");
        out.write("  // Set these to generate debugging output\n");
        out.write("  java.io.PrintWriter echoControlWriter = null;\n");
        out.write("  XDROutputStream regenerateControlStream = null;\n");
        out.write("\n");
        out.write("  void dispatch (ndmp_header header) throws IOException{\n");
        out.write("    String message = composeMessageName(header);\n");

        StringWriter clientDispatcherWriter = new StringWriter();
        clientDispatcherWriter.write("\n");
        clientDispatcherWriter.write("  void clientDispatch (ndmp_header header) throws IOException{\n");
        clientDispatcherWriter.write("    String message = composeMessageName(header);\n");

        boolean firstData = true;
        boolean firstTape = true;

        for (int i = 0; i < messages.size(); i++){
            String post = postName(i);
            if (posts.get(post) != null){
                writeDispatcher(post, true, firstData, clientDispatcherWriter);
                firstTape = false;
            }
            else {
                String message = requestName(i);
                boolean hasArgs = requests.get(message) != null;
                writeDispatcher(message, hasArgs, firstData, out);
                firstData = false;
                message = replyName(i);
                hasArgs = replies.get(message) != null;
                writeDispatcher(message, hasArgs, firstTape, clientDispatcherWriter);
                firstTape = false;
            }
        }
        out.write("    else\n       throw new RuntimeException(\"Message handler not found: \" + message + \" \" + header);\n");
        out.write("  }\n");


        out.write("\n");
        out.write("\n");
        out.write(clientDispatcherWriter.toString());
        out.write("    else\n       throw new RuntimeException(\"Message handler not found: \" + message + \" \" + header);\n");
        out.write("  }\n");
    }

    private void writeDispatcher(String message, boolean hasArgs, boolean first, Writer out) throws IOException{
        String messageString = (message.endsWith("_post") ? message.replace("_post", "_request") : message);
        out.write("    ");
        if (!first)
            out.write("else ");
        out.write("if (message.equalsIgnoreCase(\"" + messageString + "\")){\n");
        if (hasArgs){
            out.write("        " + message + " arg = read_" + message + "();\n");
            out.write("      if (LOGGER.isLoggable(Level.INFO)){\n");
            out.write("        LOGGER.info(\"\\nNDMP Message\");\n");
            out.write("        LOGGER.info(\"\\nNDMP Request \" + \"" + message + "\");\n");
            out.write("        LOGGER.info(arg.toString());\n");
            out.write("      }\n");

            out.write("      if (echoControlWriter != null){\n");
            out.write("        echoControlWriter.println(\"\\nNDMP Request \" + header.message_code);\n");
            out.write("        echoControlWriter.println(arg.toString());\n");
            out.write("        XDROutputStream saveOut = controlOutputStream;\n");
            out.write("        controlOutputStream = regenerateControlStream;\n");
            out.write("        write_ndmp_header(header);\n");
            out.write("        arg.write();\n");
            out.write("        controlOutputStream.sendMessage();\n");
            out.write("        controlOutputStream = saveOut;\n");
            out.write("      }\n");

            out.write("      handle_" + message + "(arg, header);\n");
        }
        else {
            out.write("      if (LOGGER.isLoggable(Level.INFO)){\n");
            out.write("        LOGGER.info(\"\\nNDMP Message\");\n");
            out.write("        LOGGER.info(\"\\nNDMP Request \" + header.message_code);\n");
            out.write("      }\n");
            out.write("      if (echoControlWriter != null){\n");
            out.write("        echoControlWriter.println(\"\\nNDMP Request \" + header.message_code);\n");
            out.write("        XDROutputStream saveOut = controlOutputStream;\n");
            out.write("        controlOutputStream = regenerateControlStream;\n");
            out.write("        write_ndmp_header(header);\n");
            out.write("        controlOutputStream.sendMessage();\n");
            out.write("        controlOutputStream = saveOut;\n");
            out.write("      }\n");
            
            // weird special case where the response is not canonical
            if (messageString.equals("ndmp_config_get_butype_info_reply")){
                out.write("        ndmp_config_get_butype_attr_reply arg = read_ndmp_config_get_butype_attr_reply();\n");
                out.write("      handle_" + message + "(arg, header);\n");
            }
            else
                out.write("      handle_" + message + "(header);\n");
        }
        out.write("    }\n");
    }

    private void writeReadersForDebugging() throws IOException{
        out.write("\n\n  /** Reply readers generated for debugging */\n");
        out.write("  String readReply (ndmp_header header) throws IOException{\n");
        out.write("    String reply = header.message_code.toString() + \"_reply\";\n");
        String extra = "";
        Iterator iter = replies.keySet().iterator();

        while (iter.hasNext()){
            String reply = (String) iter.next();
            out.write("    " + extra + "if (reply.equalsIgnoreCase(\"" + reply + "\"))\n");
            out.write("      return new " + reply + "().toString();\n");
            extra = "else ";
        }
        out.write("    else\n");
        out.write("      return \"Unknown reply: \" + reply;\n");
        out.write("  }\n");
        out.write("\n");
    }



    ////////////////////// Helper methods

    String readString() throws IOException{
        return readString(false);
    }
    String readString(boolean eolOK) throws IOException{
        String s;
        st.nextToken();
        switch (st.ttype) {
        case StreamTokenizer.TT_EOF:
            throw new RuntimeException("Premature EOF");
        case StreamTokenizer.TT_EOL:
            if (eolOK){
                s = "\n";
                break;
            }
            else{
                throw new RuntimeException("Unexpected EOL");
            }
        case StreamTokenizer.TT_NUMBER:
            if (st.nval == Math.round(st.nval))
                s = Integer.toString((int)st.nval);
            else
                s = Double.toString(st.nval);
            break;

        case StreamTokenizer.TT_WORD:
            s = st.sval;
            break;
        default:
            s = Character.toString((char)st.ttype);
            break;
        }
        String sub = (String) substitutions.get(s);
        if (sub != null)
            return sub;
        else
            return s;
    }

    boolean isHex(String s){
        if (!s.startsWith("x"))
            return false;
        for (int i = 1; i < s.length(); i++){
            char c = s.charAt(i);
            if (!(c >= '0' && c <= '9' ||
                  c >= 'a' && c <= 'f' ||
                  c >= 'A' && c <= 'F'))
                return false;
        }
        return true;
    }

    // Preserve the decimal or hex representation of an integer literal
    String readInt() throws IOException{
        st.nextToken();
        switch (st.ttype) {
        case StreamTokenizer.TT_EOF:
            throw new RuntimeException("Premature EOF");
        case StreamTokenizer.TT_EOL:
            throw new RuntimeException("Unexpected EOL");
        case StreamTokenizer.TT_NUMBER:
            int i = (int) st.nval;
            if (i == 0){
                String s = readString();
                if (isHex(s)){
                    return i + s;
                }
                else{
                    st.pushBack();
                }
            }
            return Integer.toString(i);

        case StreamTokenizer.TT_WORD:
            String s = (String) substitutions.get(st.sval);
            if (s == null)
                throw new RuntimeException("Expected int, found " + st.sval);
            else{
                return s;
            }
        default:
            throw new RuntimeException("Expected int, found " + Character.toString((char)st.ttype));
        }
    }

    void decode(){
        switch (st.ttype) {
        case StreamTokenizer.TT_EOF:
            System.err.print("TT_EOF");
            break;
        case StreamTokenizer.TT_EOL:
            System.err.println();
            break;
        case StreamTokenizer.TT_NUMBER:
            System.err.print(st.nval);
            break;
        case StreamTokenizer.TT_WORD:
            System.err.print(st.sval);
            break;
        default:
            System.err.print((char)st.ttype);
        }
        System.err.print(" ");
    }


    
    public static void main (String argv[]) throws Exception{
        FileReader fis = new FileReader(argv[0]);
        //FileReader fis = new FileReader("/hc/cabrillo/ndmp/src/java/com/sun/honeycomb/ndmp/ndmp.x.2");
        RPCGen r = new RPCGen(fis, argv[1]);
        try{r.parse();}
        catch (Exception e){
            System.err.println(e + " line " + r.st.lineno());
            e.printStackTrace();
        }
        finally{r.out.flush();}
    }

}

//java -classpath /hc/cabrillo/build/ndmp/classes com.sun.honeycomb.ndmp.RPCGen /hc/cabrillo/ndmp/src/java/com/sun/honeycomb/ndmp/ndmp.x


// TO DO
//  Make XDR writers dynamic methods? 
//  -- Special case the primitives
//  -- Or just define them for messages
