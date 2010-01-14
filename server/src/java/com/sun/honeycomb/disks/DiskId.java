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



package com.sun.honeycomb.disks;

import java.io.*;
import java.util.StringTokenizer;
import com.sun.honeycomb.coding.*;
import com.sun.honeycomb.cm.node_mgr.NodeMgr;
import com.sun.honeycomb.diskmonitor.DiskProxy;


public class DiskId implements Serializable, Comparable, Codable {

  private int cell;              // 0, 1, 2, ...
  private int silo;              // 0, 1, 2, ...
  private int node;              // 101, 102, 103, ...
  private int index;             // 0, 1, 2, ...
  private long inc;              // 0, 1, 2, ...

  private DiskLabel label;

  /** separates nodeId and diskIndex when parsing/printing disk */
  public static final String DELIM = ":";

  /** 
   * FIXME - a DiskId contains a cell and a silo id.
   * the silo id is not used anymore. The cell id should be fetch with
   * ClusterProperties.getInstance().getProperties("honeycomb.silo.cellid");
   * Not clear why a disk has a notion of a cell.
   */
  public static final int CELL_ID = 0;
  public static final short SILO_ID = 0;
  
  
  /** create a DiskId, leave the label as null */
  public DiskId(int cellId, int siloId, int nodeId, int diskIndex,
                long incarnation) {
    this(cellId, siloId, nodeId, diskIndex, incarnation, null);
  }

  /**
   * Create a DiskId based on the specified location information, and
   * the non-location specific information in the label.
   */
  public DiskId(int cellId, int siloId, int nodeId, int diskIndex,
		  DiskLabel label) {
    this(cellId, siloId, nodeId, diskIndex, label.incarnation(), label);
  }

  /** create a DiskId based on the given label */
  public DiskId(DiskLabel label) {
    this(label.cellId(), label.siloId(), label.nodeId(), label.diskIndex(),
         label.incarnation(), label);
  }

  /** used when we only know the nodeId and diskId */
  public DiskId(int nodeId, int diskIndex) {
    this(CELL_ID, SILO_ID, nodeId, diskIndex, DiskLabel.getSysIncarnation());
  }

  /** example input string: "104:2" meaning nodeId=104, diskIndex=2 */
  public DiskId(String s) {
    this(0, 0);
    StringTokenizer st = new StringTokenizer(s, DELIM);
    if (st.countTokens() != 2) {
      throw new IllegalArgumentException(
                                         "input string must be of the form nodeId,diskIndex " +
                                         "(parsed: " + s + ")");
    }
    node = Integer.parseInt(st.nextToken());
    index = Integer.parseInt(st.nextToken());
  }

  /** This constructor fully specifies all DiskId fields. */
  public DiskId(int cellId, int siloId, int nodeId, int diskIndex,
                long incarnation, DiskLabel label) {
    this.cell = cellId;
    this.silo = siloId;
    this.node = nodeId;
    this.index = diskIndex;
    this.inc = incarnation;
    this.label = label;
  }

  public void setLabel(DiskLabel label) {
    this.label = label;
  }

  public int cellId() {
    return cell;
  }

  public int siloId() {
    return silo;
  }

  public int nodeId() {
    return node;
  }

  public int diskIndex() {
    return index;
  }

  public long incarnation() {
    return inc;
  }

  public DiskLabel label() {
    return label;
  }

  public long incrIncarnation() {
    return ++inc;
  }

  public String toString() {
    StringBuffer buf = new StringBuffer();

    buf.append("[");
    buf.append(cellId());
    buf.append(",");
    buf.append(siloId());
    buf.append(",");
    buf.append(nodeId());
    buf.append(",");
    buf.append(diskIndex());
    buf.append(";");
    buf.append(incarnation());
    buf.append("]");

    return buf.toString();
  }

  /** example disk strings: "102:1" and "103:3" */
  public String toStringShort() {
    if (diskIndex() < 0)
      return nodeId() + DELIM + "?";
    else
      return nodeId() + DELIM + diskIndex();
  }

  /** Do not use incarnation or label for equality comparison. */
  public boolean equals(Object obj) {
    if (!(obj instanceof DiskId))
      return false;

    DiskId other = (DiskId) obj;
    boolean equal = (cellId() == other.cellId() &&
                     siloId() == other.siloId() &&
                     nodeId() == other.nodeId() &&
                     diskIndex() == other.diskIndex());
    return equal;
  }

  public int compareTo(final Object obj) {
    if (!(obj instanceof DiskId)) {
      throw new ClassCastException("cannot compare");
    }
    DiskId other = (DiskId) obj;

    if (cellId() < other.cellId())
      return -1;
    if (cellId() > other.cellId())
      return 1;

    if (siloId() < other.siloId())
      return -1;
    if (siloId() > other.siloId())
      return 1;

    if (nodeId() < other.nodeId())
      return -1;
    if (nodeId() > other.nodeId())
      return 1;

    if (diskIndex() < other.diskIndex())
      return -1;
    if (diskIndex() > other.diskIndex())
      return 1;

    // no incarnation in equality comparisons
       
    return 0;
  }

  private int hc(int i) { return (new Integer(i)).hashCode(); }
  private int hc(long l) { return (new Long(l)).hashCode(); }
  public int hashCode() {
    return hc(cellId()) ^ hc(siloId()) ^ hc(nodeId()) ^
      hc(diskIndex());  // no incarnation in equality comparisons
  }

  /* Implementation of Codable inteface */

  public void encode(Encoder encoder) {
    encoder.encodeInt(cellId());
    encoder.encodeInt(siloId());
    encoder.encodeInt(nodeId());
    encoder.encodeInt(diskIndex());
    encoder.encodeLong(incarnation());
  }

  public void decode(Decoder decoder) {
    cell = decoder.decodeInt();
    silo = decoder.decodeInt();
    node = decoder.decodeInt();
    index = decoder.decodeInt();
    inc = decoder.decodeLong();
  }

  public static void serialize(DiskId diskId, DataOutput output) 
    throws IOException {
    output.writeInt(diskId.cellId());
    output.writeInt(diskId.siloId());
    output.writeInt(diskId.nodeId());
    output.writeInt(diskId.diskIndex());
    output.writeLong(diskId.incarnation());

    DiskLabel.serialize(diskId.label(), output);
  }

  public static DiskId deserialize(DataInput input) throws IOException {
    int cellId = input.readInt();
    int siloId = input.readInt();
    int nodeId = input.readInt();
    int diskIndex = input.readInt();
    long incarnation = input.readLong();

    return new DiskId(cellId, siloId, nodeId, diskIndex, incarnation,
                      DiskLabel.deserialize(input));
  }
}
