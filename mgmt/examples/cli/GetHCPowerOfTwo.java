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
 * This example fetches the HcPowerOfTwo object from the management server and
 * print its values
 */

import com.sun.honeycomb.multicell.mgmt.client.Fetcher;
import com.sun.honeycomb.multicell.mgmt.client.HCPowerOfTwo;
import com.sun.honeycomb.multicell.mgmt.client.HCCellCapacity;
import com.sun.honeycomb.multicell.mgmt.client.HCRule;
import com.sun.honeycomb.mgmt.common.MgmtException;
import java.util.List;
import java.util.ArrayList;
import java.math.BigInteger;

public class GetHCPowerOfTwo {

    public static void main(String[] arg) {

        String destination = "http://127.0.0.1:9000";
        if (arg.length >= 1) {
            destination = arg[0];
        }

        System.out.println("-> Fetching the HCPowerOfTwo object from ["+
                           destination+"]");

        HCPowerOfTwo pot = null;
        try {
            pot = Fetcher.fetchHCPowerOfTwo(destination);
            List<HCCellCapacity> list = pot.getCellCapacity();
            for (int i = 0; i < list.size(); i++) {
                HCCellCapacity curCellCapacity = list.get(i);
                System.out.println("cell " + curCellCapacity.getCellid() +
                                   ", versionMinor = " + 
                                   curCellCapacity.getVersionMinor() +
                                   ", usedCapacity = " + 
                                   curCellCapacity.getUsedCapacity());
            }
        } catch (Exception e) {
            System.err.println("Exception " + e);
            System.exit(-1);

        } 


//         System.out.println("-> Setting the cell capacity ");
//         List<HCCellCapacity> list = pot.getCellCapacity();
//         list.clear();
//         for (int i = 0; i < 3; i++) {
//             HCCellCapacity curCellCapacity = new HCCellCapacity();
//             curCellCapacity.setVersionMinor(i);
//             curCellCapacity.setTotalCapacity(1000 + i * 1000);
//             curCellCapacity.setUsedCapacity(500 + i * 500);
//             list.add(curCellCapacity);
//         }

//         try {
//             pot.push();
//         } catch (Exception e) {
//             System.err.println("Exception " + e);
//             System.exit(-1);
//         } 
//     }


// 	HCPowerOfTwo newPot = new HCPowerOfTwo();
//         List<HCCellCapacity> list = newPot.getCellCapacity();
//         list.clear();
//         for (int i = 0; i < 3; i++) {
//             HCCellCapacity curCellCapacity = new HCCellCapacity();
//             curCellCapacity.setVersionMinor(i);
//             curCellCapacity.setTotalCapacity(1000 + i * 1000);
//             curCellCapacity.setUsedCapacity(500 + i * 500);
//             list.add(curCellCapacity);
//         }

// 	try {
// 	    byte res = pot.pushNewPowerOfTwo(newPot);
// 	} catch (Exception e) {
//              System.err.println("Exception " + e);
//              System.exit(-1);
// 	}
    }
}
