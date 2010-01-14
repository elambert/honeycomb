// JdaiPhotoList.java
// $Id: JdaiPhotoList.java 11627 2007-10-31 01:31:41Z ds158322 $

package dk.jdai.gui;

import dk.jdai.model.*;

import java.awt.*;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.prefs.*;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.JFileChooser;
import com.drew.imaging.jpeg.JpegProcessingException;
import java.nio.channels.Pipe;
import java.io.ByteArrayInputStream;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.Channels;
import java.io.FileInputStream;
import java.util.Map;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileFilter;

import com.sun.honeycomb.fs.HCFile;
import com.sun.honeycomb.jdai.HCSection;
import com.sun.honeycomb.exif.ExifExtract;
import dk.jdai.cmdline.Cmdline;

/**
 * Provides a list control of JDAI photos.
 * @author Jeppe Buk (jaybe@jaybe.dk)
 * @version $Revision: 1.3 $
 */
public class JdaiPhotoList
	implements ActionListener, DragSourceListener, DragGestureListener {

	/**
	 * The interface to implement if you need events from the JdaiPhotoList control.
	 */
	public interface Delegate {
		/**
		 * The method called when the selection is changed.
		 * @param photo The JdaiPhoto selected.
		 */
		public void selectionChanged(JdaiPhoto photo);

		/**
		 * The method called when a new photo is selected for viewing.
		 * @param photos The current array of photos
		 * @param index The index of the selected photo
		 * @param slideshow Flag indicating if slideshow mode is desired
		 */
		public void selectedForViewing(
			JdaiPhoto[] photos,
			int index,
			boolean slideshow);

		/**
		 * The method called when a new photo is selected for editing.
		 * @param photos The current array of photos
		 * @param index The index of the selected photo
		 */
		public void selectedForEditing(JdaiPhoto[] photos, int index);
	}

	private static final String UPLOAD_PATH = "JdaiExplorer_uploadpath";
    
	private JdaiSection section;
	private int noPhotos = 0;
	private JScrollPane pane;
	private JList list;
	private JdaiPhotoListModel model;
	private DragSource dragSource;
	private Delegate delegate;
	private static final int THUMB_SIZE = 190;

	private JPopupMenu popup;

	private static Preferences prefs;
	private static final String PREF_WIDTH = "JdaiPhotoList_width";
	private static final String PREF_HEIGHT = "JdaiPhotoList_height";

	/**
	 * Creates a new instance of JdaiPhotoList
	 */
	public JdaiPhotoList() {
		section = null;
		delegate = null;

		// init JList
		list = new JList();
		pane = new JScrollPane(list);
		list.setModel(model = new JdaiPhotoListModel());
		JdaiPhotoListCellRenderer renderer = new JdaiPhotoListCellRenderer();
		list.setCellRenderer(renderer);
		list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
		JLabel proto = new JLabel();
		proto.setPreferredSize(new Dimension(THUMB_SIZE, THUMB_SIZE));
		list.setPrototypeCellValue(proto);
		list.setVisibleRowCount(0); // Makes cells flow like wrapped text

		// init DnD
		dragSource = new DragSource();
		dragSource.createDefaultDragGestureRecognizer(
			list,
			DnDConstants.ACTION_COPY_OR_MOVE,
			this);

		// Events
		initPopup();
		list.addMouseListener(new MyMouseListener());
		list.addListSelectionListener(new JdaiPhotoListSelectionListener());
		list.addKeyListener(new MyKeyListener());

		// Actions
		JdaiActions.instance().uploadAction().addActionListener(this);
		JdaiActions.instance().viewAction().addActionListener(this);
		JdaiActions.instance().editAction().addActionListener(this);
		JdaiActions.instance().rotateRightAction().addActionListener(this);
		JdaiActions.instance().rotateLeftAction().addActionListener(this);
		JdaiActions.instance().deleteAction().addActionListener(this);
		JdaiActions.instance().slideshowAction().addActionListener(this);

		// prefs
		prefs = Preferences.userNodeForPackage(getClass());
		//int width = prefs.getInt(PREF_WIDTH, 400);
		//int height = prefs.getInt(PREF_HEIGHT, 150);
		//pane.setPreferredSize(new Dimension(width, height));
	}

	/**
	 * Creates a new instance of JdaiPhotoList with a delegate.
	 * @param delegate The delegate object.
	 */
	public JdaiPhotoList(Delegate delegate) {
		this();
		this.delegate = delegate;
	}

	private void initPopup() {
		popup = new JPopupMenu();
		popup.add(new JMenuItem(JdaiActions.instance().viewAction()));
		popup.add(new JMenuItem(JdaiActions.instance().editAction()));
		popup.addSeparator();
		popup.add(new JMenuItem(JdaiActions.instance().slideshowAction()));
		popup.addSeparator();
		popup.add(new JMenuItem(JdaiActions.instance().rotateLeftAction()));
		popup.add(new JMenuItem(JdaiActions.instance().rotateRightAction()));
		popup.addSeparator();
		popup.add(new JMenuItem(JdaiActions.instance().deleteAction()));
	}

	class MyMouseListener extends MouseAdapter {
		public void mousePressed(MouseEvent e) {
			maybeShowPopup(e);
		}

		public void mouseReleased(MouseEvent e) {
			maybeShowPopup(e);
			if (e.getButton() == MouseEvent.BUTTON1
				&& e.getClickCount() == 2) {
				doSelectedForViewing();
			}
		}

		private void maybeShowPopup(MouseEvent e) {
			int x = e.getX();
			int y = e.getY();
			int eventIndex = list.locationToIndex(new Point(x, y));
			if (e.isPopupTrigger()
				&& list.getCellBounds(eventIndex, eventIndex).contains(x, y)) {
				if (!list.isSelectedIndex(eventIndex)) {
					selectPhoto(eventIndex);
				}
				popup.show(e.getComponent(), x, y);
			}
		}
	}

	/**
	 * Provides a ListSelectionListener class for events.
	 */
	class JdaiPhotoListSelectionListener implements ListSelectionListener {

		/**
		* Called by JList when user selection changes.
		 * @param ev The ListSelectionEvent that caused this call.
		 */
		public void valueChanged(ListSelectionEvent ev) {
			if (!ev.getValueIsAdjusting() && delegate != null) {
				if (list.getSelectedIndices().length == 1) {
					JdaiPhoto newPhoto =
						(JdaiPhoto) ((JList) ev.getSource()).getSelectedValue();
					if (newPhoto != null) {
						delegate.selectionChanged(newPhoto);
					}
				} else {
					delegate.selectionChanged(null);
				}
			}
		}
	}

	class MyKeyListener extends KeyAdapter {
		public void keyPressed(KeyEvent e) {
			int key = e.getKeyCode();
			int modifiers = e.getModifiers();
			if (modifiers == 0) {
				// No modifiers
				switch (key) {
					case KeyEvent.VK_ENTER :
						doSelectedForViewing();
						break;
					case KeyEvent.VK_DELETE :
						doDelete();
						break;
				}
			} else if (modifiers == KeyEvent.ALT_MASK) {
				// Alt modifiers
				switch (key) {
					case KeyEvent.VK_RIGHT :
						doRotateRight();
						break;
					case KeyEvent.VK_LEFT :
						doRotateLeft();
						break;
				}
			} else if (modifiers == KeyEvent.SHIFT_MASK) {
				// Shift modifiers
				switch (key) {
					case KeyEvent.VK_F10 :
						doPopup();
						break;
				}
			} else if (modifiers == KeyEvent.CTRL_MASK) {
				// Ctrl modifiers
				switch (key) {
				}
			}
		}
	}

    private void doUpload() {
        String uploadPath = JdaiExplorer.prefs.get(UPLOAD_PATH, "");
        JFileChooser fileChooser = null;
        JCheckBox checkBox = new JCheckBox("Upload subdirectories");
        if (uploadPath.length() == 0) {
            fileChooser = new JFileChooser();
        } else {
            File uploadPathFile = new File(uploadPath);
            if (!uploadPathFile.exists()) {
                fileChooser = new JFileChooser();
            } else {
                fileChooser = new JFileChooser(uploadPathFile);
            }
        }
        fileChooser.setAccessory(checkBox);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        if (fileChooser.showDialog(JdaiExplorer.explorer.getFrame(),
                                   "Upload !") == JFileChooser.APPROVE_OPTION) {

            File file = fileChooser.getSelectedFile();
            if ((checkBox.isSelected()) && (!file.isDirectory())) {
                file = file.getParentFile();
            }
            JdaiExplorer.prefs.put(UPLOAD_PATH, file.getParentFile().getAbsolutePath());
            if (file.isDirectory()) {
                if (checkBox.isSelected()) {
                    Cmdline.uploadDirectory(HCFile.getRepository(), file, -1);
                } else {
                    Cmdline.uploadDirectory(HCFile.getRepository(), file, 1);
                }
            } else {
                Cmdline.uploadSingleFile(HCFile.getRepository(), file);
            }

            JdaiExplorer.explorer.refreshView();
        }
    }

	private void doSelectedForViewing() {
		JdaiPhoto[] photos = model.getPhotos();
		int photoIndex = list.getSelectedIndex();
		if (delegate != null && photos != null && photoIndex >= 0)
			delegate.selectedForViewing(photos, photoIndex, false);
	}

	private void doSelectedForSlideshow() {
		JdaiPhoto[] photos = model.getPhotos();
		int photoIndex = list.getSelectedIndex();
		if (delegate != null && photos != null && photoIndex >= 0)
			delegate.selectedForViewing(photos, photoIndex, true);
	}

	private void doSelectedForEditing() {
		JdaiPhoto[] photos = model.getPhotos();
		int photoIndex = list.getSelectedIndex();
		if (delegate != null && photos != null && photoIndex >= 0)
			delegate.selectedForEditing(photos, photoIndex);
	}

	private void doPopup() {
		int selectIndex = list.getSelectedIndex();
		if (selectIndex >= 0) {
			Point p = list.indexToLocation(selectIndex);
			popup.show(list, p.x, p.y);
		}
	}

	/**
	 * Returns the presentation of the JdaiPhotoList for use in composite GUIs.
	 * @return The Component.
	 */
	public Component getPresentation() {
		return pane;
	}

	/**
	 * Saves the preferences (should be called when the application quits).
	 */
	public void savePreferences() {
		//prefs.putInt(PREF_WIDTH, pane.getWidth());
		//prefs.putInt(PREF_HEIGHT, pane.getHeight());
	}

	/**
	 * Set the array of JDAI photos to use in the list.
	 * @param photos The array.
	 */
	public void setPhotoArray(JdaiPhoto[] photos) {
		if (photos != null)
			noPhotos = photos.length;
		else
			noPhotos = 0;
		model.setPhotos(photos);
		list.ensureIndexIsVisible(0);
	}

	/**
	 * Set the JDAI section to use in the list.
	 * @param section The section.
	 */
	public void setSection(JdaiSection section) {
		this.section = section;
		if (this.section != null)
			setPhotoArray(section.getPhotos());
	}

	/**
	 * Method size.
	 * @return int the size of the photo list (no photos).
	 */
	public int getPhotoCount() {
		return noPhotos;
	}

	/**
	 * Set the selected photo programatically
	 * @param i The index
	 */
	public void selectPhoto(int i) {
		list.setSelectedIndex(i);
	}

	/**
	 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
	 */
	public void actionPerformed(ActionEvent e) {
		String command = e.getActionCommand();
		if (command
			.equals(JdaiActions.instance().uploadAction().getActionCommand())) {
            doUpload();
        } else if (command
			.equals(JdaiActions.instance().viewAction().getActionCommand())) {
			doSelectedForViewing();
		} else if (
			command.equals(
				JdaiActions.instance().editAction().getActionCommand())) {
			doSelectedForEditing();
		} else if (
			command.equals(
				JdaiActions
					.instance()
					.rotateLeftAction()
					.getActionCommand())) {
			doRotateLeft();
		} else if (
			command.equals(
				JdaiActions
					.instance()
					.rotateRightAction()
					.getActionCommand())) {
			doRotateRight();
		} else if (
			command.equals(
				JdaiActions.instance().deleteAction().getActionCommand())) {
			doDelete();
		} else if (
			command.equals(
				JdaiActions.instance().slideshowAction().getActionCommand())) {
			doSelectedForSlideshow();
		}
	}

	private void doRotateRight() {
		int[] photoIndices = list.getSelectedIndices();
		if (photoIndices.length > 0) {
			for (int i = 0; i < photoIndices.length; i++) {
				model.rotateRight(photoIndices[i]);
			}
		}
	}

	private void doRotateLeft() {
		int[] photoIndices = list.getSelectedIndices();
		if (photoIndices.length > 0) {
			for (int i = 0; i < photoIndices.length; i++) {
				model.rotateLeft(photoIndices[i]);
			}
		}
	}

	private void doDelete() {
		int[] photoIndices = list.getSelectedIndices();
		if (photoIndices.length > 0) {
			String photoMsg;
			if (photoIndices.length == 1) {
				photoMsg = model.getPhotos()[photoIndices[0]].getId();
			} else {
				photoMsg = photoIndices.length + " photos";
			}
			int choice =
				JOptionPane.showConfirmDialog(
					list,
					"Delete " + photoMsg + "?",
					"Confirm",
					JOptionPane.YES_NO_OPTION);
			if (choice == JOptionPane.YES_OPTION) {
				model.deletePhotos(photoIndices);
			}
		}
	}

	/**
	 * @see java.awt.dnd.DragSourceListener#dragEnter(java.awt.dnd.DragSourceDragEvent)
	 */
	public void dragEnter(DragSourceDragEvent dsde) {
		updateCursor(dsde);
	}

	/**
	 * @see java.awt.dnd.DragSourceListener#dragOver(java.awt.dnd.DragSourceDragEvent)
	 */
	public void dragOver(DragSourceDragEvent dsde) {
		updateCursor(dsde);
	}

	/**
	 * @see java.awt.dnd.DragSourceListener#dropActionChanged(java.awt.dnd.DragSourceDragEvent)
	 */
	public void dropActionChanged(DragSourceDragEvent dsde) {
		updateCursor(dsde);
	}

	/**
	 * @see java.awt.dnd.DragSourceListener#dragExit(java.awt.dnd.DragSourceEvent)
	 */
	public void dragExit(DragSourceEvent dse) {
		dse.getDragSourceContext().setCursor(DragSource.DefaultMoveNoDrop);
	}

	/**
	 * @see java.awt.dnd.DragSourceListener#dragDropEnd(java.awt.dnd.DragSourceDropEvent)
	 */
	public void dragDropEnd(DragSourceDropEvent dsde) {
		setSection(section);
	}

	/**
	 * @see java.awt.dnd.DragGestureListener#dragGestureRecognized(java.awt.dnd.DragGestureEvent)
	 */
	public void dragGestureRecognized(DragGestureEvent dge) {
		Object[] values = list.getSelectedValues();
		dragSource.startDrag(
			dge,
			DragSource.DefaultMoveNoDrop,
			new JdaiTransferablePhoto(values),
			this);
	}

	private void updateCursor(DragSourceDragEvent dsde) {
		int action = dsde.getDropAction();
		if (action == DnDConstants.ACTION_COPY) {
			dsde.getDragSourceContext().setCursor(DragSource.DefaultCopyDrop);
		} else {
			if (action == DnDConstants.ACTION_MOVE) {
				dsde.getDragSourceContext().setCursor(
					DragSource.DefaultMoveDrop);
			} else {
				dsde.getDragSourceContext().setCursor(
					DragSource.DefaultMoveNoDrop);
			}
		}
	}

}
