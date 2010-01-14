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



package com.sun.honeycomb.admingui.client;

/**
 * encapsulates minimal info needed to communicate with a cell
 */
public class Cell {

    private int id;
    private boolean alive, switchesOk;
    private long estFree;
    private boolean master;
    private int numNodes;

    Cell(int id, boolean alive, String estFree) {
        this(id, alive, estFree, 16);
    }
    Cell(int id, boolean alive, String estFree, int nodes) {
        this.id = id;
        this.alive = alive;
        this.estFree = -1;
        this.numNodes = nodes;
        try {
             this.estFree = (long)Double.parseDouble(estFree);
        } catch (Exception e) {
            System.out.println("invalid estFree for cell " + id + ":" +
                estFree);
        }
        this.master = false;
    }
    void setMaster(boolean master) {
        this.master = master;
    }
    public int getNumNodes() { return numNodes; }
    public int getID() { return id; }
    public boolean isAlive() { return alive; }
    public long getEstFreeSpace() { return estFree; }
    public boolean isMaster() { return master; }
    public String toString() { return "cell{" + id + "," +
            (alive ? "alive," : "down,") + estFree + "}"; }
    
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof Cell)) {
            return false;
        }
        Cell c = (Cell)obj;
        return c.getID() == this.getID();       
    }
    
    public int hashCode() {
        return id;
    }
}
