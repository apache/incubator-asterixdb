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
package org.apache.asterix.feeds;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.asterix.common.active.ActiveJobId;
import org.apache.asterix.common.active.ActiveJobInfo.JobState;
import org.apache.asterix.common.exceptions.AsterixException;
import org.apache.asterix.common.feeds.ActiveActivity;
import org.apache.asterix.common.feeds.FeedConnectionId;
import org.apache.asterix.common.feeds.NodeLoadReport;
import org.apache.asterix.common.feeds.api.ActiveRuntimeId;
import org.apache.asterix.common.feeds.api.IActiveLoadManager;
import org.apache.asterix.common.feeds.api.IActiveRuntime.ActiveRuntimeType;
import org.apache.asterix.common.feeds.api.IFeedTrackingManager;
import org.apache.asterix.common.feeds.message.FeedCongestionMessage;
import org.apache.asterix.common.feeds.message.FeedReportMessage;
import org.apache.asterix.common.feeds.message.ScaleInReportMessage;
import org.apache.asterix.common.feeds.message.ThrottlingEnabledFeedMessage;
import org.apache.asterix.file.FeedOperations;
import org.apache.asterix.metadata.feeds.ActiveUtil;
import org.apache.asterix.metadata.feeds.PrepareStallMessage;
import org.apache.asterix.metadata.feeds.TerminateDataFlowMessage;
import org.apache.asterix.om.util.AsterixAppContextInfo;
import org.apache.hyracks.algebricks.common.utils.Pair;
import org.apache.hyracks.api.client.IHyracksClientConnection;
import org.apache.hyracks.api.job.JobId;
import org.apache.hyracks.api.job.JobSpecification;

public class ActiveLoadManager implements IActiveLoadManager {

    private static final Logger LOGGER = Logger.getLogger(ActiveLoadManager.class.getName());

    private static final long MIN_MODIFICATION_INTERVAL = 180000; // 10 seconds
    private final TreeSet<NodeLoadReport> nodeReports;
    private final Map<ActiveJobId, ActiveActivity> activities;
    private final Map<String, Pair<Integer, Integer>> feedMetrics;

    private ActiveJobId lastModified;
    private long lastModifiedTimestamp;

    private static final int UNKNOWN = -1;

    public ActiveLoadManager() {
        this.nodeReports = new TreeSet<NodeLoadReport>();
        this.activities = new HashMap<ActiveJobId, ActiveActivity>();
        this.feedMetrics = new HashMap<String, Pair<Integer, Integer>>();
    }

    @Override
    public void submitNodeLoadReport(NodeLoadReport report) {
        nodeReports.remove(report);
        nodeReports.add(report);
    }

    @Override
    public void reportCongestion(FeedCongestionMessage message) throws AsterixException {
        ActiveRuntimeId runtimeId = message.getRuntimeId();
        JobState jobState = ActiveJobLifecycleListener.INSTANCE.getJobState(message.getConnectionId());
        if (jobState == null
                || (jobState.equals(JobState.UNDER_RECOVERY))
                || (message.getConnectionId().equals(lastModified) && System.currentTimeMillis()
                        - lastModifiedTimestamp < MIN_MODIFICATION_INTERVAL)) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("Ignoring congestion report from " + runtimeId);
            }
            return;
        } else {
            try {
                ActiveJobLifecycleListener.INSTANCE.setJobState(message.getConnectionId(), JobState.UNDER_RECOVERY);
                int inflowRate = message.getInflowRate();
                int outflowRate = message.getOutflowRate();
                List<String> currentComputeLocations = new ArrayList<String>();
                currentComputeLocations.addAll(ActiveJobLifecycleListener.INSTANCE.getComputeLocations(message
                        .getConnectionId().getActiveId()));
                int computeCardinality = currentComputeLocations.size();
                int requiredCardinality = (int) Math
                        .ceil((double) ((computeCardinality * inflowRate) / (double) outflowRate)) + 5;
                int additionalComputeNodes = requiredCardinality - computeCardinality;
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.warning("INCREASING COMPUTE CARDINALITY from " + computeCardinality + " by "
                            + additionalComputeNodes);
                }

                List<String> helperComputeNodes = getNodeForSubstitution(additionalComputeNodes);

                // Step 1) Alter the original feed job to adjust the cardinality
                JobSpecification jobSpec = ActiveJobLifecycleListener.INSTANCE.getCollectJobSpecification(message
                        .getConnectionId());
                helperComputeNodes.addAll(currentComputeLocations);
                List<String> newLocations = new ArrayList<String>();
                newLocations.addAll(currentComputeLocations);
                newLocations.addAll(helperComputeNodes);
                ActiveUtil.increaseCardinality(jobSpec, ActiveRuntimeType.COMPUTE, requiredCardinality, newLocations);

                // Step 2) send prepare to  stall message
                gracefullyTerminateDataFlow(message.getConnectionId(), Integer.MAX_VALUE);

                // Step 3) run the altered job specification 
                if (LOGGER.isLoggable(Level.INFO)) {
                    LOGGER.info("New Job after adjusting to the workload " + jobSpec);
                }

                Thread.sleep(10000);
                runJob(jobSpec, false);
                lastModified = message.getConnectionId();
                lastModifiedTimestamp = System.currentTimeMillis();

            } catch (Exception e) {
                e.printStackTrace();
                if (LOGGER.isLoggable(Level.SEVERE)) {
                    LOGGER.severe("Unable to form the required job for scaling in/out" + e.getMessage());
                }
                throw new AsterixException(e);
            }
        }
    }

    @Override
    public void submitScaleInPossibleReport(ScaleInReportMessage message) throws Exception {
        JobState jobState = ActiveJobLifecycleListener.INSTANCE.getJobState(message.getConnectionId());
        if (jobState == null || (jobState.equals(JobState.UNDER_RECOVERY))) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.warning("JobState information for job " + "[" + message.getConnectionId() + "]" + " not found ");
            }
            return;
        } else {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("Processing scale-in message " + message);
            }
            ActiveJobLifecycleListener.INSTANCE.setJobState(message.getConnectionId(), JobState.UNDER_RECOVERY);
            JobSpecification jobSpec = ActiveJobLifecycleListener.INSTANCE.getCollectJobSpecification(message
                    .getConnectionId());
            int reducedCardinality = message.getReducedCardinaliy();
            List<String> currentComputeLocations = new ArrayList<String>();
            currentComputeLocations.addAll(ActiveJobLifecycleListener.INSTANCE.getComputeLocations(message
                    .getConnectionId().getActiveId()));
            ActiveUtil.decreaseComputeCardinality(jobSpec, ActiveRuntimeType.COMPUTE, reducedCardinality,
                    currentComputeLocations);

            gracefullyTerminateDataFlow((FeedConnectionId) message.getConnectionId(), reducedCardinality - 1);
            Thread.sleep(3000);
            JobId newJobId = runJob(jobSpec, false);
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("Launch modified job" + "[" + newJobId + "]" + "for scale-in \n" + jobSpec);
            }

        }
    }

    private void gracefullyTerminateDataFlow(FeedConnectionId connectionId, int computePartitionRetainLimit)
            throws Exception {
        // Step 1) send prepare to  stall message
        PrepareStallMessage stallMessage = new PrepareStallMessage(connectionId, computePartitionRetainLimit);
        List<String> intakeLocations = ActiveJobLifecycleListener.INSTANCE.getCollectLocations(connectionId);
        List<String> computeLocations = ActiveJobLifecycleListener.INSTANCE.getComputeLocations(connectionId
                .getActiveId());
        List<String> storageLocations = ActiveJobLifecycleListener.INSTANCE.getStoreLocations(connectionId);

        Set<String> operatorLocations = new HashSet<String>();

        operatorLocations.addAll(intakeLocations);
        operatorLocations.addAll(computeLocations);
        operatorLocations.addAll(storageLocations);

        JobSpecification messageJobSpec = FeedOperations.buildPrepareStallMessageJob(stallMessage, operatorLocations);
        runJob(messageJobSpec, true);

        // Step 2)
        TerminateDataFlowMessage terminateMesg = new TerminateDataFlowMessage(connectionId);
        messageJobSpec = FeedOperations.buildTerminateFlowMessageJob(terminateMesg, intakeLocations);
        runJob(messageJobSpec, true);
    }

    public static JobId runJob(JobSpecification spec, boolean waitForCompletion) throws Exception {
        IHyracksClientConnection hcc = AsterixAppContextInfo.getInstance().getHcc();
        JobId jobId = hcc.startJob(spec);
        if (waitForCompletion) {
            hcc.waitForCompletion(jobId);
        }
        return jobId;
    }

    @Override
    public void submitFeedRuntimeReport(FeedReportMessage report) {
        String key = "" + report.getConnectionId() + ":" + report.getRuntimeId().getRuntimeType();
        Pair<Integer, Integer> value = feedMetrics.get(key);
        if (value == null) {
            value = new Pair<Integer, Integer>(report.getValue(), 1);
            feedMetrics.put(key, value);
        } else {
            value.first = value.first + report.getValue();
            value.second = value.second + 1;
        }
    }

    @Override
    public int getOutflowRate(ActiveJobId connectionId, ActiveRuntimeType runtimeType) {
        int rVal;
        String key = "" + connectionId + ":" + runtimeType;
        feedMetrics.get(key);
        Pair<Integer, Integer> value = feedMetrics.get(key);
        if (value == null) {
            rVal = UNKNOWN;
        } else {
            rVal = value.first / value.second;
        }
        return rVal;
    }

    private List<String> getNodeForSubstitution(int nRequired) {
        List<String> nodeIds = new ArrayList<String>();
        Iterator<NodeLoadReport> it = null;
        int nAdded = 0;
        while (nAdded < nRequired) {
            it = nodeReports.iterator();
            while (it.hasNext()) {
                nodeIds.add(it.next().getNodeId());
                nAdded++;
            }
        }
        return nodeIds;
    }

    @Override
    public synchronized List<String> getNodes(int required) {
        Iterator<NodeLoadReport> it;
        List<String> allocated = new ArrayList<String>();
        while (allocated.size() < required) {
            it = nodeReports.iterator();
            while (it.hasNext() && allocated.size() < required) {
                allocated.add(it.next().getNodeId());
            }
        }
        return allocated;
    }

    @Override
    public void reportThrottlingEnabled(ThrottlingEnabledFeedMessage mesg) throws AsterixException, Exception {
        System.out.println("Throttling Enabled for " + mesg.getConnectionId() + " " + mesg.getFeedRuntimeId());
        ActiveJobId connectionId = mesg.getConnectionId();
        List<String> destinationLocations = new ArrayList<String>();
        List<String> storageLocations = ActiveJobLifecycleListener.INSTANCE.getStoreLocations(connectionId);
        List<String> collectLocations = ActiveJobLifecycleListener.INSTANCE.getCollectLocations(connectionId);

        destinationLocations.addAll(storageLocations);
        destinationLocations.addAll(collectLocations);
        JobSpecification messageJobSpec = FeedOperations.buildNotifyThrottlingEnabledMessageJob(mesg,
                destinationLocations);
        runJob(messageJobSpec, true);
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.warning("Acking disabled for " + mesg.getConnectionId() + " in view of activated throttling");
        }
        IFeedTrackingManager trackingManager = CentralActiveManager.getInstance().getFeedTrackingManager();
        trackingManager.disableAcking(connectionId);
    }

    @Override
    public void reportActivity(ActiveJobId activeJobId, ActiveActivity activity) {
        activities.put(activeJobId, activity);
    }

    @Override
    public ActiveActivity getActivity(ActiveJobId activeJobId) {
        return activities.get(activeJobId);
    }

    @Override
    public Collection<ActiveActivity> getActivities() {
        return activities.values();
    }

    @Override
    public void removeActivity(ActiveJobId activeJobId) {
        activities.remove(activeJobId);
    }
}
