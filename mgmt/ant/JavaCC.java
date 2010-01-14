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
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.Mkdir;
import org.apache.tools.ant.taskdefs.Java;
import org.apache.tools.ant.types.Commandline;
import java.io.File;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.Project;

public class JavaCC
    extends Task {

    private String javaccJar;
    private String srcDir;
    private String outputDir;
    private String pkgName;
    private String pkgPath;
    private String className;

    public JavaCC() {
        super();
    }

    public void setJavaCC(String jar) {
        javaccJar = jar;
    }

    public void setSrcDir(String _srcDir) {
        srcDir = _srcDir;
    }

    public void setOutputDir(String _outputDir) {
        outputDir = _outputDir;
    }

    public void setPkgName(String _pkgName) {
        pkgName = _pkgName;
        pkgPath = _pkgName.replace('.', '/');
    }

    public void setClassName(String _className) {
        className = _className;
    }

    public void execute()
        throws BuildException {
        if (javaccJar == null) {
            throw new BuildException("No javaCC attribute defined");
        }
        if (srcDir == null) {
            throw new BuildException("No srcDir attribute defined");
        }
        if (outputDir == null) {
            throw new BuildException("No outputDir attribute defined");
        }
        if (pkgName == null) {
            throw new BuildException("No pkgName attribute defined");
        }
        if (className == null) {
            throw new BuildException("No className defined");
        }

        File orig = new File(srcDir+"/"+pkgPath+"/"+className+".jj");
        File dest = new File(outputDir+"/"+pkgPath+"/"+className+".java");

        if (dest.exists() && (orig.lastModified() < dest.lastModified())) {
            log(className+" is up to date");
            return;
        }

        log("Creating ["+className+"] from package ["+
            pkgName+"]");

        Mkdir mkdirTask = new Mkdir();
        mkdirTask.setProject(getProject());
        mkdirTask.setDir(new File(outputDir+"/"+pkgPath));
        mkdirTask.execute();

        Java javaTask = new Java();
        javaTask.setProject(getProject());
        javaTask.setClassname("javacc");
        javaTask.createClasspath().setPath(javaccJar);
        javaTask.setFork(false);
        javaTask.setFailonerror(true);
        
        javaTask.createArg().setValue("-OUTPUT_DIRECTORY="+outputDir+"/"+pkgPath);
        javaTask.createArg().setValue(orig.getAbsolutePath());

        javaTask.execute();
    }
}
