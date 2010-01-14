// JdaiPhotoInfoPane.java
// $Id: JdaiPhotoInfoPane.java 3456 2005-02-05 00:43:33Z rw151951 $

package dk.jdai.gui;

import dk.jdai.model.*;

import java.awt.*;
import java.util.prefs.*;
import javax.swing.*;

/**
 * Provides a pane to display a info from a JDAI photo.
 * @author Jeppe Buk (jaybe@jaybe.dk)
 * @version $Revision: 1.2 $
 */
public class JdaiPhotoInfoPane {

	private JScrollPane pane;
	private JTable table;
	private JdaiPhotoInfoTableModel model;

	private static Preferences prefs;
	private static final String PREF_WIDTH = "JdaiPhotoInfoPane_width";
	private static final String PREF_HEIGHT = "JdaiPhotoInfoPane_height";

	/**
	 * Creates a new instance of JdaiPhotoInfoPane
	 */
	public JdaiPhotoInfoPane() {
		table = new JTable();
		model = new JdaiPhotoInfoTableModel();
		table.setModel(model);
		pane = new JScrollPane(table);
		
		prefs = Preferences.userNodeForPackage(getClass());
		int width = prefs.getInt(PREF_WIDTH, 200);
		int height = prefs.getInt(PREF_HEIGHT, 175);
		pane.setPreferredSize(new Dimension(width, 0)); // let system decide height
	}

	/**
	 * Returns the presentation of the JdaiPhotoInfoPane for use in composite GUIs.
	 * @return The Component.
	 */
	public Component getPresentation() {
		return pane;
	}

	/**
	 * Saves the preferences (should be called when the application quits).
	 */
	public void savePreferences() {
		prefs.putInt(PREF_WIDTH, pane.getWidth());
		prefs.putInt(PREF_HEIGHT, pane.getHeight());
	}

	/**
	 * Set the JDAI photo to display.
	 * @param photos The photo.
	 */
	public void setPhoto(JdaiPhoto photo) {
		model.setPhoto(photo);
	}

}
