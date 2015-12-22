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
package org.apache.asterix.metadata.feeds;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.asterix.common.active.ActiveRuntime;
import org.apache.asterix.common.active.ActiveRuntimeId;
import org.apache.asterix.common.active.ActiveRuntimeInputHandler;
import org.apache.asterix.common.active.api.IActiveManager;
import org.apache.asterix.common.active.api.IActiveRuntime.ActiveRuntimeType;
import org.apache.asterix.common.active.api.IActiveRuntime.Mode;
import org.apache.asterix.common.api.IAsterixAppRuntimeContext;
import org.apache.asterix.common.feeds.DistributeFeedFrameWriter;
import org.apache.asterix.common.feeds.FeedConnectionId;
import org.apache.asterix.common.feeds.SubscribableFeedRuntimeId;
import org.apache.asterix.common.feeds.SubscribableRuntime;
import org.apache.asterix.common.feeds.api.ISubscribableRuntime;
import org.apache.hyracks.api.context.IHyracksTaskContext;
import org.apache.hyracks.api.dataflow.IActivity;
import org.apache.hyracks.api.dataflow.IOperatorDescriptor;
import org.apache.hyracks.api.dataflow.value.IRecordDescriptorProvider;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.dataflow.common.comm.io.FrameTupleAccessor;
import org.apache.hyracks.dataflow.std.base.AbstractUnaryInputUnaryOutputOperatorNodePushable;

/*
 * This IFrameWriter doesn't follow the contract
 */
public class FeedMetaComputeNodePushable extends AbstractUnaryInputUnaryOutputOperatorNodePushable {

    private static final Logger LOGGER = Logger.getLogger(FeedMetaComputeNodePushable.class.getName());

    /** Runtime node pushable corresponding to the core feed operator **/
    private AbstractUnaryInputUnaryOutputOperatorNodePushable coreOperator;

    /**
     * A policy enforcer that ensures dynamic decisions for a feed are taken
     * in accordance with the associated ingestion policy
     **/
    private FeedPolicyEnforcer policyEnforcer;

    /**
     * The Feed Runtime instance associated with the operator. Feed Runtime
     * captures the state of the operator while the feed is active.
     */
    private ActiveRuntime feedRuntime;

    /**
     * A unique identifier for the feed instance. A feed instance represents
     * the flow of data from a feed to a dataset.
     **/
    private FeedConnectionId connectionId;

    /**
     * Denotes the i'th operator instance in a setting where K operator
     * instances are scheduled to run in parallel
     **/
    private int partition;

    private int nPartitions;

    /** The (singleton) instance of IFeedManager **/
    private IActiveManager activeManager;

    private FrameTupleAccessor fta;

    private final IHyracksTaskContext ctx;

    private final ActiveRuntimeType runtimeType = ActiveRuntimeType.COMPUTE;

    private ActiveRuntimeInputHandler inputSideHandler;

    public FeedMetaComputeNodePushable(IHyracksTaskContext ctx, IRecordDescriptorProvider recordDescProvider,
            int partition, int nPartitions, IOperatorDescriptor coreOperator, FeedConnectionId feedConnectionId,
            Map<String, String> feedPolicyProperties, String operationId) throws HyracksDataException {
        this.ctx = ctx;
        this.coreOperator = (AbstractUnaryInputUnaryOutputOperatorNodePushable) ((IActivity) coreOperator)
                .createPushRuntime(ctx, recordDescProvider, partition, nPartitions);
        this.policyEnforcer = new FeedPolicyEnforcer(feedConnectionId, feedPolicyProperties);
        this.partition = partition;
        this.nPartitions = nPartitions;
        this.connectionId = feedConnectionId;
        this.activeManager = ((IAsterixAppRuntimeContext) ctx.getJobletContext().getApplicationContext()
                .getApplicationObject()).getActiveManager();
        IAsterixAppRuntimeContext runtimeCtx = (IAsterixAppRuntimeContext) ctx.getJobletContext()
                .getApplicationContext().getApplicationObject();
        this.activeManager = runtimeCtx.getActiveManager();
    }

    @Override
    public void open() throws HyracksDataException {
        ActiveRuntimeId runtimeId = new SubscribableFeedRuntimeId(connectionId.getActiveId(), runtimeType, partition);
        try {
            feedRuntime = activeManager.getConnectionManager().getActiveRuntime(connectionId, runtimeId);
            if (feedRuntime == null) {
                initializeNewFeedRuntime(runtimeId);
            } else {
                reviveOldFeedRuntime(runtimeId);
            }
            writer.open();
            coreOperator.open();
        } catch (Exception e) {
            e.printStackTrace();
            throw new HyracksDataException(e);
        }
    }

    private void initializeNewFeedRuntime(ActiveRuntimeId runtimeId) throws Exception {
        this.fta = new FrameTupleAccessor(recordDesc);
        this.inputSideHandler = new ActiveRuntimeInputHandler(ctx, connectionId, runtimeId, coreOperator,
                policyEnforcer.getFeedPolicyAccessor(), true, fta, recordDesc, activeManager, nPartitions);

        DistributeFeedFrameWriter distributeWriter = new DistributeFeedFrameWriter(ctx, connectionId.getActiveId(),
                writer, runtimeType, partition, new FrameTupleAccessor(recordDesc), activeManager);

        coreOperator.setOutputFrameWriter(0, distributeWriter, recordDesc);

        feedRuntime = new SubscribableRuntime(connectionId.getActiveId(), runtimeId, inputSideHandler, distributeWriter,
                recordDesc);
        activeManager.getFeedSubscriptionManager().registerFeedSubscribableRuntime((ISubscribableRuntime) feedRuntime);
        activeManager.getConnectionManager().registerActiveRuntime(connectionId, feedRuntime);

        distributeWriter.subscribeFeed(policyEnforcer.getFeedPolicyAccessor(), writer, connectionId);
    }

    private void reviveOldFeedRuntime(ActiveRuntimeId runtimeId) throws Exception {
        this.fta = new FrameTupleAccessor(recordDesc);
        this.inputSideHandler = feedRuntime.getInputHandler();
        this.inputSideHandler.setCoreOperator(coreOperator);

        DistributeFeedFrameWriter distributeWriter = new DistributeFeedFrameWriter(ctx, connectionId.getActiveId(),
                writer, runtimeType, partition, new FrameTupleAccessor(recordDesc), activeManager);

        coreOperator.setOutputFrameWriter(0, distributeWriter, recordDesc);
        distributeWriter.subscribeFeed(policyEnforcer.getFeedPolicyAccessor(), writer, connectionId);

        inputSideHandler.reset(nPartitions);
        feedRuntime.setMode(Mode.PROCESS);
    }

    @Override
    public void nextFrame(ByteBuffer buffer) throws HyracksDataException {
        try {
            inputSideHandler.nextFrame(buffer);
        } catch (Exception e) {
            e.printStackTrace();
            throw new HyracksDataException(e);
        }
    }

    @Override
    public void fail() throws HyracksDataException {
        if (LOGGER.isLoggable(Level.WARNING)) {
            LOGGER.warning("Core Op:" + coreOperator.getDisplayName() + " fail ");
        }
        feedRuntime.setMode(Mode.FAIL);
        coreOperator.fail();
    }

    @Override
    public void close() throws HyracksDataException {
        boolean stalled = inputSideHandler.getMode().equals(Mode.STALL);
        boolean end = inputSideHandler.getMode().equals(Mode.END);
        try {
            if (inputSideHandler != null) {
                if (!(stalled || end)) {
                    inputSideHandler.nextFrame(null); // signal end of data
                    while (!inputSideHandler.isFinished()) {
                        synchronized (coreOperator) {
                            coreOperator.wait();
                        }
                    }
                } else {
                    inputSideHandler.setFinished(true);
                }
            }
            coreOperator.close();
            System.out.println("CLOSED " + coreOperator + " STALLED ?" + stalled + " ENDED " + end);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (!stalled) {
                deregister();
                System.out.println("DEREGISTERING " + this.feedRuntime.getRuntimeId());
            } else {
                System.out.println("NOT DEREGISTERING " + this.feedRuntime.getRuntimeId());
            }
            if (inputSideHandler != null) {
                inputSideHandler.close();
            }
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("Ending Operator  " + this.feedRuntime.getRuntimeId());
            }
        }
    }

    private void deregister() {
        if (feedRuntime != null) {
            // deregister from subscription manager
            SubscribableFeedRuntimeId runtimeId = (SubscribableFeedRuntimeId) feedRuntime.getRuntimeId();
            activeManager.getFeedSubscriptionManager().deregisterFeedSubscribableRuntime(runtimeId);

            // deregister from connection manager
            activeManager.getConnectionManager().deRegisterActiveRuntime(connectionId, feedRuntime.getRuntimeId());

        }
    }

}