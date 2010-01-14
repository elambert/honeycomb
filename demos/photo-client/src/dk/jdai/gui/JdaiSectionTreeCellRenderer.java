// JdaiSectionTreeCellRenderer.java
// $Id: JdaiSectionTreeCellRenderer.java 3456 2005-02-05 00:43:33Z rw151951 $

package dk.jdai.gui;


import javax.swing.tree.*;

/**
 * Provides a tree cell renderer for JDAI sections. This class is undocumented
 * since the methods are well known from the TreeCellRenderer interface.
 * @author Jeppe Buk (jaybe@jaybe.dk)
 * @version $Revision: 1.2 $
 */
public class JdaiSectionTreeCellRenderer extends DefaultTreeCellRenderer {
    
    public JdaiSectionTreeCellRenderer() {
	super();
	closedIcon = JdaiGuiHelpers.getIcon("20x20/Folder.gif");
	openIcon = JdaiGuiHelpers.getIcon("20x20/Open.gif");
	leafIcon = closedIcon;
    }
}
