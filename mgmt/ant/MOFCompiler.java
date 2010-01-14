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



import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.Java;
import java.io.File;
import org.apache.tools.ant.types.Environment;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.Mkdir;
import org.apache.tools.ant.types.Path;

public class MOFCompiler
    extends Task {

    private String mofCompilerJar;
    private String moffile;
    private String clientXsdFile;
    private String serverXsdFile;
    private String outputDir;
    private String rootPackage;
    private String classpath;

    public MOFCompiler() {
        super();
    }

    public void setMofCompilerJar(String _mofCompilerJar) {
        mofCompilerJar = _mofCompilerJar;
    }

    public void setMoffile(String _moffile) {
        moffile = _moffile;
    }

    public void setClientXsdFile(String _clientXsdFile) {
        clientXsdFile = _clientXsdFile;
    }
    public void setServerXsdFile(String _serverXsdFile) {
        serverXsdFile = _serverXsdFile;
    }

    public void setOutputDir(String _outputDir) {
        outputDir = _outputDir;
    }
    public void setRootPackage(String _rootPackage) {
        rootPackage = _rootPackage;
    }
    public void setClasspath(String _classpath) {
        classpath = _classpath;
    }

    public void execute()
        throws BuildException {
        if (mofCompilerJar == null) {
            throw new BuildException("mofCompilerJar has not been defined");
        }
        if (moffile == null) {
            throw new BuildException("moffile has not been defined");
        }
        if (clientXsdFile == null) {
            throw new BuildException("clientXsdFile has not been defined");
        }
        if (serverXsdFile == null) {
            throw new BuildException("serverXsdFile has not been defined");
        }
        if (outputDir == null) {
            throw new BuildException("outputDir has not been defined");
        }
        if (rootPackage == null) {
            throw new BuildException("rootPackage has not been defined");
        }

        File mof = new File(moffile);
        File xsd = new File(clientXsdFile);
        boolean hasToCompile = !xsd.exists() || (xsd.lastModified() <= mof.lastModified());

        if (!hasToCompile) {
            xsd = new File(serverXsdFile);
            hasToCompile = !xsd.exists() || (xsd.lastModified() <= mof.lastModified());
        }
        
        if (!hasToCompile) {
            File compilerFile = new File(mofCompilerJar);
            if (compilerFile.lastModified() > xsd.lastModified()) {
                System.out.println("The compiler has been modified. Need to recompile the mof file");
                hasToCompile = true;
            }
        }

        if (!hasToCompile) {
            System.out.println(xsd.getAbsolutePath()+" is up to date");
            return;
        }

        String pkgPath = outputDir+"/"+rootPackage.replace('.', '/');
        String handlerPackage = "com.sun.ws.management.server.handler";
        String handlerDir=outputDir+"/"+handlerPackage.replace('.', '/');
        
        Mkdir mkdirTask = new Mkdir();
        mkdirTask.setProject(getProject());
        mkdirTask.setDir(new File(pkgPath+"/client"));
        mkdirTask.execute();

        mkdirTask = new Mkdir();
        mkdirTask.setProject(getProject());
        mkdirTask.setDir(new File(pkgPath+"/server"));
        mkdirTask.execute();

        mkdirTask = new Mkdir();
        mkdirTask.setProject(getProject());
        mkdirTask.setDir(new File(handlerDir));
        mkdirTask.execute();

        Java javaTask = new Java();
        javaTask.setProject(getProject());
        javaTask.setJar(new File(mofCompilerJar));
        if (classpath != null)
            javaTask.setClasspath(new Path(getProject(), classpath));
        javaTask.createArg().setValue(moffile);
        javaTask.setFork(true);
        javaTask.setFailonerror(true);

        Environment.Variable var = new Environment.Variable();
        var.setKey("outputDir");
        var.setValue(outputDir);
        javaTask.addSysproperty(var);

        var = new Environment.Variable();
        var.setKey("clientXsdFile");
        var.setValue(clientXsdFile);
        javaTask.addSysproperty(var);

        var = new Environment.Variable();
        var.setKey("serverXsdFile");
        var.setValue(serverXsdFile);
        javaTask.addSysproperty(var);

        var = new Environment.Variable();
        var.setKey("rootPackage");
        var.setValue(rootPackage);
        javaTask.addSysproperty(var);

        var = new Environment.Variable();
        var.setKey("handlerPackage");
        var.setValue(handlerPackage);
        javaTask.addSysproperty(var);

        javaTask.execute();
    }
}
