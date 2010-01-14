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



package com.sun.honeycomb.admin.mgmt.server;

import java.math.BigInteger;
import java.util.ArrayList;
import com.sun.honeycomb.common.ConfigPropertyNames;
import com.sun.honeycomb.config.ClusterProperties;
import java.util.List;
import java.util.Properties;
import java.io.IOException;
import java.io.FileInputStream;
import com.sun.honeycomb.cm.NodeMgr;
import com.sun.honeycomb.common.CliConstants;
import com.sun.honeycomb.util.ServiceProcessor;
import com.sun.honeycomb.multicell.SpaceRemaining;

/**
 * Reads a set of dummy properties out of a config file. 
 * The config file is called "admEmulator.config". 
 * If the property "AdmEmulatorPropertiesSetName" is defined
 * in cluster.properties, then "admEmulator.config.[EmulatorPropertiesSetName]
 * will be read.
 */
public class ValuesRepository {

    private static ValuesRepository _instance = null;
    private     HCCell _cell;
    private     HCVersions _versions;
    private int _numNodes = -1;
    protected List<HCSensor> _sensors = new ArrayList();
    protected List<HCNode> _nodesArray = new ArrayList();
    protected HCNodes  _nodes;
    protected HCDisks _disks;
    protected HCFrus _frus;
    protected List<HCDisk> _disksArray = new ArrayList();
    protected List<HCSwitch> _switches = new ArrayList();
    protected List<HCFru> _frusArray = new ArrayList();
    protected HCSP _sp = null;
    protected ClusterProperties  config;
    private Properties props;
    /**
     * The getter for this singleton class
     */
    public static synchronized ValuesRepository getInstance() {
        if (_instance == null) {
            _instance = new ValuesRepository();
        }

        return(_instance);
    }
    
    /**
     * set up and load property files
     */
    ValuesRepository() {

        String propSetName = 
            ClusterProperties.getInstance().getProperty("AdmEmulatorPropertiesSetName");
        
        if(null!=propSetName) {
            propSetName += "admEmulator.config."+propSetName;
        } else {
            propSetName = "admEmulator.config";
        }
        propSetName = "/config/" + propSetName;
        System.out.println("PropSetName: "+propSetName);

        props = new Properties();
        
        FileInputStream input = null;
        try {
            input = new FileInputStream(NodeMgr.getInstance().getEmulatorRoot()+propSetName);
            props.load(input);
            input.close();
        } catch (IOException e) {
            System.err.println("Unable to open properties file: " + propSetName);
            System.exit(1);
        }        
        setupCell();
        setupNodes();
        setupDisks();
        setupSwitches();
        setupSp();
        setupSensors();
        setupVersioning();
        setupFrus();
    }
    private void setupVersioning() {
        
        _versions = new HCVersions();
        _versions.setSpBios(props.getProperty("versions.cell.spBios"));
        _versions.setSpSmdc(props.getProperty("versions.cell.spSmdc"));
        _versions.setSwitchOneOverlay(
                props.getProperty("versions.cell.overlay.switch.1"));
        _versions.setSwitchTwoOverlay(
                props.getProperty("versions.cell.overlay.switch.2"));
        _versions.setVersion(props.getProperty("versions.cell.version"));
        
    }
    private void setupSensors() {

        for (int i = 0; i < _numNodes; i++) {
            HCSensor sensor= new HCSensor();
            sensor.nodeid = (i+101)+"";
            sensor.ddrVoltage = props.getProperty("sensor.ddrVoltage."+(i+101));
            sensor.threeVCC = props.getProperty("sensor.threeVCC."+(i+101));
            sensor.fiveVCC = props.getProperty("sensor.fiveVCC."+(i+101));
            sensor.twelveVCC = props.getProperty("sensor.twelveVCC."+(i+101));
            sensor.batteryVoltage = props.getProperty("sensor.batteryVoltage."+(i+101));
            sensor.cpuVoltage = props.getProperty("sensor.cpuVoltage."+(i+101));
            sensor.cpuTemperature = props.getProperty("sensor.cpuTemprature."+(i+101));
            sensor.cpuFanSpeed = props.getProperty("sensor.cpuFanSpeed."+(i+101));
            sensor.systemTemperature = props.getProperty("sensor.systemTemprature."+(i+101));
            sensor.systemFan1Speed = props.getProperty("sensor.systemFan1Speed."+(i+101));
            sensor.systemFan2Speed = props.getProperty("sensor.systemFan2Speed."+(i+101));
            sensor.systemFan3Speed = props.getProperty("sensor.systemFan3Speed."+(i+101));
            sensor.systemFan4Speed = props.getProperty("sensor.systemFan4Speed."+(i+101));
            sensor.systemFan5Speed = props.getProperty("sensor.systemFan5Speed."+(i+101));
            _sensors.add(sensor);
        }


        
    }
    private void setupCell() {
        /*
          protected List<String> fmwVersions;
          protected HCAlertAddr alertAddr;          
        */
        config = ClusterProperties.getInstance();
        _numNodes=config.getPropertyAsInt(ConfigPropertyNames.PROP_NUM_NODES);
        _cell = new HCCell();
        _cell.endTimeLastRecoverCycle=100001;
        _cell.numDisksPerNode=BigInteger.
            valueOf(Integer.parseInt(props.getProperty("cell.maxDisks")));

        _cell.noUnhealeadFailures=BigInteger.valueOf(0);
        _cell.noUnhealeadUniqueFailures=BigInteger.valueOf(0);
        _cell.possibleDataLoss=false;


        //        _cell.expansionStatus="unset";
        _cell.language="en";


        _cell.quorumReached=true;

        
        //
        // Verions - add emulation FIXME
        //

        
    }

    

    private void setupNodes() {
        _nodes = new HCNodes();
        _nodes.masterNode=BigInteger.valueOf(1);
        _nodes.protocolRunning=true;
        _nodes.numAliveDisks=BigInteger.valueOf(64);
        _nodes.numAliveNodes=BigInteger.valueOf(_numNodes);
        if (_nodes.nodeIds == null) {
            _nodes.nodeIds = new ArrayList<BigInteger>();
        }
        for (int i=0; i<_numNodes; i++) {
            _nodes.nodeIds.add(BigInteger.valueOf(i+101));
        }
        long totalCapacity =  
            SpaceRemaining.getInstance().getTotalCapacityBytes()/(1024*1024);
        long spaceRemaining =  
            SpaceRemaining.getInstance().getUsedCapacityBytes()/(1024*1024);
        long perDiskSpaceRemaining = spaceRemaining/(_numNodes*4);
        long perDiskTotalCapacity = totalCapacity/(_numNodes*4);
        for (int i = 0; i < _numNodes; i++) {
            HCNode node = new HCNode();
            node.nodeId=BigInteger.valueOf(i+101);
            node.hostname="hostname placeholder";
            node.fruName=""+(i+101);
            node.isAlive=Boolean.valueOf
                (props.getProperty("node.isAlive."+(i+101)));
            node.isEligible= Boolean.valueOf
                (props.getProperty("node.isEligible."+(i+101)));
            node.isMaster=  Boolean.valueOf
                (props.getProperty("node.isMaster."+(i+101)));           
            node.isViceMaster= Boolean.valueOf
                (props.getProperty("node.isViceMaster."+(i+101)));
            node.diskCount=BigInteger.valueOf(Integer.parseInt
                                              (props.getProperty
                                               ("node.diskCount."+(i+101))));
            
            node.status= BigInteger.valueOf(Integer.parseInt
                                            (props.getProperty
                                             ("node.status."+(i+101))));
            node.fruType=BigInteger.valueOf(CliConstants.HCFRU_TYPE_NODE);
            node.fruId=props.getProperty("node.serial."+(i+101));
            


            _frusArray.add(node);
            _nodesArray.add(node);


            for (int j = 0; j < 4; j++) {
                HCDisk disk = new HCDisk();
                String diskKeySuffix = new StringBuffer().append(101+i).append(".").append(j).toString();
                disk.setDiskId("DISK-" + (101+i) +":"+j);
                disk.status=BigInteger.valueOf
                    (Integer.parseInt(props.getProperty(
                        new StringBuffer("disk.status.")
                            .append(diskKeySuffix).toString())));
                disk.fruId=props.getProperty(
                        new StringBuffer("disk.serial.")
                            .append(diskKeySuffix).toString());
                disk.fruType=BigInteger.valueOf(CliConstants.HCFRU_TYPE_DISK);
                disk.fruName=disk.getDiskId();
                disk.totalCapacity= perDiskTotalCapacity;
                disk.usedCapacity= perDiskSpaceRemaining;

                //
                // If we want to test specific sizes, for some
                // reason, use this code block instead
                //
                /*
                disk.totalCapacity=
                    Long.parseLong(props.getProperty(
                        new StringBuffer("disk.totalCapacity.")
                            .append(diskKeySuffix).toString()));
                disk.usedCapacity=
                    Long.parseLong(props.getProperty(
                        new StringBuffer("disk.usedCapacity.")
                            .append(diskKeySuffix).toString()));
                */
                disk.nodeId=BigInteger.valueOf(i+101);
                String dPath = null;
                switch (i) {
                    case 0:
                        dPath = "c0t0d0s4";
                        break;
                    case 1:
                        dPath = "c0t1d0s4";
                        break;
                    case 2:
                        dPath = "c1t0d0s4";
                        break;
                    case 3:
                        dPath = "c1t1d0s4";
                        break;
                    default:    // Should never hit
                        dPath = "??";
                        break;
                }
                disk.path= new StringBuffer("/dev/rdsk/").append(dPath).toString();
                disk.device= new StringBuffer("/dev/dsk/").append(dPath).toString();
                _disksArray.add(disk);
                _frusArray.add(disk);
            }
        }

        _nodes.nodesList=_nodesArray;
    }


    private void setupDisks() {
        _disks = new HCDisks();
        _disks.totalCapacity=
            SpaceRemaining.getInstance().getTotalCapacityBytes()/(1024*1024);
        _disks.usedCapacity=
            SpaceRemaining.getInstance().getUsedCapacityBytes()/(1024*1024);


        _disks.disksList=_disksArray;
    }

    private void setupSwitches() {
        for (int i=1; i <= 2; i++) {
            HCSwitch fru = getSwitchFru(i);
            _frusArray.add(fru);
            _switches.add(fru);
        }
    }
    private void setupFrus() {
        _frus = new HCFrus();
        _frus.frusList=_frusArray;
    }    
    
    public HCSwitch getSwitchFru(int switchId) {
        /*
        if (switchId != 1 && switchId != 2) {
            throw new IllegalArgumentException("Switch id must be 1 or 2, passed '"
                    + switchId + "'.");
        }
         */
        HCSwitch fru = new HCSwitch();
        String key = new StringBuffer("switch.").append(switchId).toString();
        fru.setFruName(props.getProperty(
                new StringBuffer(key).append(".fruName").toString()));
        fru.setFruId(props.getProperty(
                new StringBuffer(key).append(".fruId").toString()));
        int status = 0;
        if (switchId ==1 )
            status = CliConstants.HCSWITCH_STATUS_ACTIVE;
        else
            status = CliConstants.HCSWITCH_STATUS_STANDBY;
        fru.setStatus(BigInteger.valueOf(status));
        fru.setVersion(props.getProperty(
                new StringBuffer("versions.cell.overlay.switch.").append(switchId).toString()));
        fru.setFruType(BigInteger.valueOf(CliConstants.HCFRU_TYPE_SWITCH));
        return fru;
    }

    public HCSP getSPFru() {
	HCSP fru = new HCSP();
        fru.setFruId(props.getProperty("sp.serial"));
        fru.setFruName("SN");
        fru.setFruType(BigInteger.valueOf(CliConstants.HCFRU_TYPE_SP));
        fru.setStatus(BigInteger.valueOf(Integer.parseInt(
		props.getProperty("sp.status"))));
        return fru;
    }
    
    
    private void setupSp() {
        _sp = getSPFru();
        _frusArray.add(_sp);
    }
    
    HCCell getCell() {
        return _cell;
    }
    
    HCVersions getVersions() {
        return _versions;
    }


    HCNodes getNodes() {
        return _nodes;
    }

    HCDisks getDisks() {
        return _disks;
    }


    HCFrus getFrus() {
        return _frus;
    }
    
    void getNodeBiosList(List list) {
        String[] biosNodes = new String[_numNodes];        
        String biosProp = null;         
        for (int idx = 0; idx < _numNodes; idx++) {
            HCNode n = _nodesArray.get(idx);
            biosProp = props.getProperty("versions.node.spBios." + (idx + 101));
            list.add(idx, biosProp);
        }
    }
    void getNodeSmdcList(List list) {
        String[] smdcNodes = new String[_numNodes];
        String smdcProp = null;
        for (int idx = 0; idx < _numNodes; idx++) {
            HCNode n = _nodesArray.get(idx);
            smdcProp = props.getProperty("versions.node.spSmdc." + (idx + 101));
            list.add(idx, smdcProp);
        }
    }
    
    List<HCSensor> getSensors() {
        return _sensors;
    }

    HCNode getNode(int nodeId) {
        return _nodesArray.get(nodeId-101);
    }

    HCSensor getSensor(int nodeId) {
        return _sensors.get(nodeId-101);
    }

    long getTaskCompletionTimeDelta() {
        return Long.parseLong(props.getProperty("Tasks.completion.delta"));
    }

}
