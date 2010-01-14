//JdaiPhotoListModel.java
//$Id: JdaiPhotoListModel.java 3456 2005-02-05 00:43:33Z rw151951 $

package dk.jdai.gui;

import javax.swing.AbstractListModel;
import javax.swing.JOptionPane;

import dk.jdai.model.JdaiPhoto;
import dk.jdai.model.JdaiPhotoInfoStore;
import dk.jdai.model.JdaiReadException;
import dk.jdai.model.JdaiWriteException;

/**
 * Provides a ListModel implementation for JDAI sections. This class is
 * undocumented since the methods are well known from the ListModel interface.
 * @author Jeppe Buk (jaybe@jaybe.dk)
 * @version $Revision: 1.2 $
 */
public class JdaiPhotoListModel extends AbstractListModel {

	private JdaiPhoto[] photos;

	/**
	 * Constructor for JdaiPhotoListModel.
	 */
	public JdaiPhotoListModel() {
		super();
		photos = null;
	}

	public void setPhotos(JdaiPhoto[] photos) {
		int oldSize = getSize();
		this.photos = photos;
		int newSize = getSize();
		if (oldSize > 0)
			fireIntervalRemoved(this, 0, oldSize - 1);
		if (newSize > 0)
			fireIntervalAdded(this, 0, newSize - 1);
	}

	public JdaiPhoto[] getPhotos() {
		return photos;
	}

	/**
	 * @see javax.swing.ListModel#getSize()
	 */
	public int getSize() {
		if (photos == null)
			return 0;
		else
			return photos.length;
	}

	/**
	 * @see javax.swing.ListModel#getElementAt(int)
	 */
	public Object getElementAt(int index) {
		if (photos == null || index >= getSize()) {
			return null;
		} else {
			return photos[index];
		}
	}

	/**
	 * Method photoUpdated. Must be called after updating (typically rotating) a
	 * photo in the model.
	 * @param index index of the updated photo
	 */
	public void photoUpdated(int index) {
		fireContentsChanged(this, index, index);
	}

	/**
	 * Method rotateRight. Rotates the photo at index clockwise.
	 * @param index
	 */
	public void rotateRight(int index) {
		try {
			int newRot;
			int oldRot =
				photos[index].getSection().getInfoStore().getRotation(
					photos[index]);
			if (oldRot == JdaiPhotoInfoStore.WEST)
				newRot = JdaiPhotoInfoStore.NORTH;
			else
				newRot = oldRot + 1;
			photos[index].getSection().getInfoStore().setRotation(
				photos[index],
				newRot);
		} catch (JdaiReadException e) {
			JdaiGuiHelpers.reportException("Unable to read infostore", e);
		} catch (JdaiWriteException e) {
			JdaiGuiHelpers.reportException("Unable to write infostore", e);
		}
		photos[index].refreshThumbnail();
		photoUpdated(index);
	}

	/**
	 * Method rotateLeft. Rotates the photo at index counter-clockwise.
	 * @param index
	 */
	public void rotateLeft(int index) {
		try {
			int newRot;
			int oldRot =
				photos[index].getSection().getInfoStore().getRotation(
					photos[index]);
			if (oldRot == JdaiPhotoInfoStore.NORTH)
				newRot = JdaiPhotoInfoStore.WEST;
			else
				newRot = oldRot - 1;
			photos[index].getSection().getInfoStore().setRotation(
				photos[index],
				newRot);
		} catch (JdaiReadException e) {
			JdaiGuiHelpers.reportException("Unable to read infostore", e);
		} catch (JdaiWriteException e) {
			JdaiGuiHelpers.reportException("Unable to write infostore", e);
		}
		photos[index].refreshThumbnail();
		photoUpdated(index);
	}

	/**
	 * Method deletePhotos. Deletes photos denoted by the sorted(!) array of
	 * indices
	 * @param indices Sorted array of indices of photos to delete
	 */
	public void deletePhotos(int[] indices) {
		JdaiPhoto[] newPhotos = new JdaiPhoto[photos.length - indices.length];
		int deleteIndex = 0;
		for (int i = 0; i < photos.length; i++) {
			boolean deleted = true;
			if (deleteIndex == indices.length || i < indices[deleteIndex]) {
				newPhotos[i - deleteIndex] = photos[i];
			} else if (i == indices[deleteIndex]) {
				try {
					photos[i].delete();
				} catch (JdaiWriteException e) {
					break;
				} catch (JdaiReadException e) {
					// ignore unreadable infostore
				}
				deleteIndex++;
			}
		}
		if (deleteIndex < indices.length) {
			JOptionPane.showMessageDialog(
				null,
				"One or more photos not deleted",
				"Warning",
				JOptionPane.WARNING_MESSAGE);
		}
		photos = newPhotos;
		fireContentsChanged(this, indices[0], indices[indices.length - 1]);
	}
}
