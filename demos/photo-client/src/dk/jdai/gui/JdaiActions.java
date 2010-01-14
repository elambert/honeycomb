// JdaiActions.java
// $Id: JdaiActions.java 3456 2005-02-05 00:43:33Z rw151951 $

package dk.jdai.gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.util.*;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;

import javax.swing.event.EventListenerList;
import com.sun.honeycomb.fs.HCFile;
import java.awt.Component;

/**
 * Provides all the actions for JDAI.
 */

public class JdaiActions {
	static java.util.ResourceBundle labels =
		java.util.ResourceBundle.getBundle(
			"dk.jdai.gui.JdaiActionBundle",
			java.util.Locale.getDefault());

	private static JdaiActions singleton = null;
	private Map actionMap;

	protected JdaiActions() {
		actionMap = new HashMap();
	}

	public static JdaiActions instance() {
		if (singleton == null)
			singleton = new JdaiActions();
		return singleton;
	}

	/**
	 * Abstract Action for the JDAI.
	 * @author Jeppe Buk (jaybe@jaybe.dk)
	 * @version $Revision: 1.3 $
	 */
	public abstract class JdaiAbstractAction extends AbstractAction {

		private EventListenerList listeners;

		/**
		 * The key used for storing a large icon for the action,
		 * used for toolbar buttons.
		 * <p>
		 * Note: Eventually this key belongs in the javax.swing.Action interface.
		 */
		public static final String LARGE_ICON_STR = "LargeIcon";

		/** 
		 * Gets the value from the key Action.ACTION_COMMAND_KEY
		 */
		public String getActionCommand() {
			return (String) getValue(Action.ACTION_COMMAND_KEY);
		}

		/** 
		 * Gets the value from the key Action.SHORT_DESCRIPTION
		 */
		public String getShortDescription() {
			return (String) getValue(Action.SHORT_DESCRIPTION);
		}

		/** 
		 * Gets the value from the key Action.LONG_DESCRIPTION
		 */
		public String getLongDescription() {
			return (String) getValue(Action.LONG_DESCRIPTION);
		}

		/* Should finish the implementation and add get/set methods for all the 
		 * javax.swing.Action keys:
		    
		 Action.NAME
		 Action.SMALL_ICON
		 ActionConstants.LARGE_ICON
		 Action.MNEMONIC_KEY
		*/

		/** 
		 * Forwards the ActionEvent to the registered listener.
		 */
		public void actionPerformed(ActionEvent evt) {
			if (listeners != null) {
				Object[] listenerList = listeners.getListenerList();

				/* Recreate the ActionEvent and stuff the value of the ACTION_COMMAND_KEY */
				ActionEvent e =
					new ActionEvent(
						evt.getSource(),
						evt.getID(),
						(String) getValue(Action.ACTION_COMMAND_KEY));
				for (int i = 0; i <= listenerList.length - 2; i += 2) {
					((ActionListener) listenerList[i + 1]).actionPerformed(e);
				}
			}
		}

		public void addActionListener(ActionListener l) {
			if (listeners == null) {
				listeners = new EventListenerList();
			}
			listeners.add(ActionListener.class, l);
		}

		public void removeActionListener(ActionListener l) {
			if (listeners == null) {
				return;
			}
			listeners.remove(ActionListener.class, l);
		}

		/** 
		 * Returns the Icon associated with the name from the resources.
		 * Tries both JLF icons and JFA icons (in that order).
		 * The resouce should be in the path.
		 * @param name Name of the icon file, e.g. navigation/Down16.gif or 20x20/ClockS.gif
		 * @return the icon of the image or null if the icon is not found.
		 */
		public ImageIcon getIcon(String name) {
			return JdaiGuiHelpers.getIcon(name);
		}
	}

	public class QuitAction extends JdaiAbstractAction {

		private static final String ACTION_COMMAND_KEY = "quit-command";
		private final String NAME = labels.getString("Quit");
		private static final String SMALL_ICON = "20x20/Error.gif";
		private static final String LARGE_ICON = "20x20/Error.gif";
		private final String SHORT_DESCRIPTION = NAME;
		private final String LONG_DESCRIPTION =
			labels.getString("Quit the application");
		private final int MNEMONIC_KEY =
			((Character) labels.getObject("QuitMnemonic")).charValue();

		/**
		 * Constructor.
		 */
		protected QuitAction() {
			putValue(Action.NAME, NAME);
			putValue(Action.SMALL_ICON, getIcon(SMALL_ICON));
			putValue(LARGE_ICON_STR, getIcon(LARGE_ICON));
			putValue(Action.SHORT_DESCRIPTION, SHORT_DESCRIPTION);
			putValue(Action.LONG_DESCRIPTION, LONG_DESCRIPTION);
			putValue(Action.MNEMONIC_KEY, new Integer(MNEMONIC_KEY));
			putValue(Action.ACTION_COMMAND_KEY, ACTION_COMMAND_KEY);
		}
	}

	public QuitAction quitAction() {
		if (actionMap.containsKey(QuitAction.ACTION_COMMAND_KEY))
			return (QuitAction) actionMap.get(QuitAction.ACTION_COMMAND_KEY);
		else {
			QuitAction act = new QuitAction();
			actionMap.put(QuitAction.ACTION_COMMAND_KEY, act);
			return act;
		}
	}

	public class AboutAction extends JdaiAbstractAction {

		private static final String ACTION_COMMAND_KEY = "about-command";
		private final String NAME = labels.getString("About...");
		private static final String SMALL_ICON = "20x20/About.gif";
		private static final String LARGE_ICON = "20x20/About.gif";
		private final String SHORT_DESCRIPTION = NAME;
		private final String LONG_DESCRIPTION =
			labels.getString("About the application");
		private final int MNEMONIC_KEY =
			((Character) labels.getObject("AboutMnemonic")).charValue();

		/**
		 * Constructor.
		 */
		protected AboutAction() {
			putValue(Action.NAME, NAME);
			putValue(Action.SMALL_ICON, getIcon(SMALL_ICON));
			putValue(LARGE_ICON_STR, getIcon(LARGE_ICON));
			putValue(Action.SHORT_DESCRIPTION, SHORT_DESCRIPTION);
			putValue(Action.LONG_DESCRIPTION, LONG_DESCRIPTION);
			putValue(Action.MNEMONIC_KEY, new Integer(MNEMONIC_KEY));
			putValue(Action.ACTION_COMMAND_KEY, ACTION_COMMAND_KEY);
		}
	}

	public AboutAction aboutAction() {
		if (actionMap.containsKey(AboutAction.ACTION_COMMAND_KEY))
			return (AboutAction) actionMap.get(AboutAction.ACTION_COMMAND_KEY);
		else {
			AboutAction act = new AboutAction();
			actionMap.put(AboutAction.ACTION_COMMAND_KEY, act);
			return act;
		}
	}

	public class OptionsAction extends JdaiAbstractAction {

		private static final String ACTION_COMMAND_KEY = "option-command";
		private final String NAME = labels.getString("Options");
		private static final String SMALL_ICON = "20x20/DocumentDraw.gif";
		private static final String LARGE_ICON = "20x20/DocumentDraw.gif";
		private final String SHORT_DESCRIPTION = NAME;
		private final String LONG_DESCRIPTION = labels.getString("OptionsDesc");
		private final int MNEMONIC_KEY =
			((Character) labels.getObject("OptionsMnemonic")).charValue();

		/**
		 * Constructor.
		 */
		protected OptionsAction() {
			putValue(Action.NAME, NAME);
			putValue(Action.SMALL_ICON, getIcon(SMALL_ICON));
			putValue(LARGE_ICON_STR, getIcon(LARGE_ICON));
			putValue(Action.SHORT_DESCRIPTION, SHORT_DESCRIPTION);
			putValue(Action.LONG_DESCRIPTION, LONG_DESCRIPTION);
			putValue(Action.MNEMONIC_KEY, new Integer(MNEMONIC_KEY));
			putValue(Action.ACTION_COMMAND_KEY, ACTION_COMMAND_KEY);
		}
	}

	public OptionsAction optionsAction() {
		if (actionMap.containsKey(OptionsAction.ACTION_COMMAND_KEY))
			return (OptionsAction) actionMap.get(
				OptionsAction.ACTION_COMMAND_KEY);
		else {
			OptionsAction act = new OptionsAction();
			actionMap.put(OptionsAction.ACTION_COMMAND_KEY, act);
			return act;
		}
	}

	public class SelectSectionAction extends JdaiAbstractAction {

		private static final String ACTION_COMMAND_KEY =
			"select-section-command";
		private final String NAME = labels.getString("Select Section");
		private static final String SMALL_ICON = "20x20/Folder.gif";
		private static final String LARGE_ICON = "20x20/Folder.gif";
		private final String SHORT_DESCRIPTION = NAME;
		private final String LONG_DESCRIPTION =
			labels.getString("Select the photo section to explore");
		private final int MNEMONIC_KEY =
			((Character) labels.getObject("SelectMnemonic")).charValue();

		/**
		 * Constructor.
		 */
		protected SelectSectionAction() {
			putValue(Action.NAME, NAME);
			putValue(Action.SMALL_ICON, getIcon(SMALL_ICON));
			putValue(LARGE_ICON_STR, getIcon(LARGE_ICON));
			putValue(Action.SHORT_DESCRIPTION, SHORT_DESCRIPTION);
			putValue(Action.LONG_DESCRIPTION, LONG_DESCRIPTION);
			putValue(Action.MNEMONIC_KEY, new Integer(MNEMONIC_KEY));
			putValue(Action.ACTION_COMMAND_KEY, ACTION_COMMAND_KEY);
		}
	}

	public SelectSectionAction selectSectionAction() {
		if (actionMap.containsKey(SelectSectionAction.ACTION_COMMAND_KEY))
			return (SelectSectionAction) actionMap.get(
				SelectSectionAction.ACTION_COMMAND_KEY);
		else {
			SelectSectionAction act = new SelectSectionAction();
			actionMap.put(SelectSectionAction.ACTION_COMMAND_KEY, act);
			return act;
		}
	}

	public class ClusterIPAction extends JdaiAbstractAction {

		private static final String ACTION_COMMAND_KEY =
			"cluster-ip-command";
		private final String NAME = "Change cluster IP";
		private static final String SMALL_ICON = "20x20/Plug.gif";
		private static final String LARGE_ICON = "20x20/Plug.gif";
		private final String SHORT_DESCRIPTION = NAME;
		private final String LONG_DESCRIPTION = "Change the IP to connect to the cluster";
// 		private final int MNEMONIC_KEY =
// 			((Character) labels.getObject("SelectMnemonic")).charValue();

		/**
		 * Constructor.
		 */
		protected ClusterIPAction() {
			putValue(Action.NAME, NAME);
			putValue(Action.SMALL_ICON, getIcon(SMALL_ICON));
			putValue(LARGE_ICON_STR, getIcon(LARGE_ICON));
			putValue(Action.SHORT_DESCRIPTION, SHORT_DESCRIPTION);
			putValue(Action.LONG_DESCRIPTION, LONG_DESCRIPTION);
// 			putValue(Action.MNEMONIC_KEY, new Integer(MNEMONIC_KEY));
 			putValue(Action.ACTION_COMMAND_KEY, ACTION_COMMAND_KEY);
		}
	}

	public ClusterIPAction clusterIPAction() {
		if (actionMap.containsKey(ClusterIPAction.ACTION_COMMAND_KEY))
			return (ClusterIPAction) actionMap.get(ClusterIPAction.ACTION_COMMAND_KEY);
		else {
			ClusterIPAction act = new ClusterIPAction();
			actionMap.put(ClusterIPAction.ACTION_COMMAND_KEY, act);
			return act;
		}
	}

	public class QueryConsoleAction
        extends JdaiAbstractAction {

		public static final String ACTION_COMMAND_KEY = "query-console-command";
		private static final String NAME = "Query console";
        private static final String SHORT_DESCRIPTION = NAME;
		private static final String LONG_DESCRIPTION = "Show/Hide the query console window";

        private boolean selected;

		/**
		 * Constructor.
		 */

		protected QueryConsoleAction() {
			putValue(Action.NAME, NAME);
			putValue(Action.SHORT_DESCRIPTION, SHORT_DESCRIPTION);
			putValue(Action.LONG_DESCRIPTION, LONG_DESCRIPTION);
 			putValue(Action.ACTION_COMMAND_KEY, ACTION_COMMAND_KEY);
            selected = false;
		}

        public void changeSelection() {
            selected = !selected;
            if (HCFile.queryLogger instanceof Component) {
                ((Component)HCFile.queryLogger).setVisible(selected);
            }
        }
	}
    
	public QueryConsoleAction queryConsoleAction() {
		if (actionMap.containsKey(QueryConsoleAction.ACTION_COMMAND_KEY))
			return (QueryConsoleAction) actionMap.get(QueryConsoleAction.ACTION_COMMAND_KEY);
		else {
			QueryConsoleAction act = new QueryConsoleAction();
			actionMap.put(QueryConsoleAction.ACTION_COMMAND_KEY, act);
			return act;
		}
	}

	public class Rotate0Action extends JdaiAbstractAction {

		private static final String ACTION_COMMAND_KEY = "rotate0-command";
		private final String NAME = labels.getString("No Rotation");
		private static final String SMALL_ICON = "20x20/ClockN.gif";
		private static final String LARGE_ICON = "20x20/ClockN.gif";
		private final String SHORT_DESCRIPTION = NAME;
		private final String LONG_DESCRIPTION =
			labels.getString("Do not rotate the photo");
		private final int MNEMONIC_KEY =
			((Character) labels.getObject("NoRotMnemonic")).charValue();

		/**
		 * Constructor.
		 */
		protected Rotate0Action() {
			putValue(Action.NAME, NAME);
			putValue(Action.SMALL_ICON, getIcon(SMALL_ICON));
			putValue(LARGE_ICON_STR, getIcon(LARGE_ICON));
			putValue(Action.SHORT_DESCRIPTION, SHORT_DESCRIPTION);
			putValue(Action.LONG_DESCRIPTION, LONG_DESCRIPTION);
			putValue(Action.MNEMONIC_KEY, new Integer(MNEMONIC_KEY));
			putValue(Action.ACTION_COMMAND_KEY, ACTION_COMMAND_KEY);
		}
	}

	public Rotate0Action rotate0Action() {
		if (actionMap.containsKey(Rotate0Action.ACTION_COMMAND_KEY))
			return (Rotate0Action) actionMap.get(
				Rotate0Action.ACTION_COMMAND_KEY);
		else {
			Rotate0Action act = new Rotate0Action();
			actionMap.put(Rotate0Action.ACTION_COMMAND_KEY, act);
			return act;
		}
	}

	public class Rotate90Action extends JdaiAbstractAction {

		private static final String ACTION_COMMAND_KEY = "rotate90-command";
		private final String NAME = labels.getString("Rotate Clockwise");
		private static final String SMALL_ICON = "20x20/ClockE.gif";
		private static final String LARGE_ICON = "20x20/ClockE.gif";
		private final String SHORT_DESCRIPTION = NAME;
		private final String LONG_DESCRIPTION =
			labels.getString("Rotate the photo 90 degrees clockwise");
		private final int MNEMONIC_KEY =
			((Character) labels.getObject("ClockRotMnemonic")).charValue();

		/**
		 * Constructor.
		 */
		public Rotate90Action() {
			putValue(Action.NAME, NAME);
			putValue(Action.SMALL_ICON, getIcon(SMALL_ICON));
			putValue(LARGE_ICON_STR, getIcon(LARGE_ICON));
			putValue(Action.SHORT_DESCRIPTION, SHORT_DESCRIPTION);
			putValue(Action.LONG_DESCRIPTION, LONG_DESCRIPTION);
			putValue(Action.MNEMONIC_KEY, new Integer(MNEMONIC_KEY));
			putValue(Action.ACTION_COMMAND_KEY, ACTION_COMMAND_KEY);
		}
	}

	public Rotate90Action rotate90Action() {
		if (actionMap.containsKey(Rotate90Action.ACTION_COMMAND_KEY))
			return (Rotate90Action) actionMap.get(
				Rotate90Action.ACTION_COMMAND_KEY);
		else {
			Rotate90Action act = new Rotate90Action();
			actionMap.put(Rotate90Action.ACTION_COMMAND_KEY, act);
			return act;
		}
	}

	public class Rotate180Action extends JdaiAbstractAction {

		private static final String ACTION_COMMAND_KEY = "rotate180-command";
		private final String NAME = labels.getString("Turn Upside-Down");
		private static final String SMALL_ICON = "20x20/ClockS.gif";
		private static final String LARGE_ICON = "20x20/ClockS.gif";
		private final String SHORT_DESCRIPTION = NAME;
		private final String LONG_DESCRIPTION =
			labels.getString("Turn the photo upside-down");
		private final int MNEMONIC_KEY =
			((Character) labels.getObject("UpsideMnemonic")).charValue();

		/**
		 * Constructor.
		 */
		public Rotate180Action() {
			putValue(Action.NAME, NAME);
			putValue(Action.SMALL_ICON, getIcon(SMALL_ICON));
			putValue(LARGE_ICON_STR, getIcon(LARGE_ICON));
			putValue(Action.SHORT_DESCRIPTION, SHORT_DESCRIPTION);
			putValue(Action.LONG_DESCRIPTION, LONG_DESCRIPTION);
			putValue(Action.MNEMONIC_KEY, new Integer(MNEMONIC_KEY));
			putValue(Action.ACTION_COMMAND_KEY, ACTION_COMMAND_KEY);
		}
	}

	public Rotate180Action rotate180Action() {
		if (actionMap.containsKey(Rotate180Action.ACTION_COMMAND_KEY))
			return (Rotate180Action) actionMap.get(
				Rotate180Action.ACTION_COMMAND_KEY);
		else {
			Rotate180Action act = new Rotate180Action();
			actionMap.put(Rotate180Action.ACTION_COMMAND_KEY, act);
			return act;
		}
	}

	public class Rotate270Action extends JdaiAbstractAction {

		private static final String ACTION_COMMAND_KEY = "rotate270-command";
		private final String NAME =
			labels.getString("Rotate Counter-Clockwise");
		private static final String SMALL_ICON = "20x20/ClockW.gif";
		private static final String LARGE_ICON = "20x20/ClockW.gif";
		private final String SHORT_DESCRIPTION = NAME;
		private final String LONG_DESCRIPTION =
			labels.getString("Rotate the photo 90 degrees counter-clockwise");
		private final int MNEMONIC_KEY =
			((Character) labels.getObject("CounterRotMnemonic")).charValue();

		/**
		 * Constructor.
		 */
		public Rotate270Action() {
			putValue(Action.NAME, NAME);
			putValue(Action.SMALL_ICON, getIcon(SMALL_ICON));
			putValue(LARGE_ICON_STR, getIcon(LARGE_ICON));
			putValue(Action.SHORT_DESCRIPTION, SHORT_DESCRIPTION);
			putValue(Action.LONG_DESCRIPTION, LONG_DESCRIPTION);
			putValue(Action.MNEMONIC_KEY, new Integer(MNEMONIC_KEY));
			putValue(Action.ACTION_COMMAND_KEY, ACTION_COMMAND_KEY);
		}
	}

	public Rotate270Action rotate270Action() {
		if (actionMap.containsKey(Rotate270Action.ACTION_COMMAND_KEY))
			return (Rotate270Action) actionMap.get(
				Rotate270Action.ACTION_COMMAND_KEY);
		else {
			Rotate270Action act = new Rotate270Action();
			actionMap.put(Rotate270Action.ACTION_COMMAND_KEY, act);
			return act;
		}
	}

	public class RotateRightAction extends JdaiAbstractAction {

		private static final String ACTION_COMMAND_KEY = "rotate-right-command";
		private final String NAME = labels.getString("Rotate Right");
		private static final String SMALL_ICON = "20x20/RotCWDown.gif";
		private static final String LARGE_ICON = "20x20/RotCWDown.gif";
		private final String SHORT_DESCRIPTION = NAME;
		private final String LONG_DESCRIPTION =
			labels.getString("Rotate the photo to the right");
		private final int MNEMONIC_KEY =
			((Character) labels.getObject("RotateRightMnemonic")).charValue();

		/**
		 * Constructor.
		 */
		public RotateRightAction() {
			putValue(Action.NAME, NAME);
			putValue(Action.SMALL_ICON, getIcon(SMALL_ICON));
			putValue(LARGE_ICON_STR, getIcon(LARGE_ICON));
			putValue(Action.SHORT_DESCRIPTION, SHORT_DESCRIPTION);
			putValue(Action.LONG_DESCRIPTION, LONG_DESCRIPTION);
			putValue(Action.MNEMONIC_KEY, new Integer(MNEMONIC_KEY));
			putValue(Action.ACTION_COMMAND_KEY, ACTION_COMMAND_KEY);
		}
	}

	public RotateRightAction rotateRightAction() {
		if (actionMap.containsKey(RotateRightAction.ACTION_COMMAND_KEY))
			return (RotateRightAction) actionMap.get(
				RotateRightAction.ACTION_COMMAND_KEY);
		else {
			RotateRightAction act = new RotateRightAction();
			actionMap.put(RotateRightAction.ACTION_COMMAND_KEY, act);
			return act;
		}
	}

	public class RotateLeftAction extends JdaiAbstractAction {

		private static final String ACTION_COMMAND_KEY = "rotate-left-command";
		private final String NAME = labels.getString("Rotate Left");
		private static final String SMALL_ICON = "20x20/RotCCDown.gif";
		private static final String LARGE_ICON = "20x20/RotCCDown.gif";
		private final String SHORT_DESCRIPTION = NAME;
		private final String LONG_DESCRIPTION =
			labels.getString("Rotate the photo to the left");
		private final int MNEMONIC_KEY =
			((Character) labels.getObject("RotateLeftMnemonic")).charValue();

		/**
		 * Constructor.
		 */
		public RotateLeftAction() {
			putValue(Action.NAME, NAME);
			putValue(Action.SMALL_ICON, getIcon(SMALL_ICON));
			putValue(LARGE_ICON_STR, getIcon(LARGE_ICON));
			putValue(Action.SHORT_DESCRIPTION, SHORT_DESCRIPTION);
			putValue(Action.LONG_DESCRIPTION, LONG_DESCRIPTION);
			putValue(Action.MNEMONIC_KEY, new Integer(MNEMONIC_KEY));
			putValue(Action.ACTION_COMMAND_KEY, ACTION_COMMAND_KEY);
		}
	}

	public RotateLeftAction rotateLeftAction() {
		if (actionMap.containsKey(RotateLeftAction.ACTION_COMMAND_KEY))
			return (RotateLeftAction) actionMap.get(
				RotateLeftAction.ACTION_COMMAND_KEY);
		else {
			RotateLeftAction act = new RotateLeftAction();
			actionMap.put(RotateLeftAction.ACTION_COMMAND_KEY, act);
			return act;
		}
	}

	public class UploadAction extends JdaiAbstractAction {

		public static final String ACTION_COMMAND_KEY = "upload-command";
		private final String NAME = "Upload";
		private static final String SMALL_ICON = "20x20/DataStore.gif";
		private static final String LARGE_ICON = "20x20/DataStore.gif";
		private final String SHORT_DESCRIPTION = NAME;
		private final String LONG_DESCRIPTION = "Upload Photos to honeycomb";

		/**
		 * Constructor.
		 */

		public UploadAction() {
			putValue(Action.NAME, NAME);
			putValue(Action.SMALL_ICON, getIcon(SMALL_ICON));
			putValue(LARGE_ICON_STR, getIcon(LARGE_ICON));
			putValue(Action.SHORT_DESCRIPTION, SHORT_DESCRIPTION);
			putValue(Action.LONG_DESCRIPTION, LONG_DESCRIPTION);
			putValue(Action.ACTION_COMMAND_KEY, ACTION_COMMAND_KEY);
		}
	}
    
	public UploadAction uploadAction() {
		if (actionMap.containsKey(UploadAction.ACTION_COMMAND_KEY))
			return (UploadAction) actionMap.get(UploadAction.ACTION_COMMAND_KEY);
		else {
			UploadAction act = new UploadAction();
			actionMap.put(UploadAction.ACTION_COMMAND_KEY, act);
			return act;
		}
	}
    
	public class ViewAction extends JdaiAbstractAction {

		private static final String ACTION_COMMAND_KEY = "view-command";
		private final String NAME = labels.getString("View");
		private static final String SMALL_ICON = "20x20/Magnify.gif";
		private static final String LARGE_ICON = "20x20/Magnify.gif";
		private final String SHORT_DESCRIPTION = NAME;
		private final String LONG_DESCRIPTION =
			labels.getString("View Photo");
		private final int MNEMONIC_KEY =
			((Character) labels.getObject("ViewMnemonic")).charValue();

		/**
		 * Constructor.
		 */
		public ViewAction() {
			putValue(Action.NAME, NAME);
			putValue(Action.SMALL_ICON, getIcon(SMALL_ICON));
			putValue(LARGE_ICON_STR, getIcon(LARGE_ICON));
			putValue(Action.SHORT_DESCRIPTION, SHORT_DESCRIPTION);
			putValue(Action.LONG_DESCRIPTION, LONG_DESCRIPTION);
			putValue(Action.MNEMONIC_KEY, new Integer(MNEMONIC_KEY));
			putValue(Action.ACTION_COMMAND_KEY, ACTION_COMMAND_KEY);
		}
	}

	public ViewAction viewAction() {
		if (actionMap.containsKey(ViewAction.ACTION_COMMAND_KEY))
			return (ViewAction) actionMap.get(
				ViewAction.ACTION_COMMAND_KEY);
		else {
			ViewAction act = new ViewAction();
			actionMap.put(ViewAction.ACTION_COMMAND_KEY, act);
			return act;
		}
	}

	public class EditAction extends JdaiAbstractAction {

		private static final String ACTION_COMMAND_KEY = "edit-command";
		private final String NAME = labels.getString("Edit");
		private static final String SMALL_ICON = "20x20/DocumentDraw.gif";
		private static final String LARGE_ICON = "20x20/DocumentDrav.gif";
		private final String SHORT_DESCRIPTION = NAME;
		private final String LONG_DESCRIPTION =
			labels.getString("Edit Photo");
		private final int MNEMONIC_KEY =
			((Character) labels.getObject("EditMnemonic")).charValue();

		/**
		 * Constructor.
		 */
		public EditAction() {
			putValue(Action.NAME, NAME);
			putValue(Action.SMALL_ICON, getIcon(SMALL_ICON));
			putValue(LARGE_ICON_STR, getIcon(LARGE_ICON));
			putValue(Action.SHORT_DESCRIPTION, SHORT_DESCRIPTION);
			putValue(Action.LONG_DESCRIPTION, LONG_DESCRIPTION);
			putValue(Action.MNEMONIC_KEY, new Integer(MNEMONIC_KEY));
			putValue(Action.ACTION_COMMAND_KEY, ACTION_COMMAND_KEY);
		}
	}

	public EditAction editAction() {
		if (actionMap.containsKey(EditAction.ACTION_COMMAND_KEY))
			return (EditAction) actionMap.get(
				EditAction.ACTION_COMMAND_KEY);
		else {
			EditAction act = new EditAction();
			actionMap.put(EditAction.ACTION_COMMAND_KEY, act);
			return act;
		}
	}

	public class DeleteAction extends JdaiAbstractAction {

		private static final String ACTION_COMMAND_KEY = "delete-command";
		private final String NAME = labels.getString("Delete");
		private static final String SMALL_ICON = "20x20/Delete.gif";
		private static final String LARGE_ICON = "20x20/Delete.gif";
		private final String SHORT_DESCRIPTION = NAME;
		private final String LONG_DESCRIPTION =
			labels.getString("Delete Photo");
		private final int MNEMONIC_KEY =
			((Character) labels.getObject("DeleteMnemonic")).charValue();

		/**
		 * Constructor.
		 */
		public DeleteAction() {
			putValue(Action.NAME, NAME);
			putValue(Action.SMALL_ICON, getIcon(SMALL_ICON));
			putValue(LARGE_ICON_STR, getIcon(LARGE_ICON));
			putValue(Action.SHORT_DESCRIPTION, SHORT_DESCRIPTION);
			putValue(Action.LONG_DESCRIPTION, LONG_DESCRIPTION);
			putValue(Action.MNEMONIC_KEY, new Integer(MNEMONIC_KEY));
			putValue(Action.ACTION_COMMAND_KEY, ACTION_COMMAND_KEY);
		}
	}

	public DeleteAction deleteAction() {
		if (actionMap.containsKey(DeleteAction.ACTION_COMMAND_KEY))
			return (DeleteAction) actionMap.get(
				DeleteAction.ACTION_COMMAND_KEY);
		else {
			DeleteAction act = new DeleteAction();
			actionMap.put(DeleteAction.ACTION_COMMAND_KEY, act);
			return act;
		}
	}

	public class SlideshowAction extends JdaiAbstractAction {

		private static final String ACTION_COMMAND_KEY = "slideshow-command";
		private final String NAME = labels.getString("Slideshow");
		private static final String SMALL_ICON = "20x20/ClockGo.gif";
		private static final String LARGE_ICON = "20x20/ClockGo.gif";
		private final String SHORT_DESCRIPTION = NAME;
		private final String LONG_DESCRIPTION =
			labels.getString("Start Slideshow");
		private final int MNEMONIC_KEY =
			((Character) labels.getObject("SlideshowMnemonic")).charValue();

		/**
		 * Constructor.
		 */
		public SlideshowAction() {
			putValue(Action.NAME, NAME);
			putValue(Action.SMALL_ICON, getIcon(SMALL_ICON));
			putValue(LARGE_ICON_STR, getIcon(LARGE_ICON));
			putValue(Action.SHORT_DESCRIPTION, SHORT_DESCRIPTION);
			putValue(Action.LONG_DESCRIPTION, LONG_DESCRIPTION);
			putValue(Action.MNEMONIC_KEY, new Integer(MNEMONIC_KEY));
			putValue(Action.ACTION_COMMAND_KEY, ACTION_COMMAND_KEY);
		}
	}

	public SlideshowAction slideshowAction() {
		if (actionMap.containsKey(SlideshowAction.ACTION_COMMAND_KEY))
			return (SlideshowAction) actionMap.get(
				SlideshowAction.ACTION_COMMAND_KEY);
		else {
			SlideshowAction act = new SlideshowAction();
			actionMap.put(SlideshowAction.ACTION_COMMAND_KEY, act);
			return act;
		}
	}

	public class PrevPhotoAction extends JdaiAbstractAction {

		private static final String ACTION_COMMAND_KEY = "prev-photo-command";
		private final String NAME = labels.getString("PrevPhoto");
		private static final String SMALL_ICON = "20x20/VCRBack.gif";
		private static final String LARGE_ICON = "20x20/VCRBack.gif";
		private final String SHORT_DESCRIPTION = NAME;
		private final String LONG_DESCRIPTION =
			labels.getString("PrevPhotoLong");
		private final int MNEMONIC_KEY =
			((Character) labels.getObject("PrevPhotoMnemonic")).charValue();

		/**
		 * Constructor.
		 */
		public PrevPhotoAction() {
			putValue(Action.NAME, NAME);
			putValue(Action.SMALL_ICON, getIcon(SMALL_ICON));
			putValue(LARGE_ICON_STR, getIcon(LARGE_ICON));
			putValue(Action.SHORT_DESCRIPTION, SHORT_DESCRIPTION);
			putValue(Action.LONG_DESCRIPTION, LONG_DESCRIPTION);
			putValue(Action.MNEMONIC_KEY, new Integer(MNEMONIC_KEY));
			putValue(Action.ACTION_COMMAND_KEY, ACTION_COMMAND_KEY);
		}
	}

	public PrevPhotoAction prevPhotoAction() {
		if (actionMap.containsKey(PrevPhotoAction.ACTION_COMMAND_KEY))
			return (PrevPhotoAction) actionMap.get(
				PrevPhotoAction.ACTION_COMMAND_KEY);
		else {
			PrevPhotoAction act = new PrevPhotoAction();
			actionMap.put(PrevPhotoAction.ACTION_COMMAND_KEY, act);
			return act;
		}
	}

	public class NextPhotoAction extends JdaiAbstractAction {

		private static final String ACTION_COMMAND_KEY = "next-photo-command";
		private final String NAME = labels.getString("NextPhoto");
		private static final String SMALL_ICON = "20x20/VCRForward.gif";
		private static final String LARGE_ICON = "20x20/VCRForward.gif";
		private final String SHORT_DESCRIPTION = NAME;
		private final String LONG_DESCRIPTION =
			labels.getString("NextPhotoLong");
		private final int MNEMONIC_KEY =
			((Character) labels.getObject("NextPhotoMnemonic")).charValue();

		/**
		 * Constructor.
		 */
		public NextPhotoAction() {
			putValue(Action.NAME, NAME);
			putValue(Action.SMALL_ICON, getIcon(SMALL_ICON));
			putValue(LARGE_ICON_STR, getIcon(LARGE_ICON));
			putValue(Action.SHORT_DESCRIPTION, SHORT_DESCRIPTION);
			putValue(Action.LONG_DESCRIPTION, LONG_DESCRIPTION);
			putValue(Action.MNEMONIC_KEY, new Integer(MNEMONIC_KEY));
			putValue(Action.ACTION_COMMAND_KEY, ACTION_COMMAND_KEY);
		}
	}

	public NextPhotoAction nextPhotoAction() {
		if (actionMap.containsKey(NextPhotoAction.ACTION_COMMAND_KEY))
			return (NextPhotoAction) actionMap.get(
				NextPhotoAction.ACTION_COMMAND_KEY);
		else {
			NextPhotoAction act = new NextPhotoAction();
			actionMap.put(NextPhotoAction.ACTION_COMMAND_KEY, act);
			return act;
		}
	}

}
