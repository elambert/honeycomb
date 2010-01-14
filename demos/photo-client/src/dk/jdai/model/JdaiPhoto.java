// JdaiPhoto.java
// $Id: JdaiPhoto.java 3456 2005-02-05 00:43:33Z rw151951 $

package dk.jdai.model;

import java.util.Map;
import java.awt.Image;

/**
 * Provides
 * @author jaybe
 * @version $Revision: 1.2 $
 */
/**
 * The interface a Photo class must implement.
 * @author Jeppe Buk (jaybe@jaybe.dk)
 * @version $Revision: 1.2 $
 */
public interface JdaiPhoto extends Comparable {

    /**
     * Get the section this photo belongs to.
     * @return The section.
     */
    public JdaiSection getSection();

    /**
     * Get the section-unique ID of this photo.
     * @return The ID.
     */
    public String getId();

    /**
     * Get a thumbnail of the photo as a BufferedImage for displaying.
     * @return The thumbnail image.
     * @exception JdaiReadException Thrown when thumbnail could not be read.
     */
    public Image getThumbnail() throws JdaiReadException;

    /**
     * Refresh the thumbnail of this photo. Is a thumbnail has not been loaded
     * this method does nothing - otherwise it tells the photo to reload the
     * thumnail next time it's needed.
     */
    public void refreshThumbnail();

    /**
     * Get the photo itself as a BufferedImage for displaying.
     * @return The image.
     * @exception JdaiReadException Thrown when image could not be read.
     */
    public Image getImage() throws JdaiReadException;

    /**
     * Get the photo itself as a BufferedImage for displaying. This
     * method supports resizing the image in a bounding box (the image
     * is never enlarged).
     * @param width Maximum width of the new image.
     * @param height Maximum height of the new image.
     * @return The image.
     * @exception JdaiReadException Thrown when image could not be read.
     */
    public Image getImage(int width, int height) throws JdaiReadException;
    
    /**
     * Method copyTo. Copy photo to another photo (deep copy);
     * @param other the other photo.
     * @throws JdaiReadException when photo or infostore could not be read.
     * @throws JdaiWriteException when photo or infostore could not be written.
     */
    public void copyTo(JdaiPhoto other) throws JdaiReadException, JdaiWriteException;
    
    /**
     * Method delete. Deletes this photo and information in the infostore of the section.
     * @throws JdaiReadException when photo or infostore could not be read.
     * @throws JdaiWriteException when photo or infostore could not be written.
     */
    public void delete() throws JdaiReadException, JdaiWriteException;

    /**
     * Get meta information from the photo (e.g. EXIF from digital camera photos).
     * @return An Array of String arrays with metadata.
     */
    public Map getMetaInfo();

    /**
     * Get meta information from the photo (e.g. EXIF from digital camera photos).
     * @return An HTML String with pretty-printed metadata.
     */
    public String getMetaInfoHtml();

    /** 
     * Aborts any ongoing image reads.
     */
    public void abortRead();

    /**
     * Sets which listener should receive info about progress of reads
     *
     * @param progress The listener
     */
    public void setProgressListener(JdaiProgressListener progress);
}
