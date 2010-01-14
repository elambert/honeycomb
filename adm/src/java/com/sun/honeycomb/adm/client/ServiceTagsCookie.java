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


package com.sun.honeycomb.adm.client;

import com.sun.honeycomb.admin.mgmt.client.HCServiceTags;

/**
 * The data object that is used by MultiCellServiceTagsUpdater
 * to retrieve the new service tag data to be written out
 * to the cell.
 */
public class ServiceTagsCookie {
    
        HCServiceTags data;
        boolean updateRegistryFile;

        public ServiceTagsCookie(HCServiceTags data, boolean updateRegistryFile) {
            this.data = data;
            this.updateRegistryFile = updateRegistryFile;
        }
        
        /**
         * The new service tag data to written out to the cell.
         * Only the payload HCServiceTags.getData() is actually
         * used ont the remove side by the server
         * @return HCServiceTags the servicetag data to update the
         * remote cell with
         */
        public HCServiceTags getData() {
            return data;
        }
        
        /**
         * Flag to indicate whether the registry should be updated
         * on the master cell after the service tag data contained in 
         * HCServiceTags is written out to each cell.   The old 
         * service tag entries in the service tag registry are always 
         * removed.  Regardless of the setting of this value.  This
         * value basically tells us whether the data we are writting out
         * is valid or not.  If it's not valid we don't want to
         * create the service tag registry file entries. 
         * @return boolean, true update registry on master cell, false
         * otherwise.
         */
        public boolean updateRegistry() {
            return updateRegistryFile;
        }
}
