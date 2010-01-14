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



package gui;

import config.Config;
import javax.swing.filechooser.FileFilter;
import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import java.util.ArrayList;
import java.util.LinkedList;

public class MainFrameImpl
    extends MainFrame 
    implements MainFrameInterface {
    public MainFrameImpl() {
        super();
    }

    // Config window requested
    protected void configure() {
        ConfigureDialog dialog = new ConfigureDialog(this, true);
        dialog.show();
        
        if (dialog.exitCode == 0) {
            Config.setClusterIP(dialog.getClusterIP());
        }
    }

    private class AudioFileFilter extends FileFilter {
        public boolean accept(File file) {
            return(file.isDirectory()
                   || file.getName().endsWith(".ogg")
                   || file.getName().endsWith(".mp3"));
        }
            
        public String getDescription() {
            return("audio files");
        }
    }

    protected void uploadFiles() {
        if (!Config.isValid()) {
            JOptionPane.showMessageDialog(this,
                                          "The current configuration is invalid\n"+
                                          "Update the configuration (Tools->Configure)",
                                          "Bad configuration",
                                          JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser(Config.getLastDirectory());
        fileChooser.setFileFilter(new AudioFileFilter());
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.showDialog(this, "Upload file(s) !");
        
        File[] selected = fileChooser.getSelectedFiles();
        if (selected != null) {
	    if ((selected.length > 0) && (selected[0] != null)) {
		Config.setLastDirectory(selected[0]);
	    }
            mp3.Upload upload = new mp3.Upload(this);
	    upload.uploadFiles(selected);
        }
    }
    
    protected void uploadDirectory() {
        if (!Config.isValid()) {
            JOptionPane.showMessageDialog(this,
                                          "The current configuration is invalid\n"+
                                          "Update the configuration (Tools->Configure)",
                                          "Bad configuration",
                                          JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser(Config.getLastDirectory());
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.showDialog(this, "Upload file(s) !");
        
        LinkedList selected = new LinkedList();
        ArrayList directories = new ArrayList();
        directories.add(fileChooser.getSelectedFile());
	if (fileChooser.getSelectedFile() == null) {
	    return;
	}
	Config.setLastDirectory(fileChooser.getSelectedFile());

        while (directories.size() > 0) {
            File dir = (File)directories.remove(0);
            File[] children = dir.listFiles();
	    if (children == null) {
		System.out.println(dir.getAbsolutePath()+" does not contain any entry");
	    } else {
		for (int i=0; i<children.length; i++) {
                    String fName = children[i].getName();
		    if ( !fName.startsWith(".") &&
                         (fName.endsWith(".mp3") || fName.endsWith(".ogg"))) {
			selected.add(children[i]);
		    }
		    if (children[i].isDirectory()) {
			directories.add(children[i]);
		    }
		}
	    }
        }
        
        mp3.Upload upload = new mp3.Upload(this);
	upload.uploadFiles(selected);
    }

    public void error(String message) {
        JOptionPane.showMessageDialog(this,
                                      message,
                                      "Error !",
                                      JOptionPane.ERROR_MESSAGE);
    }
}
