/*
 * Copyright © 2008, Sun Microsystems, Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 *    * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 *    * Neither the name of Sun Microsystems, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */



package com.sun.honeycomb.alertviewer;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.Enumeration;

public class AlertTreeNode
    extends DefaultMutableTreeNode {

    public static DefaultTreeModel model;

    private boolean marked;

    public AlertTreeNode(String tag) {
        super(tag);
        marked = false;
    }

    public String getTag() {
        return((String)getUserObject());
    }

    public void setTag(String newTag) {
        setUserObject(newTag);
    }

    public void sweep() {
        Enumeration childrenEnum = children();
        while (childrenEnum.hasMoreElements()) {
            AlertTreeNode child = (AlertTreeNode)childrenEnum.nextElement();
            if (!child.clearAndReturnMarked()) {
                model.removeNodeFromParent(child);
            }
        }
    }
    
    public boolean clearAndReturnMarked() {
        boolean result = marked;
        marked = false;
        return(result);
    }

    public void update(String[] nodes,
                       int index,
                       String value) {

        marked = true;

        // Find the child
        String tag = nodes[index];
        Enumeration childrenEnum = children();
        AlertTreeNode child = null;
        boolean found = false;

        while (childrenEnum.hasMoreElements()) {
            child = (AlertTreeNode)childrenEnum.nextElement();
            if ( (index < nodes.length-1) && (child.getTag().equals(tag))) {
                found = true;
                break;
            }
            if ( (index == nodes.length-1) && (child.getTag().startsWith(tag)) ) {
                found = true;
                break;
            }
        }
        
        if (!found) {
            // The node has to be created
            if (index == nodes.length-1) {
                child = new AlertTreeNode(tag+"="+value);
            } else {
                child = new AlertTreeNode(tag);
            }
            add(child);
            model.insertNodeInto(child, this, getChildCount()-1);
        } else {
            // If this is a leaf check the value
            String realValue = tag+"="+value;
            if ( (index == nodes.length-1) && (!child.getTag().equals(realValue)) ) {
                child.setTag(realValue);
                model.nodeChanged(child);
            }
        }
        
        if (index < nodes.length-1) {
            child.update(nodes, index+1, value);
        }
    }
}
