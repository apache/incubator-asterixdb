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
package edu.uci.ics.asterix.common.feeds.api;

import java.io.IOException;
import java.util.List;

import edu.uci.ics.asterix.common.feeds.FeedConnectionId;
import edu.uci.ics.asterix.common.feeds.FeedRuntime;
import edu.uci.ics.asterix.common.feeds.FeedRuntimeId;
import edu.uci.ics.asterix.common.feeds.FeedRuntimeManager;

/**
 * Handle (de)registration of feeds for delivery of control messages.
 */
public interface IFeedConnectionManager {

    /**
     * Allows registration of a feedRuntime.
     * 
     * @param feedRuntime
     * @throws Exception
     */
    public void registerFeedRuntime(FeedConnectionId connectionId, FeedRuntime feedRuntime) throws Exception;

    /**
     * Obtain feed runtime corresponding to a feedRuntimeId
     * 
     * @param feedRuntimeId
     * @return
     */
    public FeedRuntime getFeedRuntime(FeedConnectionId connectionId, FeedRuntimeId feedRuntimeId);

    /**
     * De-register a feed
     * 
     * @param feedConnection
     * @throws IOException
     */
    void deregisterFeed(FeedConnectionId feedConnection);

    /**
     * Obtain the feed runtime manager associated with a feed.
     * 
     * @param feedConnection
     * @return
     */
    public FeedRuntimeManager getFeedRuntimeManager(FeedConnectionId feedConnection);

    /**
     * Allows de-registration of a feed runtime.
     * 
     * @param feedRuntimeId
     */
    void deRegisterFeedRuntime(FeedConnectionId connectionId, FeedRuntimeId feedRuntimeId);

    public List<FeedRuntimeId> getRegisteredRuntimes();

}