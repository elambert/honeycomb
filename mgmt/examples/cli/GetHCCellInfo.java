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
 * This example fetches the HCCellInfo object from the management server and
 * print its values
 */

import com.sun.honeycomb.multicell.mgmt.client.Fetcher;
import com.sun.honeycomb.multicell.mgmt.client.HCCellInfo;
import com.sun.honeycomb.multicell.mgmt.client.HCRule;
import com.sun.honeycomb.mgmt.common.MgmtException;
import java.util.List;
import java.math.BigInteger;

public class GetHCCellInfo {

    public static void main(String[] arg) {

        String destination = "http://127.0.0.1:9000";
        if (arg.length >= 1) {
            destination = arg[0];
        }

        System.out.println("-> Fetching the HCCellInfo object from ["+
                           destination+"]");

        try {
            HCCellInfo cell = Fetcher.fetchHCCellInfo(destination);
            System.out.println("cellid = " + cell.getCellid() +
                               ", domainname = " + cell.getDomainName() +
                               ", adminVIP = " + cell.getAdminVIP() +
                               ", dataVIP = " + cell.getDataVIP() +
                               ", spVIP = " + cell.getSpVIP() +
                               ", subnet = " + cell.getSubnet() + 
                               ", gateway = " + cell.getGateway() +
                               ", totalCapacity = " + cell.getTotalCapacity() +
                               ", usedCapacity = " + cell.getUsedCapacity());

            List<HCRule> rules = cell.getRules();
            for (int i = 0; i < rules.size(); i++) {
                System.out.println(". ruleNumber = " +
                                   rules.get(i).getRuleNumber() +
                                   ", originCellId = " + 
                                   rules.get(i).getOriginCellId() +
                                   ", start = " + 
                                   rules.get(i).getStart() +
                                   ", end = " + 
                                   rules.get(i).getEnd() +
                                   ", distance = " + 
                                   rules.get(i).getDistance() +
                                   ", initialCapacity = " + 
                                   rules.get(i).getInitialCapacity());
            }

        } catch (MgmtException e) {
            System.err.println("STEPH MgmtException " + e);
            e.printStackTrace();
            System.exit(1);
        } catch (RuntimeException rte) {
            System.out.println("STEPH runtime exception " + rte);
            System.exit(1);
        } catch (Throwable th) {
            System.out.println("STEPH Throwable" + th);
            System.exit(1);
        }
        System.exit(0);
    }

}


