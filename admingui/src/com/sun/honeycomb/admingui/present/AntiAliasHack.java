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



package com.sun.honeycomb.admingui.present;

/**
 *
 * @author ronaldso
 */
import com.sun.java.swing.SwingUtilities2;

import javax.swing.plaf.ComponentUI;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.UIManager;

/**
 * <p>Hacks the defaults table for the current look and feel to
 * set the AA_TEXT_PROPERTY_KEY on all components. The swing.aatext
 * sys prop does not work with webstart.</p>
 *
 * <p>Note that setting the AA_TEXT_PROPERTY_KEY client property on the
 * component in the createUI method does not seem to work. So components
 * to be fixed are collected and processed later using invokeLater.</p>
 */
public class AntiAliasHack extends ComponentUI {

private static Map map = new HashMap();
private static Fixer fixer = new Fixer();

/**
* Install us against the current look and feel.
*/
public static void install() {
    UIDefaults table = UIManager.getDefaults();
    List defaults = new ArrayList();
    String aaUIName = AntiAliasHack.class.getName();
    for (Enumeration i = table.keys(); i.hasMoreElements(); ) {
        Object key = i.nextElement();
        if (key instanceof String) {
            String uiClassID = (String) key;
            if (uiClassID.endsWith("UI")) {
                Object o = table.get(uiClassID);
                try {
                    Class cls = o instanceof Class
                        ? (Class) o
                        : Class.forName((String) o);
                    map.put(uiClassID,
                            cls.getMethod("createUI", new Class [] {
                                                        JComponent.class}));
                } catch (Exception e) {
                    System.out.println(
                        "$$$ AA hack failed " + uiClassID + " " + o);
                }
                defaults.add(uiClassID);
                defaults.add(aaUIName);
            }
        }
    }
    table.putDefaults(defaults.toArray());
}

/**
 * If you create components from a thread other than the
 * EventDispatchThread (e.g. your main thread creating a Frame) then you
 * must call this method when all components have been created.
 */
public static void fixComponents() {
    fixer.fixComponents();
}

public static ComponentUI createUI(JComponent c) {
    fixer.add(c);
    try {
        Method myMethod = (Method) map.get(c.getUIClassID());
        return (ComponentUI) myMethod.invoke(null, new Object [] {c});
    } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
        throw new RuntimeException(e.getCause());
    }
}

private static class Fixer implements Runnable {
    
    private List todo = new ArrayList();
    private boolean queued;
    
    public synchronized void add(JComponent c) {
        todo.add(c);
        if (!queued && SwingUtilities.isEventDispatchThread()) {
            queued = true;
            SwingUtilities.invokeLater(this);
        }
    }
    
    public synchronized void fixComponents() {
        if (!queued && !todo.isEmpty()) {
            queued = true;
            SwingUtilities.invokeLater(this);
        }
    }
    
    public synchronized void run() {
        for (ListIterator i = todo.listIterator(); i.hasNext(); ) {
            JComponent c = (JComponent) i.next();
            c.putClientProperty(
                SwingUtilities2.AA_TEXT_PROPERTY_KEY,
                Boolean.TRUE);
            c.repaint();
        }
        todo.clear();
        queued = false;
    }
    
}

}
