// JdaiTransferablePhoto.java
// $Id: JdaiTransferablePhoto.java 3456 2005-02-05 00:43:33Z rw151951 $

package dk.jdai.model;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Arrays;

/**
 * Provides a transferable JDAI photo for dnd and copy/paste.
 * @author jaybe
 * @version $Revision: 1.2 $
 */
public class JdaiTransferablePhoto implements Transferable {
	public static final DataFlavor PHOTO_FLAVOR =
		new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType, "Photo");
	private Object[] photos;
	private DataFlavor[] flavors = { PHOTO_FLAVOR };

	public JdaiTransferablePhoto(Object[] photos) {
		this.photos = photos;
	}

	/**
	 * @see java.awt.datatransfer.Transferable#getTransferDataFlavors()
	 */
	public DataFlavor[] getTransferDataFlavors() {
		return flavors;
	}

	/**
	 * @see java.awt.datatransfer.Transferable#isDataFlavorSupported(java.awt.datatransfer.DataFlavor)
	 */
	public boolean isDataFlavorSupported(DataFlavor flavor) {
		return Arrays.asList(flavors).contains(flavor);
	}

	/**
	 * @see java.awt.datatransfer.Transferable#getTransferData(java.awt.datatransfer.DataFlavor)
	 */
	public Object getTransferData(DataFlavor flavor)
		throws UnsupportedFlavorException, IOException {
		if (flavor == PHOTO_FLAVOR) {
			return photos;
		} else {
			throw new UnsupportedFlavorException(flavor);
		}
	}
}
