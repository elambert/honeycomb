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



package com.sun.honeycomb.client;

import com.sun.honeycomb.common.ArchiveException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.SAXParser;
import org.xml.sax.SAXException;

import java.util.logging.Level;
import java.util.Random;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.io.IOException;
import java.io.InputStream;


public class MultiCell {

    static final boolean USE_PCT_FREE = true;

    static final private short MAX_CELLS = 250;
    private HashMap cells;
    static private Random random = new Random(System.currentTimeMillis());
    private short[]  cellIds;
    Cell defaultCell;  // to hold starting dataVIP/port
    private int nbCells;
    private long majorVersion = -1;
    private long minorVersion = 0;

    public MultiCell(String defaultVIP, int defaultPort) {
        cells = new HashMap();
        cellIds = new short[MAX_CELLS];
        nbCells = 0;
        defaultCell = new Cell((short)-1, defaultVIP, defaultPort, -1L, -1L);
    }

    synchronized public void readConfig(InputStream stream) 
        throws IOException {

        cells = new HashMap();
        cellIds = new short[MAX_CELLS];
        nbCells = 0;

        try {
            SAXParserFactory parserFactory = SAXParserFactory.newInstance();
            SAXParser parser = parserFactory.newSAXParser();
            XMLConfigHandler handler = new XMLConfigHandler(this);
            parser.parse(stream, handler);
        } catch (SAXException e) {
            throw new IOException("SAX can't parse input file: " + e);
        } catch (ParserConfigurationException e) {
            throw new IOException("ParserConfigurationException while parsing input file: " + e);
        }
    }

    synchronized public void setMajorVersion(long v) {
        majorVersion = v;
    }
    synchronized public void setMinorVersion(long v) {
        minorVersion = v;
    }
    synchronized public String getVersion() {
        return "" + majorVersion + "." + minorVersion;
    }

/*
not used
    synchronized public void update(short id, String dataVIP, long totalCapacity, long usedCapacity) {
	Cell cell = null;
	cell = (Cell) cells.get(new Integer(id));
	if (cell == null) {
            addCell(new Cell(id, dataVIP, 8080, totalCapacity, usedCapacity));
	} else {
            cell.setDataVIP(dataVIP);
	    cell.setTotalCapacity(totalCapacity);
	    cell.setUsedCapacity(usedCapacity);
	}
    }
*/

    synchronized public void addCell(Cell cell) {
        cells.put(new Integer(cell.getId()), cell);
        cellIds[nbCells] = cell.getId();
        nbCells++;
    }

    /**
     *  Get a random cell id from the config.
     */
    synchronized public short getRandomCell() {

        if (cells.size() == 0)
            return -1;
        if (cells.size() == 1)
            return cellIds[0];

        return cellIds[random.nextInt(cells.size())];
    }

    private short getPowerOfTwo2Cells() {

        Cell tmp1 = (Cell) cells.get(new Integer(cellIds[0]));
        Cell tmp2 = (Cell) cells.get(new Integer(cellIds[1]));

        Cell cell1;
        Cell cell2;
              
        double diff = 0.0;
        if (USE_PCT_FREE) {
            if (tmp1.getPctFree() < tmp2.getPctFree()) {
                cell1 = tmp1;
                cell2 = tmp2;
            } else {
                cell1 = tmp2;
                cell2 = tmp1;
            }
            diff = cell2.getPctFree() - cell1.getPctFree();
        } else {
            if (tmp1.getAvailableCapacity() < tmp2.getAvailableCapacity()) {
                cell1 = tmp1;
                cell2 = tmp2;
            } else {
                cell1 = tmp2;
                cell2 = tmp1;
            }
            diff = cell2.getAvailableCapacity() - cell1.getAvailableCapacity();
            diff = (diff / (double) cell2.getAvailableCapacity()) * 100;
        }

        int rd = random.nextInt(100);

        Cell res = null;
        if (rd > (50 + (new Double(diff)).intValue())) {
            res = cell1;
        } else {
            res = cell2;
        }

//        if (USE_PCT_FREE) {
//         System.out.println("powerOfTwo cell " + cell1.getId() +
//           " %free = " + cell1.getPctFree() +
//           ", cell " + cell2.getId() +
//           " %free = " + cell2.getPctFree() +
//           ", random = " + rd +
//           ", barrier = " + (50 + (new Double(diff)).intValue()) +
//           " -> picked cell " + res.getId());
//        }

        return res.getId();

    }

    private short getPowerOfTwoNCells() {
        //
        // Generate two different random cells
        //
        int random1 = random.nextInt(cells.size());
        int random2 = random.nextInt(cells.size());
        while (random2 == random1) {
            random2 = random.nextInt(cells.size());
        }

        //System.out.println("Pick lowest capacity between cell " + random1 +
        //                   " and cell " + random2);

        Cell cell1 = (Cell) cells.get(new Integer(cellIds[random1]));
        Cell cell2 = (Cell) cells.get(new Integer(cellIds[random2]));

        //System.out.println("getPowerOfTwo : cell1 " + cell1 + ", cell2 " + cell2);

        if (USE_PCT_FREE) {
            if (cell1.getPctFree() > cell2.getPctFree()) {
                return cellIds[random1];
            } else if (cell2.getAvailableCapacity() > 0) {
                return cellIds[random2];
            }
        } else {
            if (cell1.getAvailableCapacity() > cell2.getAvailableCapacity()) {
                return cellIds[random1];
            } else if (cell2.getAvailableCapacity() > 0) {
                return cellIds[random2];
            }
        }

        // try to find a random cell with capactity
        for (int i=0; i<2*cells.size(); i++) {
            random2 = random.nextInt(cells.size());
            cell2 = (Cell) cells.get(new Integer(cellIds[random2]));
            if (cell2.getAvailableCapacity() > 0)
                return cellIds[random2];
        }
        // final sweep in case random was unlucky
        for (int i=0; i<cells.size(); i++) {
            cell2 = (Cell) cells.get(new Integer(cellIds[random2]));
            if (cell2.getAvailableCapacity() > 0)
                return cellIds[random2];
        }
        // silo full
        return -1;
    }

    synchronized public short checkCellId(Connection conn, int cellid)
        throws ArchiveException, IOException {

        if (cells.size() == 0) {
            conn.getMulticellConfiguration();
        }
        if (cells.size() == 0)  // no config loaded
            throw new ArchiveException("can't get multicell config");

        // handle client-specified cell
        Cell c = (Cell) cells.get(new Integer(cellid));
        if (c == null) {
            throw new ArchiveException("can't get cell id " + cellid + 
                                           " from " + cells);
        }
        return c.getId();
    }

    /**
     *  Get id of less empty of 2 randomly-selected cells.
     */
    synchronized public short getPowerOfTwoCell(Connection conn) 
        throws ArchiveException, IOException {

        if (cells.size() == 0) {
            conn.getMulticellConfiguration();
        }

        if (cells.size() == 0)  // no config loaded
            return -1;
        if (cells.size() == 1)
            return cellIds[0];


        if (cells.size() == 2) {
            return getPowerOfTwo2Cells();
        }

        return getPowerOfTwoNCells();
    }

    synchronized public int getNbCells() {
        return nbCells;
    }

    synchronized public short[] getCellList() {
        short list[] = new short[nbCells];
        for (int i=0; i<nbCells; i++)
            list[i] = cellIds[i];
        return list;
    }

    synchronized public short getCellIdByIndex(short index) 
        throws ArchiveException {

        if (index < 0  ||  index >= nbCells)
            throw new ArchiveException("index (" + index + ") bad");
        return cellIds[index];
    }

    /**
     *  Look up cell by id; if wild card (-1) get a random 
     *  cell from the config, or the default one if no config.
     */
    synchronized public Cell getCell(short id, Connection conn) 
        throws ArchiveException, IOException {

        if (id == -2)
            return defaultCell;

        if (cells.size() == 0) {
            conn.getMulticellConfiguration();
        }
        if (cells.size() == 0) {
            throw new ArchiveException("No multicell config");
        }
        //System.out.println(toString());

        if (id == -1)
            id = getRandomCell();
        if (id == -1)
            return defaultCell;

        Cell c = (Cell) cells.get(new Integer(id));
        if (c == null) {
            throw new ArchiveException("can't get cell id " + id + " from " +
                                       cells);
        }
        return c;
    }

    synchronized public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("version: ").append(getVersion()).append('\n');
        Iterator it = cells.values().iterator();
        while (it.hasNext()) {
            Cell cell = (Cell) it.next();
            sb.append("  ").append(cell.toString()).append('\n');
        }
        return sb.toString();
    }
}
