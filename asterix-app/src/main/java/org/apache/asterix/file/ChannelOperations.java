/*
 * Copyright 2009-2013 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.asterix.file;

import org.apache.asterix.common.channels.ChannelId;
import org.apache.asterix.common.channels.ChannelJobInfo;
import org.apache.asterix.common.exceptions.AsterixException;
import org.apache.asterix.common.feeds.FeedConnectionId;
import org.apache.asterix.common.feeds.ActiveId;
import org.apache.asterix.common.feeds.message.DropChannelMessage;
import org.apache.asterix.common.functions.FunctionSignature;
import org.apache.asterix.feeds.ActiveJobLifecycleListener;
import org.apache.asterix.metadata.declared.AqlMetadataProvider;
import org.apache.asterix.metadata.feeds.FeedMessageOperatorDescriptor;
import org.apache.hyracks.algebricks.common.constraints.AlgebricksAbsolutePartitionConstraint;
import org.apache.hyracks.algebricks.common.constraints.AlgebricksPartitionConstraint;
import org.apache.hyracks.algebricks.common.constraints.AlgebricksPartitionConstraintHelper;
import org.apache.hyracks.algebricks.common.exceptions.AlgebricksException;
import org.apache.hyracks.algebricks.common.utils.Pair;
import org.apache.hyracks.api.dataflow.IOperatorDescriptor;
import org.apache.hyracks.api.job.JobSpecification;
import org.apache.hyracks.dataflow.std.connectors.OneToOneConnectorDescriptor;
import org.apache.hyracks.dataflow.std.misc.NullSinkOperatorDescriptor;

/**
 * Provides helper method(s) for creating JobSpec for operations on a channel.
 */
public class ChannelOperations {

    /**
     * Builds the job spec for repetitive channel
     * 
     * @param dataverseName
     * @param channelName
     * @param metadataProvider
     * @return JobSpecification the Hyracks job specification for receiving data from external source
     * @throws Exception
     */
    public static JobSpecification buildChannelJobSpec(String dataverseName, String channelName, String duration,
            FunctionSignature function, String subName, String resultsName, AqlMetadataProvider metadataProvider)
            throws AsterixException, AlgebricksException {
        JobSpecification spec = JobSpecificationUtils.createJobSpecification();
        IOperatorDescriptor channelQueryExecuter;
        AlgebricksPartitionConstraint executerPc;

        try {
            Pair<IOperatorDescriptor, AlgebricksPartitionConstraint> p = metadataProvider.buildChannelRuntime(spec,
                    dataverseName, channelName, duration, function, subName, resultsName);
            channelQueryExecuter = p.first;
            executerPc = p.second;
        } catch (Exception e) {
            e.printStackTrace();
            throw new AsterixException(e);
        }

        AlgebricksPartitionConstraintHelper.setPartitionConstraintInJobSpec(spec, channelQueryExecuter, executerPc);

        NullSinkOperatorDescriptor nullSink = new NullSinkOperatorDescriptor(spec);
        AlgebricksPartitionConstraintHelper.setPartitionConstraintInJobSpec(spec, nullSink, executerPc);
        spec.connect(new OneToOneConnectorDescriptor(spec), channelQueryExecuter, 0, nullSink, 0);
        spec.addRoot(nullSink);
        return spec;
    }

    public static JobSpecification buildDropChannelMessageJob(DropChannelMessage terminateMessage)
            throws AsterixException {
        JobSpecification messageJobSpec = JobSpecificationUtils.createJobSpecification();

        FeedMessageOperatorDescriptor feedMessenger = new FeedMessageOperatorDescriptor(messageJobSpec,
                new FeedConnectionId(new ActiveId(terminateMessage.getChannelId().getDataverse(), terminateMessage
                        .getChannelId().getChannelName()), terminateMessage.getChannelId().getChannelName()),
                terminateMessage);

        ChannelJobInfo cInfo = (ChannelJobInfo) ActiveJobLifecycleListener.INSTANCE.getActiveJobInfo(new ChannelId(
                terminateMessage.getChannelId().getDataverse(), terminateMessage.getChannelId().getChannelName()));

        AlgebricksPartitionConstraint partitionConstraint = new AlgebricksAbsolutePartitionConstraint(cInfo
                .getLocation().toArray(new String[] {}));
        AlgebricksPartitionConstraintHelper.setPartitionConstraintInJobSpec(messageJobSpec, feedMessenger,
                partitionConstraint);
        NullSinkOperatorDescriptor nullSink = new NullSinkOperatorDescriptor(messageJobSpec);
        AlgebricksPartitionConstraintHelper.setPartitionConstraintInJobSpec(messageJobSpec, nullSink,
                partitionConstraint);
        messageJobSpec.connect(new OneToOneConnectorDescriptor(messageJobSpec), feedMessenger, 0, nullSink, 0);
        messageJobSpec.addRoot(nullSink);
        return messageJobSpec;
    }
}
