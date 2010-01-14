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



package com.sun.honeycomb.layout;

import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.disks.Disk;
import java.util.Random;
import com.sun.honeycomb.delete.Constants;

public class Layout extends DiskIdList {

    private static final String N_CONFIG_PARAM = "honeycomb.layout.datafrags";
    private static final String M_CONFIG_PARAM = "honeycomb.layout.parityfrags";

    private static int layoutSize = -1;

    private int mapId;
    private int[] disks;

    public Layout(int nMapId) {
        mapId = nMapId;

        if (layoutSize == -1) {
            ClusterProperties props = ClusterProperties.getInstance();
            int n = Integer.parseInt(props.getProperty(N_CONFIG_PARAM));
            int m = Integer.parseInt(props.getProperty(M_CONFIG_PARAM));
            layoutSize = n+m;
        }
        
        disks = new int[layoutSize];
        Random random = new Random(mapId);
        int i,j;
        for (i=0; i<disks.length; i++) {
            do {
                disks[i] = random.nextInt(Constants.NB_DISKS);
                for (j=0; j<i; j++) {
                    if (disks[i] == disks[j]) {
                        break;
                    }
                }
            } while (j<i);
        }
    }

    public int getMapId() {
        return mapId;
    }

    public Disk getDisk(int fragId) {
        return(LayoutClient.disks[disks[fragId]]);
    }

    public int size() {
        return(disks.length);
    }

    public String toString() {
        return(Integer.toString(mapId));
    }
}
