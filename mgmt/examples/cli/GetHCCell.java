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



/*
 * This example fetches the HCCell object from the management server and
 * print its values
 */

import com.sun.honeycomb.admin.mgmt.client.Fetcher;
import com.sun.honeycomb.admin.mgmt.client.HCCell;
import com.sun.honeycomb.admin.mgmt.client.HCCellProps;
import com.sun.honeycomb.admin.mgmt.client.HCSiloProps;
import com.sun.honeycomb.admin.mgmt.client.HCAlertAddr;
import com.sun.honeycomb.admin.mgmt.client.HCNode;
import com.sun.honeycomb.admin.mgmt.client.HCDisk;
import com.sun.honeycomb.mgmt.common.MgmtException;
import java.util.Iterator;
import java.math.BigInteger;

public class GetHCCell {

    public static void main(String[] arg) {

        String destination = "http://127.0.0.1:9001";
        if (arg.length >= 1) {
            destination = arg[0];
        }

        System.out.println("-> Fetching the HCCell object from ["+
                           destination+"]");

        for (int o = 0; o < 10; o++) {
        try {
            HCCell cell = Fetcher.fetchHCCell(destination);
            HCCellProps cellProps = cell.getCellProps();
            HCSiloProps siloProps = cell.getSiloProps();
            HCAlertAddr alertAddr = cell.getAlertAddr();

            System.out.println("-> ");
            System.out.println("-> The cell ["+cell.getCellId()+"] has been fetched:\n"+
                               "\tcellid: " + cell.getCellId() +"\n" +
                               "\tversion: "+cell.getVersion()+"\n"+
                               "\tlicense: " + cell.getLicense() +"\n" +
                               "\tlanguage: " + cell.getLanguage() +"\n" +
                               "\texpansion nodeid: " + cell.getExpansionNodeIds() +"\n" +
                               "\texpansion nodeid: " + cell.getExpansionStatus() +"\n" +
                               "\tdataVIP: "+cellProps.getDataVIP()+"\n"+
                               "\tadminVIP: "+cellProps.getAdminVIP()+"\n"+
                               "\tspVIP: "+cellProps.getSpVIP()+"\n"+
                               "\tsubnet: "+cellProps.getSubnet()+"\n"+
                               "\tgateway: "+cellProps.getGateway()+"\n"+
                               "\tntp server: "+siloProps.getNtpServer()+"\n"+
                               "\tsmtp server: "+siloProps.getSmtpServer()+"\n"+
                               "\tsmtp port: "+siloProps.getSmtpPort()+"\n"+
                               "\tauthorized clients: "+siloProps.getAuthorizedClients()+"\n"+
                               "\texternal logger: "+siloProps.getExtLogger()+"\n"+
                               "\talert addr to: "+alertAddr.getSmtpTo()+"\n"+
                               "\talert addr cc: "+alertAddr.getSmtpCC()+"\n"+
                               "\tmaster node: "+cell.getMasterNode()+"\n "+
                               "\tis protocol running: "+cell.isProtocolRunning()+"\n"+
                               "\tis quorum reached "+cell.isQuorumReached()+"\n"+
                               "\tnb alive nodes: "+cell.getNoAliveNodes()+"\n"+
                               "\tnb alive disks: "+cell.getNoAliveDisks()+"\n"+
                               "\tis possible data loss: "+cell.isPossibleDataLoss()+"\n"+
                               "\tis cluster sane: "+cell.isClusterSane()+"\n"+
                               "\ttotal capacity: "+cell.getTotalCapacity()+"\n"+
                               "\tused capacity: "+cell.getUsedCapacity()+"\n"+
                               "\tnb unhealed failures: "+cell.getNoUnhealeadFailures()+"\n"+
                               "\tnb unhealed unique failures: "+cell.getNoUnhealeadUniqueFailures()+"\n"+
                               "\tnb get end time last recovery cycle: "+cell.getEndTimeLastRecoverCycle()+"\n"+
                               "\tNb of nodes: "+cell.getNodeIds().size());

            System.out.println("List of nodes :");            
            Iterator<BigInteger> ite = cell.getNodeIds().iterator();
            while (ite.hasNext()) {
                HCNode node = Fetcher.fetchHCNode(destination, ite.next());
                System.out.println("Node "+node.getNodeId()+":"+
                                   "\tStatus: "+node.getStatus());
            }

            System.out.println("FmwVersions :");            
            Iterator<String> iter = cell.getFmwVersions().iterator();
            while (iter.hasNext()) {
                String fmwVersion = iter.next();
                System.out.println("\t" + fmwVersion);
            }


            System.out.println("-> Retrieve list of disks");
            Iterator<HCDisk> it = cell.getDisks().iterator();
            while (it.hasNext()) {
                HCDisk disk = it.next();
                System.out.println("- got disk, diskid = " + disk.getDiskId());
            }
            

        } catch (MgmtException e) {
            e.printStackTrace();
            System.exit(1);
        }

        try {
            System.out.println("-> iteration " + (o + 1 )+ " done, sleeps for 5 seconds");
            Thread.sleep(5000);
        } catch (InterruptedException ignored) {
        }

        }
    }
}


