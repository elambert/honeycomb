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

 

package com.sun.honeycomb.admingui.present.panels;

import com.sun.nws.mozart.ui.BaseTableModel;
import com.sun.nws.mozart.ui.BaseTableModel.TableColumn;
import com.sun.nws.mozart.ui.utility.GuiResources;

/**
 *
 * @author ronaldso
 */
public class MonitorPerfStatsTableModel extends BaseTableModel {

    /** 
     * @param tableType - defines which table is being configured
     */
    public MonitorPerfStatsTableModel(int tableType) {
        super();
        
        switch (tableType) {
            case PnlMonitorPerfStats.TABLE_TYPE_MAIN:
                setColumns(new TableColumn[] {
                    new TableColumn(0, false,GuiResources.getGuiString(
                        "monitor.perfstat.table.heading.operations")),
                    new TableColumn(1, false, GuiResources.getGuiString(
                        "monitor.perfstat.table.heading.kbs.last")),
                    new TableColumn(2, false, GuiResources.getGuiString(
                        "monitor.perfstat.table.heading.kbs.change")),
                    new TableColumn(3, false, GuiResources.getGuiString(
                        "monitor.perfstat.table.heading.ops.last")),
                    new TableColumn(4, false, GuiResources.getGuiString(
                        "monitor.perfstat.table.heading.ops.change")),
                    new TableColumn(5, false, GuiResources.getGuiString(
                        "monitor.perfstat.table.heading.opss.last")),
                    new TableColumn(6, false, GuiResources.getGuiString(
                        "monitor.perfstat.table.heading.opss.change")),
                });
                break;

            case PnlMonitorPerfStats.TABLE_TYPE_CPU:
                setColumns(new TableColumn[] {
                    new TableColumn(0, false, ""),
                    new TableColumn(1, false, GuiResources.getGuiString(
                        "monitor.perfstat.table.heading.load1min.last")),
                    new TableColumn(2, false,GuiResources.getGuiString(
                        "monitor.perfstat.table.heading.load1min.change")),
                    new TableColumn(3, false, GuiResources.getGuiString(
                        "monitor.perfstat.table.heading.load5min.last")),
                    new TableColumn(4, false,GuiResources.getGuiString(
                        "monitor.perfstat.table.heading.load5min.change")),
                    new TableColumn(5, false, GuiResources.getGuiString(
                        "monitor.perfstat.table.heading.load15min.last")),
                    new TableColumn(6, false,GuiResources.getGuiString(
                        "monitor.perfstat.table.heading.load15min.change")),
                });
                break;

        }
    }

    /**
     * Populates the table with the summary of entries contained in the
     * directory represented by the passed in node from the tree.  If the
     * node is a file, the table will be empty.
     */
    public void populate(Object modelData) {

    }
    
}
