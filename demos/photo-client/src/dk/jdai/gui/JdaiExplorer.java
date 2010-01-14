// JdaiExplorer.java
// $Id: JdaiExplorer.java 11627 2007-10-31 01:31:41Z ds158322 $

package dk.jdai.gui;

import dk.jdai.model.*;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.prefs.*;
import javax.swing.*;
import java.util.ResourceBundle;
import java.util.Locale;

import com.sun.honeycomb.jdai.*;
import com.sun.honeycomb.fs.HCFile;
import com.sun.honeycomb.gui.SwingQueryLogger;
import javax.swing.JCheckBoxMenuItem;

/**
 * Combines JdaiSectionTree and JdaiPhotoList into an explorer.
 * @author Jeppe Buk (jaybe@jaybe.dk)
 * @version $Revision: 1.3 $
 */
public class JdaiExplorer
    implements
	ActionListener,
	JdaiSectionTree.Delegate,
	JdaiPhotoList.Delegate,
	JdaiProgressListener {

    private static final String VERSION = "0.4";
    private static final String defaultView = "Make, Model, Flash, Focal_Length | %Date_Time_Original%.jpg";

    private static ResourceBundle labels =
	ResourceBundle.getBundle(
				 "dk.jdai.gui.JdaiExplorerBundle",
				 Locale.getDefault());

    private JdaiSectionTree tree;
    private JdaiPhotoList list;
    private JdaiPhotoZoom pane;
    // 	private JdaiPhotoInfoEditor edit;
    private JdaiPhotoInfoPane info;

    private JFrame frame;
    private JSplitPane split1;
    private JSplitPane split2;
    private JPanel panel;
    private JPanel topPanel;
    private JPanel statusPanel;
    private JLabel status;
    private JProgressBar progress;

    private JMenuBar menuBar;
    private JMenu fileMenu;
    private JMenu toolsMenu;
    private JMenu photoMenu;
    private JMenu helpMenu;

    private JToolBar toolBar;

    public static Preferences prefs;
    public static final String PREF_SECTION = "JdaiExplorer_section";
    private static final String PREF_FRAME_STATE = "JdaiExplorer_frame_state";
    private static final String PREF_WIDTH = "JdaiExplorer_width";
    private static final String PREF_HEIGHT = "JdaiExplorer_height";
    private static final String PREF_XOFF = "JdaiExplorer_xoffset";
    private static final String PREF_YOFF = "JdaiExplorer_yoffset";

    private static String classname = "com.sun.honeycomb.oa.OA";
    private static final String PREF_IP = "ClusterIP";

    public JFrame getFrame() {
        return(frame);
    }

    private void setClusterIp() {
        Preferences prefs = Preferences.userNodeForPackage(getClass());
        String savedClusterIP = prefs.get(PREF_IP, null);
        String newValue = null;
        

        while (newValue == null) {
            if (savedClusterIP == null) {
                newValue = JOptionPane.showInputDialog("Enter the cluster IP :");
            } else {
                newValue = JOptionPane.showInputDialog("Enter the cluster IP :", savedClusterIP);
            }
            
            if (newValue == null) {
                newValue = savedClusterIP;
            } else {
                prefs.put(PREF_IP,
                          newValue);
            }
        }
        try{        
            HCFile.setClusterIP(classname, newValue);
        }
        catch (Exception e){
            throw new RuntimeException(e);
        }
        refreshView(prefs);
    }
    
    public void refreshView() {
        refreshView(prefs);
    }

    public void refreshView(Preferences localPrefs) {
	String section = localPrefs.get(PREF_SECTION, "");
	if (section.equals(""))
            setSection(new HCSection(defaultView));
	else
	    setSection(new HCSection(section));
    }
    
    /**
     * Creates a new instance of JdaiExplorer
     */
    public JdaiExplorer() {
	tree = new JdaiSectionTree(this);
	list = new JdaiPhotoList(this);
	// 		edit = new JdaiPhotoInfoEditor();
	pane = new JdaiPhotoZoom(this);

	Thread paneThread = new Thread(pane);
	paneThread.start();
	info = new JdaiPhotoInfoPane();

	split2 =
	    new JSplitPane(
			   JSplitPane.VERTICAL_SPLIT,
			   tree.getPresentation(),
			   info.getPresentation());
	split1 =
	    new JSplitPane(
			   JSplitPane.HORIZONTAL_SPLIT,
			   split2,
			   list.getPresentation());

	status = new JLabel(labels.getString("SectionNone"));
	progress = new JProgressBar();
	progress.setStringPainted(true);
	progress.setMinimum(0);
	progress.setMaximum(100);
	progress.setVisible(false);
	statusPanel = new JPanel(new GridLayout(1, 2));
	statusPanel.add(status);
	statusPanel.add(progress);

	panel = new JPanel(new BorderLayout());
	panel.add(split1, BorderLayout.CENTER);
	panel.add(statusPanel, BorderLayout.SOUTH);

	JdaiPreferences.getInstance().addOptionsComponent(
							  JdaiEXIFInfoPrefs.getInstance());

	/* The toolbar needs all the borders of a BorderLayout for dragging to work. */
	initToolbar();
	topPanel = new JPanel(new BorderLayout());
	topPanel.add(toolBar, BorderLayout.NORTH);
	topPanel.add(panel, BorderLayout.CENTER);

	initMenu();
	initActions();

	setClusterIp();

	prefs = Preferences.userNodeForPackage(getClass());

	frame = new JFrame(labels.getString("WindowTitle"));
	JdaiPreferences.getInstance().setFrame(frame);
	frame.setJMenuBar(menuBar);
	frame.getContentPane().add(topPanel);

	frame.addWindowListener(new WindowAdapter() {
		public void windowClosing(WindowEvent we) {
		    doQuit();
		}
	    });

	frame.pack();
	int width = prefs.getInt(PREF_WIDTH, 640);
	int height = prefs.getInt(PREF_HEIGHT, 480);
	int xoff = prefs.getInt(PREF_XOFF, 0);
	int yoff = prefs.getInt(PREF_YOFF, 0);
	frame.setSize(new Dimension(width, height));
	frame.setLocation(xoff, yoff);
	frame.setVisible(true);
	frame.setExtendedState(prefs.getInt(PREF_FRAME_STATE, JFrame.NORMAL));
    }

    private void initMenu() {
	menuBar = new JMenuBar();
	menuBar.add(fileMenu = new JMenu(labels.getString("MenuFile")));
	fileMenu.setMnemonic(((Character) labels.getObject("FileMnemonic")).charValue());
	fileMenu.add(JdaiActions.instance().selectSectionAction());
        fileMenu.add(new JCheckBoxMenuItem(JdaiActions.instance().queryConsoleAction()));
	fileMenu.addSeparator();
	fileMenu.add(JdaiActions.instance().clusterIPAction());
	fileMenu.addSeparator();
	fileMenu.add(JdaiActions.instance().quitAction()).setAccelerator(
									 KeyStroke.getKeyStroke(
												((Integer) labels.getObject("QuitAcc")).intValue(),
												Event.CTRL_MASK,
												true));

	// 		menuBar.add(toolsMenu = new JMenu(labels.getString("MenuTools")));
	// 		toolsMenu.setMnemonic(
	// 			((Character) labels.getObject("ToolsMnemonic")).charValue());
	// 		toolsMenu.add(JdaiActions.instance().optionsAction());

	menuBar.add(photoMenu = new JMenu(labels.getString("MenuPhoto")));
	photoMenu.setMnemonic(
			      ((Character) labels.getObject("PhotoMnemonic")).charValue());
	photoMenu.add(JdaiActions.instance().uploadAction());
	photoMenu.add(JdaiActions.instance().viewAction());
	// 		photoMenu.add(JdaiActions.instance().editAction());
	photoMenu.addSeparator();
	photoMenu.add(JdaiActions.instance().slideshowAction());
	photoMenu.addSeparator();
	photoMenu.add(JdaiActions.instance().rotateLeftAction());
	photoMenu.add(JdaiActions.instance().rotateRightAction());
	// 		photoMenu.addSeparator();
	// 		photoMenu.add(JdaiActions.instance().deleteAction());

	menuBar.add(helpMenu = new JMenu(labels.getString("MenuHelp")));
	helpMenu.setMnemonic(
			     ((Character) labels.getObject("HelpMnemonic")).charValue());
	helpMenu.add(JdaiActions.instance().aboutAction());
    }

    private void initToolbar() {
	toolBar = new JToolBar();
	toolBar.add(JdaiActions.instance().selectSectionAction());
	toolBar.addSeparator();
	toolBar.add(JdaiActions.instance().viewAction());
	// 		toolBar.add(JdaiActions.instance().editAction());
	toolBar.addSeparator();
	toolBar.add(JdaiActions.instance().slideshowAction());
	toolBar.addSeparator();
	toolBar.add(JdaiActions.instance().rotateLeftAction());
	toolBar.add(JdaiActions.instance().rotateRightAction());
	// 		toolBar.addSeparator();
	// 		toolBar.add(JdaiActions.instance().deleteAction());
    }

    private void initActions() {
	JdaiActions.instance().selectSectionAction().addActionListener(this);
	JdaiActions.instance().clusterIPAction().addActionListener(this);
	JdaiActions.instance().queryConsoleAction().addActionListener(this);
	JdaiActions.instance().quitAction().addActionListener(this);
	JdaiActions.instance().aboutAction().addActionListener(this);
	JdaiActions.instance().optionsAction().addActionListener(this);
    }

    /**
     * This method is the action handler.
     * @param evt The action event.
     */
    public void actionPerformed(ActionEvent evt) {
	String command = evt.getActionCommand();

	if (command
	    .equals(
		    JdaiActions
		    .instance()
		    .selectSectionAction()
		    .getActionCommand())) {
	    doSelectSection();
	} else if (
		   command.equals(
				  JdaiActions.instance().clusterIPAction().getActionCommand())) {
	    setClusterIp();
        } else if (command.equals(JdaiActions.QueryConsoleAction.ACTION_COMMAND_KEY)) {
            JdaiActions.instance().queryConsoleAction().changeSelection();
	} else if (
		   command.equals(
				  JdaiActions.instance().quitAction().getActionCommand())) {
	    doQuit();
	} else if (
		   command.equals(
				  JdaiActions.instance().aboutAction().getActionCommand())) {
	    doAbout();
	} else if (
		   command.equals(
				  JdaiActions.instance().optionsAction().getActionCommand())) {
	    doOptions();
	}
    }

    private void doOptions() {
	JdaiPreferences.getInstance().show();
    }

    private void doSelectSection() {
	String section = prefs.get(PREF_SECTION, defaultView);
        
        section = JOptionPane.showInputDialog(frame,
                                              "Enter the view specification :",
                                              section);
        if (section != null) {
	    // Commented out for the demo. The view comes back as default
	    //             prefs.put(PREF_SECTION,
	    //                       section);
            tree.setSection(new HCSection(section));
        }
    }

    private void doQuit() {
	savePreferences();
	tree.savePreferences();
	list.savePreferences();
	info.savePreferences();
	System.exit(0);
    }

    /**
     * Method savePreferences.
     */
    private void savePreferences() {
	int state = frame.getExtendedState();
	state = state & ~JFrame.ICONIFIED; // ignore iconified state
	prefs.putInt(PREF_FRAME_STATE, state);
	prefs.putInt(PREF_WIDTH, frame.getWidth());
	prefs.putInt(PREF_HEIGHT, frame.getHeight());
	prefs.putInt(PREF_XOFF, frame.getX());
	prefs.putInt(PREF_YOFF, frame.getY());
    }

    private void doAbout() {
        java.net.URL url = getClass().getResource("/about.jpg");
        Image image = Toolkit.getDefaultToolkit().getImage(url);
        Icon icon = new ImageIcon(image);

	JOptionPane.showMessageDialog(split1, null, labels.getString("AboutTitle"), 
				      JOptionPane.INFORMATION_MESSAGE,
				      icon);
    }
    
    /**
     * The method called when the tree selection is changed.
     * @param section The JdaiSection selected.
     */
    public void selectionChanged(JdaiSection section) {
	list.setSection(section);
	int noPhotos = list.getPhotoCount();
	String labelText =
	    java.text.MessageFormat.format(
					   labels.getString("SectionFormat"),
					   new String[] { labels.getString("Section"), section.getName()});
	if (noPhotos == 0)
	    labelText += labels.getString("NoPhoto");
	else if (noPhotos == 1)
	    labelText += labels.getString("OnePhoto");
	else
	    labelText
		+= java.text.MessageFormat.format(
						  labels.getString("MorePhotosFormat"),
						  new String[] {
						      "" + noPhotos,
						      labels.getString("MorePhotos")});
	status.setText(labelText);
	if (noPhotos > 0) {
	    list.selectPhoto(0);
	} else {
	    info.setPhoto(null);
	}
    }

    /** 
     * The method called when the photo selection is changed.
     * @param photo The JdaiPhoto selected.
     */
    public void selectionChanged(JdaiPhoto photo) {
	info.setPhoto(photo);
    }

    /**
     * @see dk.jdai.gui.JdaiPhotoList.Delegate#selectedForViewing(dk.jdai.model.JdaiSection, int)
     */
    public void selectedForViewing(
				   JdaiPhoto[] photos,
				   int index,
				   boolean slideshow) {
	pane.setPhotoArray(photos, index);
	if (slideshow) {
	    pane.startSlideshow();
	}
    }

    /**
     * @see dk.jdai.gui.JdaiPhotoList.Delegate#selectedForEditing(dk.jdai.model.
     * JdaiSection, int)
     */
    public void selectedForEditing(JdaiPhoto[] photos, int index) {
        // Disabled in Honeycomb
	// 		edit.setPhotoArray(photos, index);
    }

    /**
     * Set the section to explore.
     * @param section The new section.
     */

    public void setSection(JdaiSection section) {
	tree.setSection(section);
    }

    private class ProgUpd implements Runnable {
	int value;
	public ProgUpd(int value) {
	    this.value = value;
	}
	public void run() {
	    if (value == -1) {
		progress.setIndeterminate(true);
		progress.setString(labels.getString("ProgressIndeterminate"));
	    } else {
		progress.setIndeterminate(false);
		progress.setValue(value);
	    }
	    if (value != 0)
		progress.setVisible(true);
	    else
		progress.setVisible(false);
	}
    }

    public void progress(Object src, float percentageDone) {
	SwingUtilities.invokeLater(new ProgUpd((int) percentageDone));
    }

    public void indeterminate(Object src) {
	SwingUtilities.invokeLater(new ProgUpd(-1));
    }

    public void complete(Object src) {
	SwingUtilities.invokeLater(new ProgUpd(0));
    }

    /**
     * The Main method.
     * @param args the command line arguments
     */

    public static JdaiExplorer explorer = null;

    public static void main(String[] args) {
        new SplashWindow();

        if (((String)System.getProperty("os.name")).indexOf("Mac") == -1) {
            // We are not on MacOS
            try {
                /* try setting kunstoff l&f */
                UIManager.setLookAndFeel(
                                         "com.incors.plaf.kunststoff.KunststoffLookAndFeel");
            } catch (Exception e) {
                /* just use default l&f */
            }
        }
        
        SwingQueryLogger logger = new SwingQueryLogger();
        logger.setVisible(false);
        HCFile.queryLogger = logger;

        explorer = new JdaiExplorer();
        if (args.length != 0) 
            classname = args[0];
        
    }
}

class SplashWindow extends JWindow
{
    public SplashWindow()
    {
	//         super(null);
        java.net.URL url = getClass().getResource("/splash-screen.jpg");
        Image splashIm = Toolkit.getDefaultToolkit().getImage(url);
	//         Image splashIm = Toolkit.getDefaultToolkit().getImage("splash-screen.jpg");
        JLabel l = new JLabel(new ImageIcon(splashIm));
        getContentPane().add(l, BorderLayout.CENTER);
        pack();

        Dimension screenSize =
	    Toolkit.getDefaultToolkit().getScreenSize();
        Dimension labelSize = new Dimension(640, 480);
        setLocation(screenSize.width/2 - (labelSize.width/2),
                    screenSize.height/2 - (labelSize.height/2));
        setSize(labelSize);
        setVisible(true);
        show();

        try {
            Thread.sleep(3000);
        } catch (InterruptedException ignored) {
        }
        dispose();
    }
}
