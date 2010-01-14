// JdaiSection.java
// $Id: JdaiSection.java 3456 2005-02-05 00:43:33Z rw151951 $

package dk.jdai.model;

/**
 * The interface a Section class must implement.
 * @author Jeppe Buk (jaybe@jaybe.dk)
 * @version $Revision: 1.2 $
 */
public interface JdaiSection extends Comparable {

    /**
     * Get the name of this section.
     * @return The name.
     */
    public String getName();

    /**
     * Get the subsections contained in this section.
     * @return The subsections.
     */
    public JdaiSection[] getSubSections();

    /**
     * Get the photos contained in this section.
     * @return The photos.
     */
    public JdaiPhoto[] getPhotos();

    /**
     * Get the information store associated with this section.
     * @return The information store.
     */
    public JdaiPhotoInfoStore getInfoStore();

    /**
     * Add a photo to this section.
     * @param photo The photo to add
     */
    public void addPhoto(JdaiPhoto photo) throws JdaiReadException, JdaiWriteException;

    /**
     * Create a new subsection.
     * @param name The name of the new subsection
     */
    public void createSubSection(String name);
}
