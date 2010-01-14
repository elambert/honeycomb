package dk.jdai.gui;

import java.util.HashSet;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.WindowConstants;

import java.util.Iterator;
import javax.swing.JFrame;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JPanel;


/**
 * This class handles the preferences GUI
 *
 * @author <a href="mailto:Mikkel@YdeKjaer.dk">Mikkel Yde Kjær</a>
 * 
 */
public class JdaiPreferences {

    private static JdaiPreferences theInstance;
    private Set options = new HashSet();
    private JFrame frame;
	private JDialog dialog;

   private static ResourceBundle labels = ResourceBundle.getBundle("dk.jdai.gui.JdaiPreferencesBundle",Locale.getDefault());


    private JdaiPreferences() {}

    /**
     * This method returns the only instance of this class (Singleton)
     *
     * @return The JdaiPreferences instance
     */
    public static JdaiPreferences getInstance() {
        if (theInstance == null) {
            theInstance = new JdaiPreferences();
        }
        return theInstance;
    }

    /**
     * Adds an Options component to the preferences GUI
     *
     * @param option The option component to add
     */
    public void addOptionsComponent(JdaiOptions option) {
        options.add(option);
    }

    /**
     * Removes an options component from the preferences GUI
     *
     * @param option The option component to remove
     */
    public void removeOptionsComponent(JdaiOptions option) {
        options.remove(option);
    }

    /**
     * This methos sets the parent frame of the preferences GUI dialog
     *
     * @param frame The parent frame
     */
    public void setFrame(JFrame frame) {
        this.frame = frame;
    }

    /**
     * This method shows the preferences GUI as a modal dialog.
     *
     */
    public void show() {
        if (options.isEmpty()) {
            System.out.println("No prefs");
        } else {
            dialog = new JDialog(frame,labels.getString("Prefs"),true);
            JTabbedPane tabs = new JTabbedPane();
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton save = new JButton(labels.getString("Save"));
            JButton cancel = new JButton(labels.getString("Cancel"));
            dialog.getContentPane().add(tabs);
            buttonPanel.add(save);
            buttonPanel.add(cancel);
            dialog.getContentPane().add(buttonPanel,BorderLayout.SOUTH);

			save.addActionListener(new Save());
			cancel.addActionListener(new Cancel());
			dialog.addWindowListener(new Cancel());
			
			save.setMnemonic(((Character)labels.getObject("SaveMnemonic")).charValue());
			cancel.setMnemonic(((Character)labels.getObject("CancelMnemonic")).charValue());

            Iterator i = options.iterator();
            while (i.hasNext()) {
                JdaiOptions opt = (JdaiOptions)i.next();
                tabs.add(opt.getName(),opt.getComponent());
            }

			dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            dialog.pack();
            dialog.setVisible(true);
        }
    }
    
    private class Cancel extends WindowAdapter implements ActionListener {
		/**
		 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
		 */
		public void actionPerformed(ActionEvent e) {
			cancel();
		}
		
		public void windowClosing(WindowEvent e) {
			cancel();
		}

		private void cancel() {
		    Iterator i = options.iterator();
            while (i.hasNext()) {
                JdaiOptions opt = (JdaiOptions)i.next();
                opt.cancel();
            }
			dialog.dispose();			
		}    	
    }
    
    private class Save extends AbstractAction {
		/**
	 	* @see java.awt.event.ActionListener#actionPerformed(java.awt.event.
	 	* ActionEvent)
	 	*/
		public void actionPerformed(ActionEvent e) {
            Iterator i = options.iterator();
            boolean success = true;
            while (i.hasNext()) {
                JdaiOptions opt = (JdaiOptions)i.next();
                if (!opt.save()) {
                	success = false;
                }
            }
			if (!success) {
				JOptionPane.showMessageDialog(dialog,labels.getString("ErrorMsg"),labels.getString("Error"),JOptionPane.ERROR_MESSAGE);
			}
			dialog.dispose();
		}			
	}

}
