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



package com.sun.honeycomb.cm.node_mgr;

import com.sun.honeycomb.cm.ipc.Mailbox;
import com.sun.honeycomb.cm.ManagedService;
import java.lang.Comparable;
import java.io.IOException;
import java.util.logging.Logger;
import com.sun.honeycomb.util.ExtLevel;
import com.sun.honeycomb.util.BundleAccess;
import java.util.ResourceBundle;


class ServiceMailbox implements Comparable {

    private static final Logger logger = 
        Logger.getLogger(ServiceMailbox.class.getName());

    static final int MAX_FAILURES = 1; // first failure = escalation
    static final int ESCALATION_TIMEOUT = (5 *(60 * 1000)); // 5m

    Service service;
    Mailbox mailbox;
    int     jvmId;
    boolean masterOnly;
    boolean keepAlive;
    String  svcClass;
    int     runLevel;
    int     nbFailures;
    Timeout timeout;

    ServiceMailbox(int     id, 
                   String  cls, 
                   String  tag, 
                   int     level, 
                   boolean master,
                   int shutdownTimeout,
                   String  options,
                   boolean jvm) throws IOException {
        mailbox = new Mailbox(tag, true);
        service = new Service(tag, cls, id, level, master, shutdownTimeout, jvm);
        masterOnly = master;
        runLevel = level;
        svcClass = cls;
        jvmId = id;
        nbFailures = 0;
        parseOptions(options);
        timeout = new Timeout(ESCALATION_TIMEOUT);
        timeout.disable();
        mailbox.rqstCancel();
    }

    ServiceMailbox(int     id, 
                   String  cls, 
                   String  tag, 
                   int     level, 
                   boolean master,
                   boolean jvm) throws IOException {
        this(id, cls, tag, level, master, -1, null, jvm);
    }

    boolean masterOnly() {
        return masterOnly;
    }

    boolean keepAlive() {
        return keepAlive;
    }

    int getId() {
        return jvmId;
    }

    Service getService() {
        if (mailbox.isRunning()) {
            service.state = ManagedService.ProxyObject.RUNNING;
        } else if (mailbox.isReady()) {
            service.state = ManagedService.ProxyObject.READY;
        } else if (mailbox.isInit()) {
            service.state = ManagedService.ProxyObject.INIT;
        } else {
            service.state = ManagedService.ProxyObject.DISABLED;
        }
        try {
            JVMProcess jvm = NodeConfig.getInstance().getJVM(this);
            service.maxRss = jvm.getMaxRss();
        } catch (MgrException e) {
            logger.severe("ClusterMgmt - cannot access node config.");
            ResourceBundle rs = BundleAccess.getInstance().getBundle();
            String str = rs.getString("err.cm.node_mgr.ServiceMailbox.nodeaccess");
            logger.log(ExtLevel.EXT_SEVERE, str);
        }
        return service;
    }

    int getLevel() {
        return runLevel;
    }

    Class getServiceClass() throws ClassNotFoundException {
        return Class.forName(service.cls);
    }

    String getTag() {
        return service.tag;
    }

    void setManaged(boolean managed) {
        service.isManaged = managed;
    }

    boolean isManaged() {
        return service.isManaged;
    }

    // FIXME - service group should be defined in the node config file
    // per service.
    boolean isPartOf(int what) {
        
        switch (what) {
            
        case NodeMgrService.MASTER_SERVICES:
        case NodeMgrService.ALL_MASTER_SERVICES:
            return masterOnly;

        case NodeMgrService.INIT_SERVICES:
            return (runLevel == 0);

        case NodeMgrService.PLATFORM_SERVICES:
            return (runLevel == 1);

        case NodeMgrService.IO_SERVICES:
            // HACK !!!
            if (svcClass.matches(".*hadb.MasterService")) {
                return true;
            }
            return (!masterOnly && (runLevel >= 2));

        case NodeMgrService.API_SERVICES:
            // HACK !!!
            if (svcClass.matches(".*protocol.server.ProtocolService")) {
                return true;
            }
            return false;

        case NodeMgrService.ALL_SERVICES:
            return true;

        default:
            return false;
        }
    }

    String getClassName() {
        return service.getClassName();
    }

    void setInitRqstReady() {
        mailbox.rqstInit();
        timeout.armIfDisabled();
    }

    void rqstReady() {
        mailbox.rqstReady();
        timeout.armIfDisabled();
    }

    void rqstRunning() {
        service.incIncarnationNumber();
        mailbox.rqstRunning();
        timeout.armIfDisabled();
    }

    boolean isReady() {
        return mailbox.isReady();
    }

    boolean isRunning() {
        return mailbox.isRunning();
    }

    boolean isDisable() {
        return mailbox.isDisabled();
    }

    boolean isJVM() {
        return service.isJVM();
    }

    void reset() {
        nbFailures = 0;
        mailbox.rqstCancel();
        mailbox.setInit();
    }

    void disable() {
        mailbox.setDisabled();
    }

    boolean start() throws MgrException {
        JVMProcess jvm = NodeConfig.getInstance().getJVM(this);
        if (!jvm.healthCheck()) {
            throw new MgrException("process " + jvm + " died");
        }
        if (mailbox.isRunning()) {
            timeout.disable();
            return true;
        }
        if (mailbox.isReady()) {
            timeout.disable();
            return true;
        } else if (mailbox.isInit()) {
            //logger.info("ClusterMgmt - " + getName() + " INIT->READY");
            if (timeout.hasExpired()) {
                throw new MgrException(getName() + " - timeout expired " + 
                                       "escalation -");
            }
            rqstReady();
            return false;
        } else if (mailbox.isDisabled()) {
            logger.severe("ClusterMgmt - " + getName() + " found DISABLED");
            if (++nbFailures >= MAX_FAILURES) {
                nbFailures = 0;
                throw new MgrException(getName() + " - escalation -");
            }
            timeout.disable();
            setInitRqstReady();
        }
        return false;
    }

    //
    // Return true if we reached the READY state or if disabled.
    // 
    boolean stop() throws MgrException {
        if (mailbox.isRunning() || mailbox.isInit()) {
            if (timeout.hasExpired()) {
                throw new MgrException(getName() + " - timeout expired " + 
                                       "escalation -");
            }
            mailbox.rqstReady();
            return false;
        } else if (mailbox.isReady()) {
            nbFailures = 0;
            timeout.disable();
            return true;
        } else if  (mailbox.isDisabled()) {
            timeout.disable();
            return true;
        }
        // not reached
        return true;
    }

    boolean monitor(boolean restart) throws MgrException {
        JVMProcess jvm = NodeConfig.getInstance().getJVM(this);
        if (!jvm.healthCheck()) {
            throw new MgrException("process " + jvm + " died");
        }
        if (mailbox.isRunning()) {
            timeout.disable();
            return true;
        } else if (mailbox.isReady()) {
            if (restart) {
                logger.info("ClusterMgmt - " + getName() + " READY->RUNNING");
                if (timeout.hasExpired()) {
                    throw new MgrException(getName() + " - timeout expired " + 
                                           "escalation -");
                }
                rqstRunning();
                return false;
            } else {
                nbFailures = 0;                
                timeout.disable();
                return true;
            }
        } else if (mailbox.isInit()) {
            logger.warning("ClusterMgmt - " + getName() + " found INIT");
            timeout.disable();
            rqstReady();

        } else if (mailbox.isDisabled()) {
            logger.warning("ClusterMgmt - " + getName() + " found DISABLE");
            if (++nbFailures >= MAX_FAILURES) {
                throw new MgrException(getName() + " - escalation");
            }
            logger.info("ClusterMgmt - " + getName() + " DISABLE->INIT");
            timeout.disable();
            setInitRqstReady();
        }
        return false;
    }

    public int compareTo(Object obj) {
        ServiceMailbox target = (ServiceMailbox) obj;
        if (service.runlevel != target.service.runlevel) {
            return service.runlevel - target.service.runlevel;
        }
        if (jvmId != target.jvmId) {
            return jvmId - target.jvmId;
        }
        return service.tag.compareTo(target.service.tag);
    }

    public String toString() {
        return "[tag=" + service.tag + 
            " class=" + service.cls + 
            " runlevel=" + service.runlevel + 
            " master only=" + service.masterOnly +
            " state(" + getStatus() + ")" +
            " options(" + (keepAlive? "keepAlive" : "") + ")]";
    }

    public void toLog() {
        logger.info("mbox " + service.tag + " size " + mailbox.size());
    }

    String getName() {
        String[] parts = service.tag.split("/");
        if (parts.length > 1) {
            return parts[parts.length - 1];
        }
        return service.tag;
    }

    String getStatus() {
        if (mailbox.isRunning()) {
            return "running";
        } else if (mailbox.isInit()) {
            return "init";
        } else if (mailbox.isReady()) {
            return "ready";
        } else if (mailbox.isDisabled()) {
            return "disabled";
        } else {
            return "???";
        }
    }

    private void parseOptions(String optstring) {
        // Currently, the only known option is "keepAlive", used
        // for the AdminService (CLI)

        keepAlive = false;

        if (optstring == null)
            return;

        String[] options = optstring.trim().split("[ \t]*,[ \t]*");
        for (int i = 0; i < options.length; i++) { 
            String option = options[i];
            if (option == null || option.length() == 0)
                continue;

            if (option.equalsIgnoreCase("keepAlive"))
                keepAlive = true;

            else
                logger.warning("Service " + service.getTag() +
                               " has unknown option \"" + option + "\"");
        }
    }
}

