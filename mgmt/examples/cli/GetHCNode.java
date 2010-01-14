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
 * This example fetches the HCNode object from the management server and
 * print its values
 */

import com.sun.honeycomb.admin.mgmt.client.Fetcher;
import com.sun.honeycomb.admin.mgmt.client.HCNode;
import java.math.BigInteger;
import com.sun.honeycomb.mgmt.common.MgmtException;

public class GetHCNode {

    public static void main(String[] arg) {

        String destination = "http://127.0.0.1:9001";
        if (arg.length > 1) {
            destination = arg[0];
        }

        System.out.println("Fetching the HCNode object from ["+
                           destination+"]");

        try {
            HCNode node = Fetcher.fetchHCNode(destination, BigInteger.valueOf(1));
            System.out.println("The node ["+node.getNodeId()+"] has been fetched");

            System.out.println("\n nodeid = " + node.getNodeId().intValue() + "\n" +
                               "\n hostname = " + node.getHostname() + "\n" +
                               "\n isAlive = " + node.isIsAlive() + "\n" +
                               "\n isEligible = " + node.isIsEligible() + "\n" +
                               "\n isMaster = " + node.isIsMaster() + "\n" +
                               "\n isViceMaster = " + node.isIsViceMaster() + "\n");


        } catch (MgmtException e) {
            e.printStackTrace();
        }

    }

}
