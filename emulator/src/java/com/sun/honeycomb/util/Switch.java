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



package com.sun.honeycomb.util;

import com.sun.honeycomb.admin.mgmt.server.HCSwitch;
import com.sun.honeycomb.admin.mgmt.server.ValuesRepository;

/**
 * Static switch class used to process switch commands in the emulator 
 */
public class Switch {

    
    private static final int PRIMARY_SWITCH_ID = 1;
    private static final int BACKUP_SWITCH_ID = 2;
    private static final int[] SWITCH_IDS = { PRIMARY_SWITCH_ID, BACKUP_SWITCH_ID }; 
    
    /**
     * Returns the list of switch id's
     * @return int[] array of switch id
     */
    public static int[] getIds() {
        return SWITCH_IDS;
    }
    
    /**
     * @param switchId the id that identifies the switch that should be 
     * retrieved.
     * @return HCSwitch the fru switch object for the specified switch id
     */
    public static HCSwitch getSwitchFru(int switchId)
    {
        return ValuesRepository.getInstance().getSwitchFru(switchId);
    }
			
}
