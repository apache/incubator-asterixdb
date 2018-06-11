/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.hyracks.control.common.controllers;

import static org.apache.hyracks.util.MXHelper.osMXBean;
import static org.apache.hyracks.util.MXHelper.runtimeMXBean;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import org.apache.hyracks.api.comm.NetworkAddress;
import org.apache.hyracks.api.job.resource.NodeCapacity;
import org.apache.hyracks.control.common.heartbeat.HeartbeatSchema;
import org.apache.hyracks.util.MXHelper;
import org.apache.hyracks.util.PidHelper;

public final class NodeRegistration implements Serializable {
    private static final long serialVersionUID = 1L;

    private final InetSocketAddress ncAddress;

    private final String nodeId;

    private final NCConfig ncConfig;

    private final NetworkAddress dataPort;

    private final NetworkAddress datasetPort;

    private final String osName;

    private final String arch;

    private final String osVersion;

    private final int nProcessors;

    private final String vmName;

    private final String vmVersion;

    private final String vmVendor;

    private final String classpath;

    private final String libraryPath;

    private final String bootClasspath;

    private final List<String> inputArguments;

    private final Map<String, String> systemProperties;

    private final HeartbeatSchema hbSchema;

    private final NetworkAddress messagingPort;

    private final int pid;

    private final NodeCapacity capacity;

    public NodeRegistration(InetSocketAddress ncAddress, String nodeId, NCConfig ncConfig, NetworkAddress dataPort,
            NetworkAddress datasetPort, HeartbeatSchema hbSchema, NetworkAddress messagingPort, NodeCapacity capacity) {
        this.ncAddress = ncAddress;
        this.nodeId = nodeId;
        this.ncConfig = ncConfig;
        this.dataPort = dataPort;
        this.datasetPort = datasetPort;
        this.hbSchema = hbSchema;
        this.messagingPort = messagingPort;
        this.capacity = capacity;
        this.osName = osMXBean.getName();
        this.arch = osMXBean.getArch();
        this.osVersion = osMXBean.getVersion();
        this.nProcessors = osMXBean.getAvailableProcessors();
        this.vmName = runtimeMXBean.getVmName();
        this.vmVersion = runtimeMXBean.getVmVersion();
        this.vmVendor = runtimeMXBean.getVmVendor();
        this.classpath = runtimeMXBean.getClassPath();
        this.libraryPath = runtimeMXBean.getLibraryPath();
        this.bootClasspath = MXHelper.getBootClassPath();
        this.inputArguments = runtimeMXBean.getInputArguments();
        this.systemProperties = runtimeMXBean.getSystemProperties();
        this.pid = PidHelper.getPid();
    }

    public InetSocketAddress getNodeControllerAddress() {
        return ncAddress;
    }

    public String getNodeId() {
        return nodeId;
    }

    public NodeCapacity getCapacity() {
        return capacity;
    }

    public NCConfig getNCConfig() {
        return ncConfig;
    }

    public NetworkAddress getDataPort() {
        return dataPort;
    }

    public NetworkAddress getDatasetPort() {
        return datasetPort;
    }

    public String getOSName() {
        return osName;
    }

    public String getArch() {
        return arch;
    }

    public String getOSVersion() {
        return osVersion;
    }

    public int getNProcessors() {
        return nProcessors;
    }

    public HeartbeatSchema getHeartbeatSchema() {
        return hbSchema;
    }

    public String getVmName() {
        return vmName;
    }

    public String getVmVersion() {
        return vmVersion;
    }

    public String getVmVendor() {
        return vmVendor;
    }

    public String getClasspath() {
        return classpath;
    }

    public String getLibraryPath() {
        return libraryPath;
    }

    public String getBootClasspath() {
        return bootClasspath;
    }

    public List<String> getInputArguments() {
        return inputArguments;
    }

    public Map<String, String> getSystemProperties() {
        return systemProperties;
    }

    public NetworkAddress getMessagingPort() {
        return messagingPort;
    }

    public int getPid() {
        return pid;
    }
}
