// JdaiSectionTreeModel.java
// $Id: JdaiSectionTreeModel.java 3456 2005-02-05 00:43:33Z rw151951 $

package dk.jdai.gui;

import dk.jdai.model.*;

import java.util.*;
import javax.swing.tree.*;
import javax.swing.event.*;

/**
 * Provides a TreeModel implementation for JDAI sections. This class is
 * undocumented since the methods are well known from the TreeModel interface.
 * @author Jeppe Buk (jaybe@jaybe.dk)
 * @version $Revision: 1.2 $
 */
public class JdaiSectionTreeModel implements TreeModel {
    
    private JdaiSection root;
    private EventListenerList eventListeners;

    private Map subSectionCache;

    public JdaiSectionTreeModel() {
        eventListeners = new EventListenerList();
        root = null;
        subSectionCache = new HashMap();
    }

    public void addTreeModelListener(TreeModelListener treeModelListener) {
        eventListeners.add(TreeModelListener.class, treeModelListener);
    }

    public Object getChild(Object obj, int index) {
        JdaiSection[] subSections = getSubSectionsCached((JdaiSection) obj);
        if (subSections != null && index < subSections.length)
            return subSections[index];
        else
            return null;
    }

    public int getChildCount(Object obj) {
        JdaiSection[] subSections = getSubSectionsCached((JdaiSection) obj);
        if (subSections != null)
            return subSections.length;
        else
            return 0;
    }

    public int getIndexOfChild(Object obj, Object obj1) {
        JdaiSection[] subSections = getSubSectionsCached((JdaiSection) obj);
        if (subSections != null)
            return Arrays.binarySearch(subSections, obj1);
        else
            return -1;
    }

    public Object getRoot() {
        return root;
    }

    public boolean isLeaf(Object obj) {
        return (getChildCount(obj) == 0);
    }

    public void removeTreeModelListener(TreeModelListener treeModelListener) {
        eventListeners.remove(TreeModelListener.class, treeModelListener);
    }

    public void setRoot(JdaiSection root) {
        this.root = root;
        fireTreeStructureChanged();
    }

    public void valueForPathChanged(TreePath treePath, Object obj) {
        throw new UnsupportedOperationException("JdaiSectionTreeModel does not support editing");
    }

    protected void fireTreeStructureChanged() {
        TreeModelEvent ev = new TreeModelEvent(this, new Object[] {root});
        Object[] listeners = eventListeners.getListenerList();
        for (int i = 0; i < listeners.length; i++) {
            if (listeners[i] == TreeModelListener.class) {
                // NOTE: Only every second element is the listener
                ((TreeModelListener) listeners[++i]).treeStructureChanged(ev);
            }
        }
    }

    private JdaiSection[] getSubSectionsCached(JdaiSection parent) {
        JdaiSection[] result = (JdaiSection[]) subSectionCache.get(parent);
        if (result == null) {
            result = parent.getSubSections();
            subSectionCache.put(parent, result);
        }
        return result;
    }
}
