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
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.common.CapacityLimitException;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.logging.Logger;
import com.sun.honeycomb.config.ClusterProperties;
import java.lang.reflect.InvocationTargetException;

/** Entry point into the Layout subsystem. */
public class LayoutClient {

    private static final String defaultGenerator = "ArnoudMapGenerator";

    /** Return the singleton. */
    synchronized public static LayoutClient getInstance() {

        if (instance == null) {
            instance = new LayoutClient();
        }
        return instance;
    }

    /** Get startup values from layout config. */
    protected LayoutClient() {

        NODES = LayoutConfig.NODES_PER_CELL;
        DISKS = LayoutConfig.DISKS_PER_NODE;
        FRAGS = LayoutConfig.FRAGS_PER_OBJ;

        String generatorClassName = ClusterProperties.getInstance().getProperty("honeycomb.layout.generator", defaultGenerator);
        if (!generatorClassName.equals(defaultGenerator)) {
            LOG.warning("***** Warning: layout is not using the default map generator but ["+
                        generatorClassName+"] *****");
        }

        mapGen = null;
        try {
            mapGen = (MapGenInterface)Class.forName("com.sun.honeycomb.layout."+generatorClassName)
                .getConstructor().newInstance();
        } catch (InstantiationException e) {
            RuntimeException newe = new RuntimeException("Failed to instanciate the map generator ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        } catch (IllegalAccessException e) {
            RuntimeException newe = new RuntimeException("Failed to instanciate the map generator ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        } catch (IllegalArgumentException e) {
            RuntimeException newe = new RuntimeException("Failed to instanciate the map generator ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        } catch (InvocationTargetException e) {
            RuntimeException newe = new RuntimeException("Failed to instanciate the map generator ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        } catch (ClassNotFoundException e) {
            RuntimeException newe = new RuntimeException("Failed to instanciate the map generator ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        } catch (NoSuchMethodException e) {
            RuntimeException newe = new RuntimeException("Failed to instanciate the map generator ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }
    }

    /** Get layout for STORE operation */
    public Layout getLayoutForStore(int mapId)
        throws CapacityLimitException {

        // if blockStores is set, throw exception to prevent store
        if (LayoutProxy.getBlockStores()) {
            String msg = "capacity limit reached, cannot store object";
            LOG.warning(msg);
            throw new CapacityLimitException(msg);
        }

        return getLayout(mapId, LayoutProxy.getCurrentDiskMask());
    }

    /** Get current layout for a mapId */
    public Layout getCurrentLayout(int mapId) {

        return getLayout(mapId, LayoutProxy.getCurrentDiskMask());
    }

    /** Get layout for RETRIEVE operation */
    public Layout getLayoutForRetrieve(int mapId) {

        return getLayout(mapId, LayoutProxy.getCurrentDiskMask());
    }

    /** Used when recovering a single fragment. */
    public Layout getLayoutForRecover(int mapId) {

        return getLayoutForRecover(mapId, LayoutProxy.getCurrentDiskMask());
    }

    /** Used when recovering a single fragment. */
    public Layout getLayoutForRecover(int mapId, DiskMask mask) {

        return getLayout(mapId, mask);
    }

    /** Get layout for SLOSHING, treat sloshing nodes as online. */
    public Layout getLayoutForSloshing(int mapId) {

        return getLayout(mapId, LayoutProxy.getPreSloshDiskMask());
    }

    /**
     * Returns the fragment id (zero-based) that maps to the given disk
     * for the given mapId.  If this mapId is not using this disk for
     * the given disk mask, returns -1.  Note that we never have two
     * fragments for the same mapId on the same disk.
     */
    public int getFragmentId(int mapId, DiskId d, DiskMask mask) {

        Layout layout = getLayoutForRecover(mapId, mask);
        if (!layout.contains(d)) {
            return -1;
        }

        return layout.indexOf(d);
    }

    /**
     * For the given mapId, returns the DiskId on which the specified
     * fragment is placed.  Used when recovering a single fragment.
     */
    public DiskId diskIdForFrag(int mapId, int fragmentId,
                                   DiskMask mask) {

        Layout layout = getLayoutForRecover(mapId, mask);
        if (fragmentId < 0 || fragmentId > layout.size() - 1) {
            throw new IllegalArgumentException("fragmentId "+
            fragmentId+" invalid, must be in range 0 - "+layout.size());
        }

        return (DiskId) layout.get(fragmentId);
    }

    /** Return a randomly chosen layout map id. */
    public static int getLayoutMapId() {
        return randomMapId();
    }

    /* If we take available capacity into account when selecting
     * layouts, using consecutive mapids for extents of the same object
     * might not be the best way. TBD in Capacity Planning design.
     */
    public static int getConsecutiveLayoutMapId(int mapId) {
        return (mapId + 1) % NUM_MAP_IDS;
    }
    public static int getPreviousLayoutMapId(int mapId) {
        int result = mapId-1;
        if(result < 0) {
            result = NUM_MAP_IDS-1;
        }
        return result;

    }

    /** Print the nodeId,diskId pairs for all disks in given layout. */
    public static String layoutToString(Disk[] layout) {

        DiskIdList diskIdList = new DiskIdList(layout);
        return diskIdList.toString();
    }

    /** Return true if given id is in valid range. */
    public static boolean isMapIdValid(int layoutMapId) {

        if (layoutMapId < 0 || layoutMapId >= NUM_MAP_IDS) {
            return false;
        } else {
            return true;
        }
    }

    /** Compare each disk in the layout. */
    public static boolean sameLayouts(Disk[] layout1, Disk[] layout2) {

        if (layout1 == null || layout2 == null) {
            return false;
        }
        if (layout1.length != layout2.length) {
            return false;
        }
        for (int i=0; i < layout1.length; i++) {
            if (layout1[i] != layout2[i]) {
                return false;
            }
        }
        return true;
    }

    /*
     * methods for unit testing and simulation
     */

    /** test method to get layout for a specific disk mask and mapId */
    public Layout utGetLayout(int mapId, DiskMask mask) {
        return getLayout(mapId, mask);
    }

    /** method to generate a random mapId */
    static int utRandomMapId() {
        return randomMapId();
    }

    /*
     * private methods
     */

    /** For now, mapIds are randomly selected. */
    private static int randomMapId() {
        return (int)(Math.random() * NUM_MAP_IDS);
    }

    /** Find the layout for the given mapid and mask. */
    protected Layout getLayout(int mapId, DiskMask mask) {

        if (mapId < 0 || mapId >= NUM_MAP_IDS) {
            throw new IllegalArgumentException(
                    "mapId "+mapId+" out of range 0-"+NUM_MAP_IDS);
        }
        if (mask ==  null) {
            throw new IllegalArgumentException(
                    "disk mask is null");
        }

        Layout layout = computeLayout(mapId, mask);
        return layout;
    }


    /**********************************************************************
     * Return the disk that is below the given diskId in the same column.
     *
     * @param mapId  Map number to generate the layout.
     * @param mask   Disk mask needed for generating the layout.
     * @param diskId diskId of the disk in the given column.
     * @param fragId fragId, to indicate the column in layout.
     *
     * @return  Disk that is below diskId in the same column.
     **/
    public Disk getNextDiskInColumn(int mapId, DiskMask mask, DiskId diskId,
                                    int fragId) {
        DiskMask temp = (DiskMask) mask.clone();
        temp.setOffline(diskId);
        Layout layout = computeLayout(mapId, temp);
        return layout.getDisk(fragId);
    }

    /**
     * Walk down the layout map column until finding an online disk,
     * then add it to the layout. Each column represents a fragment.
     */
    private Layout computeLayout(int mapId, DiskMask mask) {

		int missingCol = 0;

		Layout layout = new Layout(mapId);

		/*
		 * Frags per node allowed always starts at 1 for 16 node cluster but in
		 * the 8 node layout we decide to relax this constraint right from the
		 * start since it would give us a much better balance for the 8 node
		 * setup.
		 */
		int fragsPerNode = 1;
		if (mask.is8Node()) {
			fragsPerNode = 2;
		}

		for (int col = 0; col < FRAGS; col++) {

			boolean found = false;

			for (int k = fragsPerNode; (k < FRAGS) && (!found); k++) {
				for (int row = 0; (row < MAP_ROWS) && (!found); row++) {

					// below line is trying to get node mgr proxy???
					int[] entry = mapGen.getMapEntry(mapId, row, col);
					int nodeId = LayoutConfig.BASE_NODE_ID + entry[ENTRY_NODE];
					int diskIndex = entry[ENTRY_DISK];
					DiskId d = new DiskId(nodeId, diskIndex);
					if (mask.isOnline(d)) {
						if (layout.containsNodeCount(d.nodeId()) < k
								&& !layout.contains(d)) {
							layout.add(col, d);
							found = true;
						}
					}
				}
			}

			if (!found) {
				layout.add(col, null);
				missingCol++;
			}
		}

		// If we did not find an online disk in a particular column, it
		// probably means more than MAP_ROWS disks have failed. This
		// should never happen, as we should have lost quorum and gone
		// into maintenance mode, but check just in case and log.
		if (missingCol > 0) {
			LOG.warning("layout for mapId " + mapId + " is missing "
					+ missingCol + " disks due to disk failures");
		}

		return layout;
	}
    /*
     * Methods to print layout maps, useful in unit testing (ut).
     */

    /** Print full map for the given mapId. */
    public String mapToString(int mapId) {
        return mapToString(mapId, null);
    }

    /**
     * Print the map generated with the given mapId.  If a layout is
     * specified, annotated map to show disks used in the layout.
     */
    public String mapToString(int mapId, Layout layout) {

        int[] entry;
        ArrayList filledRows = new ArrayList();
        int maxRow = 0;
        int found = 0;
        for (int row=0; row < MAP_ROWS; row++) {

            String rowStr = "";
            for (int col=0; col < FRAGS; col++) {

                // get the map entry we need
                entry = mapGen.getMapEntry(mapId, row, col);

                // convert to real nodeIds if mapId is given
                int nodeId = LayoutConfig.BASE_NODE_ID +
                             entry[ENTRY_NODE];
                DiskId diskId = new DiskId(nodeId, entry[ENTRY_DISK]);

                // print it
                if (SHOW_DISK_ID) {
                    rowStr += diskId.toStringShort();
                } else {
                    if (diskId.nodeId() < 10) {
                        rowStr += " ";
                    }
                    rowStr += diskId.nodeId();
                }

                // if this disk is in the layout, mark it
                if (layout != null && (diskId.equals(
                                      (DiskId)layout.get(col)))) {
                    rowStr += "*  ";
                    maxRow = Math.max(maxRow, row);
                    found++;
                } else {
                    rowStr += "   ";
                }

            }
            rowStr += "\n";
            filledRows.add(rowStr);

            // when showing only nodeId, only print N rows
            if (!SHOW_DISK_ID && row+1 == NODES ) {
                rowStr += diskOrder(mapId);
                break;
            }

            // when annotation, show only as many rows as we need
            if (layout != null && found == FRAGS &&
                    row >= Math.max(maxRow, MIN_ROWS_TO_ANNOTATE-1)) {
                break;
            }
        }

        String s = "";
        for (int i=0; i < filledRows.size(); i++) {
            s += (String)filledRows.get(i);
        }

        return s;
    }

    /**
     * Display the shuffled list of disks, used as "shorthand" when
     * displaying layout maps during unit testing.
     */
    private String diskOrder(int mapId) {

        String s = "";
        s += "Disk Order: ";
        for (int i=0; i < DISKS; i++) {
            int col = 0;
            int row = i * NODES;

        int[] entry = mapGen.getMapEntry(mapId, row, col);
            s += entry[ENTRY_DISK];
            s += " ";
        }
        s += "\n";
        return s;
    }

    public static final int NUM_MAP_IDS = 10000;

    // this is how many rows we need in the layout map, since if more
    // failures occur we loose quorum and cannot store/retrieve
    protected static final int MAP_ROWS = 16;

    // index into the map entry array
    static final int ENTRY_NODE = 0;
    static final int ENTRY_DISK = 1;

    private static LayoutClient instance = null;

    protected int DISKS;
    protected int NODES;
    protected int FRAGS;
    protected MapGenInterface mapGen;

    // If false then mapToString shows only first NODES rows of map,
    // useful for manual testing but testing scripts assume it's TRUE.
    private static final boolean SHOW_DISK_ID = true;

    // Minimum number of rows to print in mapToString(mapId, layout)
    // when we're marking disks used in the given layout.
    private static final int MIN_ROWS_TO_ANNOTATE = 4;

    private transient static final Logger LOG =
        Logger.getLogger(LayoutClient.class.getName());

}

