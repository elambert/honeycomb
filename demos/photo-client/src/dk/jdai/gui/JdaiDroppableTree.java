// JdaiDroppableTree.java
// $Id: JdaiDroppableTree.java 3456 2005-02-05 00:43:33Z rw151951 $

package dk.jdai.gui;

import java.awt.Point;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;

import javax.swing.JTree;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import dk.jdai.model.JdaiPhoto;
import dk.jdai.model.JdaiSection;
import dk.jdai.model.JdaiTransferablePhoto;

/**
 * Provides a droppable tree for JDAI.
 * @author jaybe
 * @version $Revision: 1.2 $
 */
public class JdaiDroppableTree extends JTree implements DropTargetListener {

	private DropTarget dropTarget;

	/**
	 * Constructor for JdaiDroppableTree.
	 * @param newModel The tree model to use
	 */
	public JdaiDroppableTree(TreeModel newModel) {
		super(newModel);
		dropTarget =
			new DropTarget(this, DnDConstants.ACTION_COPY_OR_MOVE, this);
	}

	/**
	 * @see java.awt.dnd.DropTargetListener#dragEnter(java.awt.dnd.DropTargetDragEvent)
	 */
	public void dragEnter(DropTargetDragEvent dtde) {
		updateState(dtde);
	}

	/**
	 * @see java.awt.dnd.DropTargetListener#dragOver(java.awt.dnd.DropTargetDragEvent)
	 */
	public void dragOver(DropTargetDragEvent dtde) {
		updateState(dtde);
	}

	/**
	 * @see java.awt.dnd.DropTargetListener#dropActionChanged(java.awt.dnd.DropTargetDragEvent)
	 */
	public void dropActionChanged(DropTargetDragEvent dtde) {
		updateState(dtde);
	}

	/**
	 * @see java.awt.dnd.DropTargetListener#dragExit(java.awt.dnd.DropTargetEvent)
	 */
	public void dragExit(DropTargetEvent dte) {
	}

	/**
	 * @see java.awt.dnd.DropTargetListener#drop(java.awt.dnd.DropTargetDropEvent)
	 */
	public void drop(DropTargetDropEvent dtde) {
		try {
			int action = dtde.getDropAction();
			Transferable transferable = dtde.getTransferable();
			Point pt = dtde.getLocation();
			if (transferable
				.isDataFlavorSupported(JdaiTransferablePhoto.PHOTO_FLAVOR)) {
				JdaiSection dropSection = sectionAtPoint(pt);
				Object[] photos =
					(Object[]) transferable.getTransferData(
						JdaiTransferablePhoto.PHOTO_FLAVOR);
				if ((action == DnDConstants.ACTION_COPY
					|| action == DnDConstants.ACTION_MOVE)) {
					for (int i = 0; i < photos.length; i++) {
						JdaiPhoto photo = (JdaiPhoto) photos[i];
						if (!dropSection.equals(photo.getSection())) {
							dropSection.addPhoto(photo);
							if (action == DnDConstants.ACTION_MOVE)
								photo.delete();
						}
						dtde.acceptDrop(action);
						dtde.dropComplete(true);
					}
				} else {
					dtde.rejectDrop();
					dtde.dropComplete(false);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			dtde.rejectDrop();
			dtde.dropComplete(false);
		}
	}

	private JdaiSection sectionAtPoint(Point p) {
		TreePath path = getPathForLocation(p.x, p.y);
		if (path == null)
			return null;
		return (JdaiSection) path.getLastPathComponent();
	}

	private void updateState(DropTargetDragEvent dtde) {
		JdaiSection dropSection = sectionAtPoint(dtde.getLocation());
		if (dropSection != null
			&& dtde.isDataFlavorSupported(JdaiTransferablePhoto.PHOTO_FLAVOR))
			dtde.acceptDrag(dtde.getDropAction());
		else
			dtde.rejectDrag();
	}
}
