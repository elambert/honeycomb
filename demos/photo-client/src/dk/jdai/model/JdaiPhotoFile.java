// JdaiPhotoFile.java
// $Id: JdaiPhotoFile.java 3456 2005-02-05 00:43:33Z rw151951 $

package dk.jdai.model;

import dk.jdai.gui.JdaiGuiHelpers;

import java.io.*;
import java.util.Map;
import java.util.Map.Entry;
import java.awt.Image;

/**
 * A file based JDAI Photo class.
 * @author Jeppe Buk (jaybe@jaybe.dk)
 * @version $Revision: 1.2 $
 */
public class JdaiPhotoFile implements JdaiPhoto {

    private JdaiSection section;
    private String id;
    private String fileName;
    private EXIFInfo exif;
    private JdaiProgressListener progress;

    private static LRUCache cache = new LRUCache(150);

    private static class LRUCache extends java.util.LinkedHashMap {
        private int maxsize;
        protected boolean removeEldestEntry(Entry eldest) {
            return size() >= maxsize;
        }
        public LRUCache(int maxsize) {
            super(maxsize * 4 / 3 + 1, 0.75f, true);
            this.maxsize = maxsize;
        }
    }

    /**
     * Creates a new instance of JdaiPhotoFile.
     * @param section The section this photo belongs to.
     * @param id The ID of the photo.
     * @param fileName The filename of the photo.
     */
    public JdaiPhotoFile(JdaiSection section, String id, String fileName) {
        this.section = section;
        this.id = id;
        this.fileName = fileName;
    }

    /**
     * Get the section this photo belongs to.
     * @return The section.
     */
    public JdaiSection getSection() {
        return section;
    }

    /**
     * Get the section-unique ID of this photo.
     * @return The ID.
     */
    public String getId() {
        return id;
    }

    /**
     * Get a thumbnail of the photo as a BufferedImage for displaying.
     * @return The thumbnail image.
     * @exception JdaiReadException Thrown when thumbnail could not be read.
     */
    public Image getThumbnail() throws JdaiReadException {
        Image image = null;
        if (cache.containsKey(fileName)) {
            image = (Image) cache.get(fileName);
        } else {
            setupExif();
            if (exif.hasThumbnail()) {
                try {
                    image = exif.getThumbnail();
                } catch (IOException e) {
                    throw new JdaiReadException(e.getMessage());
                }
            } else {
                File thumbFile = new File(fileName + ".thm");
                if (thumbFile.exists()) {
                    image = JdaiImageHelpers.readJpegFile(thumbFile);
                } else {
                    image = getImage(160, 160);
                    try {
                        JdaiImageHelpers.writeJpegFile(image, thumbFile);
                    } catch (JdaiWriteException e) {
                        /* do nothing - no thumb cache on disk */
                    }
                }
            }
            int rotation = getSection().getInfoStore().getRotation(this);
            image = JdaiImageHelpers.rotate(image, rotation);
            cache.put(fileName, image);
        }
        return image;
    }

    /**
     * Refresh the thumbnail of this photo. Is a thumbnail has not been loaded
     * this method does nothing - otherwise it tells the photo to reload the
     * thumnail next time it's needed.
     */
    public void refreshThumbnail() {
        if (cache.containsKey(fileName))
            cache.remove(fileName);
    }

    /**
     * Get the photo itself as an Image for displaying.
     * @return The image.
     * @exception JdaiReadException Thrown when image could not be read.
     */
    public Image getImage() throws JdaiReadException {
        return getImage(0, 0);
    }

    /**
     * Get the photo itself as an Image for displaying. This
     * method supports resizing the image in a bounding box (the image
     * is never enlarged).
     * @param width Maximum width of the image
     * @param height Maximum height of the image
     * @return The image.
     * @exception JdaiReadException Thrown when image could not be read.
     */
    public Image getImage(int width, int height) throws JdaiReadException {
        Image result = JdaiImageHelpers.readJpegFile(new File(fileName), width, height, progress);

        int rotation = getSection().getInfoStore().getRotation(this);

        result = JdaiImageHelpers.rotate(result, rotation);
        return result;
    }

    /**
     * Compare to another photo (sort).
     * @param o The other photo.
     * @return The compare value (see Comparable interface)
     */
    public int compareTo(Object o) {
        int result;
        if ((result = section.compareTo(((JdaiPhoto) o).getSection())) == 0)
            result = id.compareTo(((JdaiPhoto) o).getId());
        return result;
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals(Object o) {
        if (o == null)
            return false;
        return compareTo(o) == 0;
    }

    /**
     * Get a readable string representation of the photo.
     * @return The string representation.
     */
    public String toString() {
        return getId();
    }

    /**
     * Copy to another file-based photo.
     * @param other The photo to copy to.
     */
    public void copyTo(JdaiPhoto other) throws JdaiReadException, JdaiWriteException {
        if (other instanceof JdaiPhotoFile) {
            JdaiPhotoFile o = (JdaiPhotoFile) other;
            try {
                FileInputStream in = new FileInputStream(new File(fileName));
                FileOutputStream out = new FileOutputStream(new File(o.fileName));
                byte[] buf = new byte[1024];
                int c;
                while ((c = in.read(buf)) != -1)
                    out.write(buf, 0, c);
                in.close();
                out.close();
            } catch (FileNotFoundException e) {
                throw (new JdaiReadException(e.getMessage()));
            } catch (IOException e) {
                throw (new JdaiWriteException(e.getMessage()));
            }
            JdaiPhotoInfoStore is1 = getSection().getInfoStore();
            JdaiPhotoInfoStore is2 = o.getSection().getInfoStore();
            int r;
            if ((r = is1.getRotation(this)) != JdaiPhotoInfoStore.NORTH)
                is2.setRotation(other, r);
            String s;
            if (!(s = is1.getCaption(this)).equals(""))
                is2.setCaption(other, s);
            if (!(s = is1.getKeywords(this)).equals(""))
                is2.setKeywords(other, s);
        }
    }

    /**
     * @see dk.jdai.model.JdaiPhoto#delete()
     */
    public void delete() throws JdaiReadException, JdaiWriteException {
    	File photoFile = new File(fileName);
    	if (!photoFile.delete()) throw new JdaiWriteException("Unable to delete file: " + fileName);
    	getSection().getInfoStore().deleteInfo(this);
    }

    /**
     * Get meta information from the photo (e.g. EXIF from digital camera photos).
     * @return A Map of String, String pairs of metadata.
     */
    public Map getMetaInfo() {
        Map infoMap;
        setupExif();
        infoMap = exif.getEXIFMetaData();
        infoMap.put("Id", getId());
        return infoMap;
    }

    private void setupExif() {
        if (exif == null) {
            exif = new EXIFInfo(new File(fileName));
        }
    }

    /**
     * Get meta information from the photo (e.g. EXIF from digital camera photos).
     * @return An HTML String with pretty-printed metadata.
     */
    public String getMetaInfoHtml() {
        Map infoMap = getMetaInfo();
        String[] fieldList = EXIFInfo.getFieldList();
        StringBuffer infoStrBuf = new StringBuffer();
        infoStrBuf.append("<table cellspacing=0 cellpadding=0>");

        for (int i = 0; i < fieldList.length; i++) {
            String key = fieldList[i];
            if (infoMap.containsKey(key)) {
                infoStrBuf.append("<tr><td><font face=\"Helvetica,Arial,sans-serif\" size=\"-1\"><b>");
                infoStrBuf.append(JdaiGuiHelpers.escapeHtml(EXIFInfo.getFieldName(key)) + ":&nbsp;");
                infoStrBuf.append("</b></font></td><td><font face=\"Helvetica,Arial,sans-serif\" size=\"-1\">");
                infoStrBuf.append(JdaiGuiHelpers.escapeHtml((String) infoMap.get(key)));
                infoStrBuf.append("</font></td></tr>");
            }
        }
        infoStrBuf.append("</table>");
        return infoStrBuf.toString();
    }

    /** 
     * Aborts any ongoing image reads.
     */
    public void abortRead() {
        // TO BE DONE
        // Some delegate or something to JdaiImageHelper
    }

    /**
     * Sets which listener should receive info about progress of reads
     *
     * @param progress The listener
     */
    public void setProgressListener(JdaiProgressListener progress) {
        this.progress = progress;
    }
}
