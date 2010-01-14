// JdaiSectionFile.java
// $Id: JdaiSectionFile.java 3456 2005-02-05 00:43:33Z rw151951 $

package dk.jdai.model;

import java.io.*;
import java.util.Arrays;

/**
 * A file based JDAI Section class.
 * @author Jeppe Buk (jaybe@jaybe.dk)
 * @version $Revision: 1.2 $
 */
public class JdaiSectionFile implements JdaiSection {

    // Temporary constants - should be configurable...
    private final static String EXT_REGEXP = "\\.[jJ][pP][gG]";
    private final static String PHOTO_REGEXP = ".*" + EXT_REGEXP;
    private final static String SECTION_REGEXP = "[^.].*";

    private File dir;
    private JdaiPhotoInfoStore infoStore;

    /**
     * Creates a new instance of JdaiSectionFile.
     * @param dirName Name of the directory of the section.
     */
    public JdaiSectionFile(String dirName) {
        dir = new File(dirName);
        infoStore = new JdaiPhotoInfoStoreFile(dir.getAbsolutePath() + File.separator + "jdaistore.xml");
    }

    /**
     * Creates a new instance of JdaiSectionFile.
     * @param dir The directory of the section.
     */
    public JdaiSectionFile(File dir) {
        this.dir = dir;
        infoStore = new JdaiPhotoInfoStoreFile(dir.getAbsolutePath() + File.separator + "jdaistore.xml");
    }

    /**
     * Get the name of this section.
     * @return The name.
     */
    public String getName() {
        return dir.getName();
    }

    /**
     * Get the subsections contained in this section.
     * @return The subsections.
     */
    public JdaiSection[] getSubSections() {
        JdaiSection[] result = null;
        File[] files = null;
        if (dir.isDirectory()) {
            files = dir.listFiles(new FileFilter() {
                public boolean accept(File f) {
                    if (f.isDirectory() && f.getName().matches(SECTION_REGEXP))
                        return true;
                    else
                        return false;
                }
            });
        }
        if (files != null) {
            result = new JdaiSection[files.length];
            for (int i = 0; i < result.length; i++)
                result[i] = new JdaiSectionFile(files[i]);
            Arrays.sort(result);
        }
        return result;
    }

    /**
     * Get the photos contained in this section.
     * @return The photos.
     */
    public JdaiPhoto[] getPhotos() {
        JdaiPhoto[] result = null;
        File[] files = null;
        if (dir.isDirectory()) {
            files = dir.listFiles(new FileFilter() {
                public boolean accept(File f) {
                    if (f.isFile() && f.getName().matches(PHOTO_REGEXP))
                        return true;
                    else
                        return false;
                }
            });
        }
        if (files != null) {
            result = new JdaiPhoto[files.length];
            for (int i = 0; i < result.length; i++) {
                String name = files[i].getName().replaceAll(EXT_REGEXP, "");
                String fileName = files[i].getAbsolutePath();
                result[i] = new JdaiPhotoFile(this, name, fileName);
            }
            Arrays.sort(result);
        }
        return result;
    }

    /**
     * Get the information store associated with this section.
     * @return The information store.
     */
    public JdaiPhotoInfoStore getInfoStore() {
        return infoStore;
    }

    /**
     * Add a photo to this section - creates a new copy of the photo file.
     * @param photo The photo to add
     */
    public void addPhoto(JdaiPhoto photo) throws JdaiReadException, JdaiWriteException {
        if (photo instanceof JdaiPhotoFile) {
        	String newId = photo.getId();
        	String newFileName = dir.getAbsolutePath() + File.separator + newId + ".jpg";
        	int i = 1;
        	while ((new File(newFileName)).exists()) {
        		newId = photo.getId() + "_" + i++;
        		newFileName = dir.getAbsolutePath() + File.separator + newId + ".jpg";
        	}
        	JdaiPhotoFile newPhoto = new JdaiPhotoFile(this, newId, newFileName);
            ((JdaiPhotoFile) photo).copyTo(newPhoto);
        }
    }

    /**
     * Create a new subsection.
     * @param name The name of the new subsection
     */
    public void createSubSection(String name) {
        File newSection = new File(dir.getAbsolutePath() + File.separator + name);
        newSection.mkdir();
    }

    /**
     * Compare to another section (sort).
     * @param o The other section.
     * @return The compare value (see Comparable interface)
     */
    public int compareTo(Object o) {
        return dir.getAbsolutePath().compareTo(((JdaiSectionFile) o).dir.getAbsolutePath());
    }
    
    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals(Object o) {
    	if (o == null) return false;
    	return compareTo(o) == 0;
    }

    /**
     * Get a readable string representation of the section.
     * @return The string representation.
     */
    public String toString() {
        return getName();
    }
}
