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

import com.sun.honeycomb.admin.mgmt.client.Fetcher;
import com.sun.honeycomb.admin.mgmt.client.HCCell;
import com.sun.honeycomb.mgmt.common.MgmtException;
import java.util.List;
import java.math.BigInteger;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

public class HCCellCmd extends HCCmd {

    private HCCell    cell = null;

    private static final String CLASS_NAME = 
        "com.sun.honeycomb.admin.mgmt.client.HCCell";


    public static void main(String[] arg) {
        HCCellCmd test = new HCCellCmd(arg);
    }

    
    public HCCellCmd(String [] arg) {

        super("HCCellCmd" , arg);

        try {
            cell = Fetcher.fetchHCCell(destination);
        } catch (MgmtException e) {
            e.printStackTrace();
            System.exit(1);
        }
        run(cell, commandName, args);
    }

    protected String getClassName() {
        return CLASS_NAME;
    }

}
