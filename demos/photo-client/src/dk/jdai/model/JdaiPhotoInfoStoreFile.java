// JdaiPhotoInfoStoreFile.java
// $Id: JdaiPhotoInfoStoreFile.java 3456 2005-02-05 00:43:33Z rw151951 $

package dk.jdai.model;

import java.io.*;

import org.dom4j.*;
import org.dom4j.io.*;

/**
 * A file based (XML) Photo Information Store.
 * @author Jeppe Buk (jaybe@jaybe.dk)
 * @version $Revision: 1.2 $
 */
public class JdaiPhotoInfoStoreFile implements JdaiPhotoInfoStore {

    private String xmlFileName;
    private Document domDoc;

    /**
     * Creates a new instance of JdaiPhotoInfoStoreFile.
     */
    public JdaiPhotoInfoStoreFile(String fileName) {
        xmlFileName = fileName;
        domDoc = null;
    }

    private void printInfoStore() {
        try {
            (new XMLWriter(System.out, OutputFormat.createPrettyPrint())).write(domDoc);
        } catch (Exception e) {
            System.out.println("JdaiPhotoInfoStoreFile (unhandled): " + e.getMessage());
        }
    }

    private void initInfoStore() throws JdaiReadException {
        File xmlFile = new File(xmlFileName);
        if (xmlFile.exists() && xmlFile.isFile()) {
            try {
                domDoc = (new SAXReader()).read(new FileInputStream(xmlFile));
            } catch (Exception e) {
                throw new JdaiReadException("JdaiPhotoInfoStoreFile: " + e.getMessage());
            }
        } else {
            try {
                domDoc = DocumentHelper.createDocument();
                Element root = domDoc.addElement("section").addAttribute("version", "1.0");
            } catch (Exception e) {
                System.out.println("JdaiPhotoInfoStoreFile (Unhandled): " + e.getMessage());
            }
        }
    }

    private String getAttrValue(JdaiPhoto photo, String attrName) throws JdaiReadException {
        if (domDoc == null)
            initInfoStore();
        Element elm = (Element) domDoc.selectSingleNode("/section/photo[@id='" + photo.getId() + "']");
        Attribute attr = null;
        
        if (elm != null)
            attr = elm.attribute(attrName);
        if (attr != null)
            return attr.getValue();
        else
            return "";
    }

    private void setAttrValue(JdaiPhoto photo, String attrName, String value)
        throws JdaiReadException, JdaiWriteException {
        if (domDoc == null)
            initInfoStore();
        Element elm = (Element) domDoc.selectSingleNode("/section/photo[@id='" + photo.getId() + "']");
        if (elm == null)
            elm = domDoc.getRootElement().addElement("photo").addAttribute("id", photo.getId());
        /* overwrites if the attribute exists */
        elm.addAttribute(attrName, value);

        /* save to disk */
        try {
            (new XMLWriter(new FileOutputStream(xmlFileName), OutputFormat.createPrettyPrint())).write(domDoc);
        } catch (Exception e) {
            throw new JdaiWriteException("JdaiPhotoInfoStoreFile: " + e.getMessage());
        }
    }

    private void deleteNode(JdaiPhoto photo)
        throws JdaiReadException, JdaiWriteException {
        if (domDoc == null)
            initInfoStore();
        Element elm = (Element) domDoc.selectSingleNode("/section/photo[@id='" + photo.getId() + "']");
        if (elm != null) {
        	elm.detach();
        	/* save to disk */
        	try {
            	(new XMLWriter(new FileOutputStream(xmlFileName), OutputFormat.createPrettyPrint())).write(domDoc);
        	} catch (Exception e) {
            	throw new JdaiWriteException("JdaiPhotoInfoStoreFile: " + e.getMessage());
        	}
        }
    }

    private String getAttrValue(String attrName) throws JdaiReadException {
        if (domDoc == null)
            initInfoStore();
        Element elm = (Element) domDoc.getRootElement();
        Attribute attr = null;
        if (elm != null)
            attr = elm.attribute(attrName);
        if (attr != null)
            return attr.getValue();
        else
            return "";
    }

    private void setAttrValue(String attrName, String value) throws JdaiReadException, JdaiWriteException {
        if (domDoc == null)
            initInfoStore();
        Element elm = (Element) domDoc.getRootElement();
        elm.addAttribute(attrName, value);

        /* save to disk */
        try {
            (new XMLWriter(new FileOutputStream(xmlFileName), OutputFormat.createPrettyPrint())).write(domDoc);
        } catch (Exception e) {
            throw new JdaiWriteException("JdaiPhotoInfoStoreFile: " + e.getMessage());
        }
    }

    /**
     * Get required rotation of the photo. NORTH for no rotation, EAST
     * for 90 degrees right, SOUTH for 180 degrees and WEST for 90 degrees
     * left.
     * @param photo The photo.
     * @return The rotation.
     */
    public int getRotation(JdaiPhoto photo) throws JdaiReadException {
        String rotStr = getAttrValue(photo, "rotation");
        if (rotStr != null) {
            try {
                return Integer.decode(rotStr).intValue();
            } catch (NumberFormatException e) {
                return NORTH;
            }
        } else {
            return NORTH;
        }
    }
    /**
     * Set required rotation of the photo. NORTH for no rotation, EAST
     * for 90 degrees right, SOUTH for 180 degrees and WEST for 90 degrees
     * left.
     * @param photo The photo.
     * @param rotation The rotation.
     */
    public void setRotation(JdaiPhoto photo, int rotation) throws JdaiReadException, JdaiWriteException {
        if (rotation >= NORTH && rotation <= WEST)
            setAttrValue(photo, "rotation", "" + rotation);
    }

    /**
     * Get the caption of a photo.
     * @param photo The photo.
     * @return The cation.
     */
    public String getCaption(JdaiPhoto photo) throws JdaiReadException {
        return getAttrValue(photo, "caption");
    }
    /**
     * Set the caption of a photo.
     * @param photo The photo.
     * @param caption The caption.
     */
    public void setCaption(JdaiPhoto photo, String caption) throws JdaiReadException, JdaiWriteException {
        setAttrValue(photo, "caption", caption);
    }

    /**
     * Get the caption of a section.
     * @param section The section (unused in this implementation).
     * @return The cation.
     */
    public String getCaption(JdaiSection section) throws JdaiReadException {
        return getAttrValue("caption");
    }
    /**
     * Set the caption of a section.
     * @param section The section (unused in this implementation).
     * @param caption The caption.
     */
    public void setCaption(JdaiSection section, String caption) throws JdaiReadException, JdaiWriteException {
        setAttrValue("caption", caption);
    }

    /**
     * Get the keywords of a photo.
     * @param photo The photo.
     * @return The keywords.
     */
    public String getKeywords(JdaiPhoto photo) throws JdaiReadException {
        return getAttrValue(photo, "keywords");
    }
    /**
     * Set the keywords of a photo.
     * @param photo The photo.
     * @param keywords The keywords.
     */
    public void setKeywords(JdaiPhoto photo, String keywords) throws JdaiReadException, JdaiWriteException {
        setAttrValue(photo, "keywords", keywords);
    }

    /**
     * Get the keywords of a section.
     * @param section The section (unused in this implementation).
     * @return The keywords.
     */
    public String getKeywords(JdaiSection section) throws JdaiReadException {
        return getAttrValue("keywords");
    }
    /**
     * Set the keywords of a section (unused in this implementation).
     * @param section The section.
     * @param keywords The keywords.
     */
    public void setKeywords(JdaiSection section, String keywords) throws JdaiReadException, JdaiWriteException {
        setAttrValue("keywords", keywords);
    }

    /**
     * Search for photos by keyword.
     * @param keywords The keywords to search for (separated by spaces).
     * @return Array of photos having all the specified keywords.
     */
    public JdaiPhoto[] searchByKeyword(String keywords) throws JdaiReadException {
        return new JdaiPhotoFile[0];
    }

    /**
     * @see dk.jdai.model.JdaiPhotoInfoStore#deleteInfo(dk.jdai.model.JdaiPhoto)
     */
    public void deleteInfo(JdaiPhoto photo) throws JdaiReadException, JdaiWriteException {
		deleteNode(photo);    	
    }

}
