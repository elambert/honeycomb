// JdaiPhotoInfoStore.java
// $Id: JdaiPhotoInfoStore.java 3456 2005-02-05 00:43:33Z rw151951 $

package dk.jdai.model;

/**
 * The interface a Photo Information Store class must implement.
 * @author Jeppe Buk (jaybe@jaybe.dk)
 * @version $Revision: 1.2 $
 */
public interface JdaiPhotoInfoStore {

    public static final int NORTH = 0;
    public static final int EAST  = 1;
    public static final int SOUTH = 2;
    public static final int WEST  = 3;

    /**
     * Get required rotation of the photo. NORTH for no rotation, EAST
     * for 90 degrees right, SOUTH for 180 degrees and WEST for 90 degrees
     * left.
     * @param photo The photo.
     * @return The rotation.
     */
    public int getRotation(JdaiPhoto photo) throws JdaiReadException;
    /**
     * Set required rotation of the photo. NORTH for no rotation, EAST
     * for 90 degrees right, SOUTH for 180 degrees and WEST for 90 degrees
     * left.
     * @param photo The photo.
     * @param rotation The rotation.
     */
    public void setRotation(JdaiPhoto photo, int rotation) throws JdaiReadException, JdaiWriteException;

    /**
     * Get the caption of a photo.
     * @param photo The photo.
     * @return The cation.
     */
    public String getCaption(JdaiPhoto photo) throws JdaiReadException;
    /**
     * Set the caption of a photo.
     * @param photo The photo.
     * @param caption The caption.
     */
    public void setCaption(JdaiPhoto photo, String caption) throws JdaiReadException, JdaiWriteException;

    /**
     * Get the caption of a section.
     * @param section The section.
     * @return The cation.
     */
    public String getCaption(JdaiSection section) throws JdaiReadException;
    /**
     * Set the caption of a section.
     * @param section The section.
     * @param caption The caption.
     */
    public void setCaption(JdaiSection section, String caption) throws JdaiReadException, JdaiWriteException;

    /**
     * Get the keywords of a photo.
     * @param photo The photo.
     * @return The keywords.
     */
    public String getKeywords(JdaiPhoto photo) throws JdaiReadException;
    /**
     * Set the keywords of a photo.
     * @param photo The photo.
     * @param title The keywords.
     */
    public void setKeywords(JdaiPhoto photo, String keywords) throws JdaiReadException, JdaiWriteException;

    /**
     * Get the keywords of a section.
     * @param section The section.
     * @return The keywords.
     */
    public String getKeywords(JdaiSection section) throws JdaiReadException;
    /**
     * Set the keywords of a section.
     * @param section The section.
     * @param title The keywords.
     */
    public void setKeywords(JdaiSection section, String keywords) throws JdaiReadException, JdaiWriteException;

    /**
     * Method deleteInfo. Deletes info about a photo.
     * @param photo The photo to delete info about.
     * @throws JdaiReadException when info could not be read.
     * @throws JdaiWriteException when info could not be written.
     */
	public void deleteInfo(JdaiPhoto photo) throws JdaiReadException, JdaiWriteException;
	
    /**
     * Search for photos by keyword.
     * @param keywords The keywords to search for (separated by spaces).
     * @return Array of photos having all the specified keywords.
     */
    public JdaiPhoto[] searchByKeyword(String keywords) throws JdaiReadException;
}
