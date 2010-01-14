// JdaiSectionTree.java
// $Id: JdaiSectionTree.java 3456 2005-02-05 00:43:33Z rw151951 $

package dk.jdai.gui;

import dk.jdai.model.*;

import java.awt.*;
import java.util.prefs.*;
import javax.swing.*;
import javax.swing.event.*;

/**
 * Provides a tree control of JDAI sections.
 * @author Jeppe Buk (jaybe@jaybe.dk)
 * @version $Revision: 1.2 $
 */
public class JdaiSectionTree {

	/**
	 * The interface to implement if you need events from the JdaiSectionTree control.
	 */
	public interface Delegate {
		/**
		 * The method called when the selection is changed.
		 * @param section The JdaiSection selected.
		 */
		public void selectionChanged(JdaiSection section);
	}

	/**
	 * Provides a TreeSelectionListener class for events.
	 */
	class JdaiSectionTreeSelectionListener implements TreeSelectionListener {

		/**
		* Called by JTree when user selection changes.
		 * @param ev The TreeSelectionEvent that caused this call.
		 */
		public void valueChanged(TreeSelectionEvent ev) {
			JdaiSection newSection =
				(JdaiSection) ((JTree) ev.getSource())
					.getLastSelectedPathComponent();
			if (delegate != null && newSection != null)
				delegate.selectionChanged(newSection);
		}
	}

	private JScrollPane pane;
	private JdaiDroppableTree tree;
	private JdaiSectionTreeModel model;
	private Delegate delegate;

	private static Preferences prefs;
	private static final String PREF_WIDTH = "JdaiSectionTree_width";
	private static final String PREF_HEIGHT = "JdaiSectionTree_height";

	/**
	 * Creates a new instance of JdaiSectionTree
	 */
	public JdaiSectionTree() {
		model = new JdaiSectionTreeModel();
		tree = new JdaiDroppableTree(model);
		tree.setCellRenderer(new JdaiSectionTreeCellRenderer());
		tree.addTreeSelectionListener(new JdaiSectionTreeSelectionListener());
		pane = new JScrollPane(tree);
		delegate = null;

		prefs = Preferences.userNodeForPackage(getClass());
		int width = prefs.getInt(PREF_WIDTH, 200);
		int height = prefs.getInt(PREF_HEIGHT, 150);
		pane.setPreferredSize(new Dimension(width, height));
	}

	/**
	 * Creates a new instance of JdaiSectionTree
	 * @param d The object to receive events through the Delegate interface.
	 */
	public JdaiSectionTree(Delegate d) {
		this();
		delegate = d;
	}

	/**
	 * Returns the presentation of the JdaiSectionTree for use in composite GUIs.
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
	 * Set the root JdaiSection for the tree.
	 * @param section The root section to use.
	 */
	public void setSection(JdaiSection section) {
		model.setRoot(section);
		if (delegate != null)
			delegate.selectionChanged(section);
	}

}
