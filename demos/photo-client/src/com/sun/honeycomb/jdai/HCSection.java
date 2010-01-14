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



package com.sun.honeycomb.jdai;

import java.util.*;
import java.io.FileNotFoundException;
import org.xml.sax.Attributes;
import dk.jdai.model.*;
import dk.jdai.gui.*;


import com.sun.honeycomb.fs.HCFile;
import com.sun.honeycomb.fs.VirtualFile;
import com.sun.honeycomb.fs.SchemaParser;
import com.sun.honeycomb.fs.ParseException;
import com.sun.honeycomb.fs.View;

public class HCSection
    implements JdaiSection {

    /****************************************
     *
     * TmbRetrieve thread class
     *
     ****************************************/

    static class TmbRetrieve 
        implements Runnable {
        
        private static final int NB_TMB_RETRIEVE_THREADS = 10;
        
        private static int total;
        private static int nbDone;
        private static ArrayList tasks;

        static {
            tasks = new ArrayList();
            total = 0;
            nbDone = 0;
            for (int i=0; i<NB_TMB_RETRIEVE_THREADS; i++) {
                Thread t = new Thread(new TmbRetrieve(),
                                      "TmbRetrieve-"+(i+1));
                t.setDaemon(true);
                t.start();
            }
        }

        private TmbRetrieve() {
        }

        public static synchronized void addTask(HCPhoto photo) {
            tasks.add(photo);
            total++;
            TmbRetrieve.class.notifyAll();
        }

        public static synchronized void reset() {
            tasks.clear();
            if (JdaiExplorer.explorer != null) {
                JdaiExplorer.explorer.complete(tasks);
            }
            total = 0;
            nbDone = 0;
        }

        public void run() {
            boolean busy = true;
            HCPhoto photo;

            while (busy) {
                try {
                    synchronized (TmbRetrieve.class) {
                        if (tasks.size() == 0) {
                            if (JdaiExplorer.explorer != null) {
                                JdaiExplorer.explorer.complete(tasks);
                            }
                        }
                        while (tasks.size() == 0) {
                            try {
                                TmbRetrieve.class.wait();
                            } catch (InterruptedException ignored) {
                            }
                        }
                        
                        photo = (HCPhoto)tasks.remove(0);
                    }

                    try {
                        photo.getThumbnail();
                        photo = null;
                    } catch (JdaiReadException e) {
                        e.printStackTrace();
                    }

                    synchronized (TmbRetrieve.class) {
                        nbDone++;
                        if (total > 0) {
                            float percentage = (float)nbDone*(float)100/(float)total;
                            JdaiExplorer.explorer.progress(tasks, percentage);
                        }
                    }

                } catch (Throwable thr) {
                    System.out.println("Exception in the thumbnail thread ! ("+thr.getMessage()+")");
                    thr.printStackTrace();
                }
            }
        }
    }

    /****************************************
     *
     * HCInfoStore class
     *
     ****************************************/

    static class HCInfoStore
        implements JdaiPhotoInfoStore {
        
        private HashMap rotations;

        HCInfoStore() {
            rotations = new HashMap();
        }

        public int getRotation(JdaiPhoto photo)
            throws JdaiReadException {
            Integer value = (Integer)rotations.get(photo);
            if (value == null) {
                return(0);
            }

            return(value.intValue());
        }

        public void setRotation(JdaiPhoto photo,
                                int rotation)
            throws JdaiReadException, JdaiWriteException {
            rotations.put(photo, new Integer(rotation));
        }
        
        public String getCaption(JdaiPhoto photo)
            throws JdaiReadException {
            return(photo.getId());
        }

        public void setCaption(JdaiPhoto photo, String caption)
            throws JdaiReadException, JdaiWriteException {
            throw new JdaiWriteException("Not supported");
        }
 
        public String getCaption(JdaiSection section)
            throws JdaiReadException {
            throw new JdaiReadException("Unsupported");
        }
 
        public void setCaption(JdaiSection section, String caption)
            throws JdaiReadException, JdaiWriteException {
            throw new JdaiReadException("Unsupported");
        }

        public String getKeywords(JdaiPhoto photo)
            throws JdaiReadException {
            throw new JdaiReadException("Unsupported");
        }

        public void setKeywords(JdaiPhoto photo, String keywords)
            throws JdaiReadException, JdaiWriteException {
            throw new JdaiReadException("Unsupported");
        }

        public String getKeywords(JdaiSection section)
            throws JdaiReadException {
            throw new JdaiReadException("Unsupported");
        }

        public void setKeywords(JdaiSection section, String keywords)
            throws JdaiReadException, JdaiWriteException {
            throw new JdaiReadException("Unsupported");
        }
        
        public void deleteInfo(JdaiPhoto photo)
            throws JdaiReadException, JdaiWriteException {
            throw new JdaiReadException("Unsupported");
        }
        
        public JdaiPhoto[] searchByKeyword(String keywords)
            throws JdaiReadException {
            throw new JdaiReadException("Unsupported");
        }
    }

    /****************************************
     *
     * HCSection class
     *
     ****************************************/

    private boolean isRoot;
    private HCFile hcf;
    private HCInfoStore infoStore;
    private View view;

    public HCSection(String viewString) {
        isRoot = true;
        infoStore = new HCInfoStore();

        SchemaParser.ViewResult viewResult = null;

        try {
            viewResult = SchemaParser.parseView(viewString);

            String[] attributes = new String[viewResult.attributes.length];
            for (int i=0; i<viewResult.attributes.length; i++) {
                attributes[i] = viewResult.attributes[i];
            }
            view = new View(viewString, attributes, viewResult.representation);

            try {
                hcf = new VirtualFile(null, view, new String[0], null);
            } catch (FileNotFoundException ignored) {
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }
    
    private HCSection(HCFile newHcf,
                      View newView) {
        hcf = newHcf;
        isRoot = false;
        infoStore = new HCInfoStore();
        view = newView;
    }

    /**
     * Get the name of this section.
     * @return The name.
     */
    public String getName() {
        if (isRoot) {
            return(view.getName());
        }

        return(hcf.getName());
    }

    /**
     * Get the subsections contained in this section.
     * @return The subsections.
     */
    public JdaiSection[] getSubSections() {
        if (hcf.isFile()) {
            return(null);
        }

        if (!hcf.hasSubDirectories()) {
            return(null);
        }

        ArrayList results = new ArrayList();

        HCFile[] entries = hcf.listFiles();

        for (int i=0; i<entries.length; i++) {
            if (entries[i].isDirectory()) {
                results.add(new HCSection(entries[i], view));
            }
        }

        JdaiSection[] res = new JdaiSection[results.size()];
        results.toArray(res);

        return(res);
    }

    /**
     * Get the photos contained in this section.
     * @return The photos.
     */
    public JdaiPhoto[] getPhotos() {
        if (hcf.isFile()) {
            return(null);
        }
        
        ArrayList results = new ArrayList();
        HCFile[] entries = hcf.listFiles();
        HCPhoto photo;

        // Take one out of 2 pictures (.md entries)
        TmbRetrieve.reset();

        for (int i=0; i<entries.length; i++) {
            if (entries[i].isFile()) {
                photo = new HCPhoto(this,
                                    entries[i]);
                results.add(photo);
                TmbRetrieve.addTask(photo);
            }
        }
        
        JdaiPhoto[] res = new JdaiPhoto[results.size()];
        results.toArray(res);

        return(res);
    }        

    /**
     * Get the information store associated with this section.
     * @return The information store.
     */
    public JdaiPhotoInfoStore getInfoStore() {
        return(infoStore);
    }

    /**
     * Add a photo to this section.
     * @param photo The photo to add
     */
    public void addPhoto(JdaiPhoto photo)
        throws JdaiReadException, JdaiWriteException {
        throw new JdaiWriteException("Write operation not permitted");
    }

    /**
     * Create a new subsection.
     * @param name The name of the new subsection
     */
    public void createSubSection(String name) {
        return;
    }

    public int compareTo(Object o) {
        if (o.getClass() != getClass()) {
            return(-1);
        }

        HCSection other = (HCSection)o;
        return(((String)hcf.getInfo().get(VirtualFile.FIELD_OBJECTID))
               .compareTo((String)other.hcf.getInfo().get(VirtualFile.FIELD_OBJECTID)));
    }

    public String toString() {
        return(getName());
    }
}
