// JdaiGuiHelpers.java
// $Id: JdaiGuiHelpers.java 3456 2005-02-05 00:43:33Z rw151951 $

package dk.jdai.gui;

import java.net.URL;
import javax.swing.*;

/**
 * Provides helper functionality for the GUI.
 * @author Jeppe Buk (jaybe@jaybe.dk)
 * @version $Revision: 1.2 $
 */
public class JdaiGuiHelpers {

    /**
     * Report an exception to the user.
     * @param e The exception.
     */
    public static void reportException(String text, Exception e) {
        JOptionPane.showMessageDialog(null,
				      text + "\n\n" + e.getMessage(),
				      "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static final String JLF_DIR = "toolbarButtonGraphics/";
    public static final String JFA_DIR = "org/javalobby/icons/";
    /** 
     * Returns the Icon associated with the name from the resources.
     * Tries both JLF icons and JFA icons (in that order).
     * The resouce should be in the path.
     * @param name Name of the icon file, e.g. navigation/Down16.gif or 20x20/ClockS.gif
     * @return the icon of the image or null if the icon is not found.
     */
    public static ImageIcon getIcon(String name)  {
	String imagePath = JLF_DIR + name;
	URL url = Thread.currentThread().getContextClassLoader().getResource(imagePath);
	if (url == null) {
	    imagePath = JFA_DIR + name;
	    url = Thread.currentThread().getContextClassLoader().getResource(imagePath);
	}
	if (url != null)  {
	    return new ImageIcon(url);
	}
	return null;
    }

    /**
     * Escape HTML-special characters in a string
     * @param s The string possibly containing HTML special characters to escape.
     * @return The escaped string.
     */
    public static final String escapeHtml(String s){
	if (s == null) return s;
	StringBuffer sb = new StringBuffer();
	int n = s.length();
	for (int i = 0; i < n; i++) {
	    char c = s.charAt(i);
	    switch (c) {
	    case '<': sb.append("&lt;"); break;
	    case '>': sb.append("&gt;"); break;
	    case '&': sb.append("&amp;"); break;
	    case '"': sb.append("&quot;"); break;
	    case '\n': sb.append("<br>"); break;
	    default:  sb.append(c); break;
	    }
	}
	return sb.toString();
    }
}
