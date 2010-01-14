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



package com.sun.honeycomb.alert.tool;

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.ipc.MailboxReader;
import com.sun.honeycomb.cm.jvm_agent.Service;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.alert.AlertType;
import com.sun.honeycomb.alert.AlertComponent;
import com.sun.honeycomb.alert.AlertException;
import java.io.ObjectInputStream;


/*
 * Small tool to read the content (alert properties) of the mailboxes
 */
public class AlertMailbox {

    private Service.ProxyHeader hdr;
    private ManagedService.ProxyObject proxy;
    private String tag;
    private boolean directChildren;

    public AlertMailbox(String t, boolean direct) {
        tag = t;
        directChildren = direct;

        try {
            MailboxReader mailbox = new MailboxReader(tag);
            ObjectInputStream in = new ObjectInputStream(mailbox);
            hdr = (Service.ProxyHeader) in.readObject();
            proxy = (ManagedService.ProxyObject) in.readObject(); 
        } catch(Exception e) {
            System.err.println("Cannot create AlertMailbox " + e);
            System.exit(1);
        }
        printAlerts(proxy, 0, tag);
    }

    static public void main(String argv[]) {
        if (argv.length < 1 || argv.length > 2) {
            usage();
            System.exit(1);
        }
        boolean direct = false;
        if (argv.length == 2) {
            direct = Boolean.valueOf(argv[1]).booleanValue();
        }
        AlertMailbox mbox = new AlertMailbox(argv[0], direct);
    }

    static private void usage() {
            System.err.println("usage : AlertMailbox <TAG> <directChildren>");
            System.err.println("\twhere TAG = <node>/service, " + 
                               "directChildren = true/false, " + 
                               "(default = false)");
    }

    private void printAlerts(AlertComponent comp, int level, String proxyName) {
        AlertComponent.AlertProperty prop = null;

        if (comp != null) {
            int nbChildren = comp.getNbChildren();

            for (int l = 0; l < level; l++) {
                System.out.print("\t");
            }
            System.out.println("  (<" + proxyName + "> : " +
                               nbChildren + " children)");
            for (int i = 0; i < nbChildren; i++) {
                try {
                    prop = comp.getPropertyChild(i);
                } catch (AlertException ae) {
                    System.err.println("cannot retrieve child, exception" + ae);
                }

                for (int l = 0; l < level; l++) {
                    System.out.print("\t");
                }
                System.out.print("- prop " + prop.getName() + " value = ");

                try {
                    switch (prop.getType()) {
                    case AlertType.INT:
                        System.out.println(comp.getPropertyValueInt(prop.getName()));
                        break;
                    case AlertType.LONG:
                        System.out.println(comp.getPropertyValueLong(prop.getName()));
                        break;
                    case AlertType.FLOAT:
                        System.out.println(comp.getPropertyValueFloat(prop.getName()));
                        break;
                    case AlertType.DOUBLE:
                        System.out.println(comp.getPropertyValueDouble(prop.getName()));
                        break;
                    case AlertType.STRING:
                        System.out.println(comp.getPropertyValueString(prop.getName()));
                        break;
                    case AlertType.BOOLEAN:
                        System.out.println(comp.getPropertyValueBoolean(prop.getName()));
                        break;
                    case AlertType.COMPOSITE:
                        System.out.println(" COMPOSITE ");
                        if (directChildren == true) {
                            AlertComponent childComp = comp.getPropertyValueComponent(prop.getName());
                            printAlerts(childComp, (level + 1), prop.getName());
                        }
                        break;
                    default:
                        System.err.println("wrong property type");
                        System.exit(1);
                        break;
                    }
                } catch (AlertException ae) {
                    System.err.println("exception retrieving the " +
                                       "property value for " +
                                       prop.getName() + ae);
                    System.exit(1);
                }
            }
        }
    }
}