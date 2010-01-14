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



package com.sun.honeycomb.hctest.cli;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SPFru extends FruInfo
{
    /**
     * @param hwstat_sp_output - the service processor output line from 'hwstat'
     * @throws IllegalArgumentException if the output is not in the expected
     * format or the name, state, or type does not have the expected value 
     */
    protected SPFru(String hwstat_sp_output) 
    {
        super(hwstat_sp_output);
        if (getName().equals("SN") == false) {
            throw new IllegalArgumentException("Invalid hwstat name of '" + getName()
		+ "' specified for service processor.");
        }
        
        // Only allowed status for SN are ONLINE, OFFLINE, UNKNOWN
	String status = getStatus();
        if (!(status.equals(ONLINE))
            || status.equals(OFFLINE) 
            || status.equals(UNKNOWN)) {
            throw new IllegalArgumentException("Invalid status of '" + status
                    + "' specified for the service processor.");
        }

	if (getFruType().equals(FRU_TYPE_SP) == false)
	    throw new IllegalArgumentException("Invalid type of '" + getFruType()
		    + "' specified for the service processor.");
    }
}
