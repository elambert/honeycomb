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



package com.sun.honeycomb.cm.cluster_membership;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.HashMap;

import com.sun.honeycomb.cm.cluster_membership.messages.Message;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.Commit;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.Update;

import com.sun.honeycomb.stressconfig.ConfigStresser;

public class LobbyTaskStressTest extends LobbyTask {

    private static final int ACTION_NORMAL = 0;
    private static final int ACTION_DROP_MESSAGE = -1;
    private static final int ACTION_SET_STATUS_FALSE = -2;

    private static final Logger logger =
        Logger.getLogger(LobbyTaskStressTest.class.getName());

    public LobbyTaskStressTest(SenderTask sender) {
        super(sender);
        logger.info(CMMApi.LOG_PREFIX +
          "LobbyTaskStressTest has been loaded ");
    }


    private int processUpdateHook(Update msg) {

        Node local = NodeTable.getLocalNode();

        if (msg.getFileToUpdate() == CMMApi.UPDATE_STRESS_TEST.val()) {

            HashMap map = ConfigStresser.parseFile(msg.getVersion());
            if (map == null) {
                logger.severe(CMMApi.LOG_PREFIX +
                  "Failed to parse config file");
                return ACTION_NORMAL;
            }
            boolean dropFile = ConfigStresser.isDropFlagForUpdate(map,
              local.nodeId(), msg.getVersion());
            if (dropFile) {
                logger.info(CMMApi.LOG_PREFIX +
                  "Node " +  local.nodeId() + " drops the update message");
                return ACTION_DROP_MESSAGE;
            }

            boolean setStatusFalse = ConfigStresser.isSetStatusFalseForUpdate(
                map, local.nodeId(), msg.getVersion());
            if (setStatusFalse) {
                logger.info(CMMApi.LOG_PREFIX +
                  "Node " +  local.nodeId() + 
                  " sets status update to false");
                return ACTION_SET_STATUS_FALSE;
            }
        }
        return ACTION_NORMAL;
    }

    protected void processUpdateNonMaster(Update msg) {

        boolean success =  doActionOnUpdateNonMaster(msg);
        int hook = processUpdateHook(msg);

        switch(hook) {
        case ACTION_NORMAL:
            break;

        case ACTION_DROP_MESSAGE:
            return;

        case ACTION_SET_STATUS_FALSE:
            success = false;
            break;

        default:
            throw new CMMError("Unsupported action for hook val = " +
                hook);
        }
        setStatusOnUpdateAndSendNext(msg, success);
    }


    protected void processCommit(Commit msg) {

        CMMApi.ConfigFile cfg = CMMApi.ConfigFile.lookup(msg.getFileToUpdate());
        boolean success = CfgUpdUtil.getInstance().activate(cfg, msg.getVersion());

        int hook = processCommitHook(msg);
        switch(hook) {
        case ACTION_NORMAL:
            break;

        case ACTION_DROP_MESSAGE:
            return;

        case ACTION_SET_STATUS_FALSE:
            success = false;
            break;

        default:
            throw new CMMError("Unsupported action for hook val = " +
                hook);
        }
        setStatusOnCommitAndSendNext(msg, success);
    }


    private int processCommitHook(Commit msg) {

        Node local = NodeTable.getLocalNode();

        if (msg.getFileToUpdate() == CMMApi.UPDATE_STRESS_TEST.val()) {

            HashMap map = ConfigStresser.parseFile(msg.getVersion());
            if (map == null) {
                logger.severe(CMMApi.LOG_PREFIX +
                  "Failed to parse config file");
                return ACTION_NORMAL;
            }

            boolean dropFile = ConfigStresser.isDropFlagForCommit(map,
              local.nodeId(), msg.getVersion());
            if (dropFile) {
                logger.info(CMMApi.LOG_PREFIX +
                  "Node " +  local.nodeId() + " drops the commit message");
                return ACTION_DROP_MESSAGE;
            }

            boolean setStatusFalse = ConfigStresser.isSetStatusFalseForCommit(
                map, local.nodeId(), msg.getVersion());
            if (setStatusFalse) {
                logger.info(CMMApi.LOG_PREFIX +
                  "Node " +  local.nodeId() + 
                  " sets status commit to false");
                return ACTION_SET_STATUS_FALSE;
            }
        }
        return ACTION_NORMAL;
    }
}
