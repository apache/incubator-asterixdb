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
package org.apache.asterix.external.parser.factory;

import java.io.IOException;
import java.util.Map;

import org.apache.asterix.common.exceptions.AsterixException;
import org.apache.asterix.external.api.IExternalDataSourceFactory.DataSourceType;
import org.apache.asterix.external.api.IRecordDataParser;
import org.apache.asterix.external.api.IRecordDataParserFactory;
import org.apache.asterix.external.parser.RSSParser;
import org.apache.asterix.om.types.ARecordType;
import org.apache.hyracks.api.context.IHyracksTaskContext;

import com.sun.syndication.feed.synd.SyndEntryImpl;

public class RSSParserFactory implements IRecordDataParserFactory<SyndEntryImpl> {

    private static final long serialVersionUID = 1L;
    private ARecordType recordType;
    private Map<String, String> configuration;

    @Override
    public DataSourceType getDataSourceType() throws AsterixException {
        return DataSourceType.RECORDS;
    }

    @Override
    public void configure(Map<String, String> configuration) throws Exception {
        this.configuration = configuration;
    }

    @Override
    public void setRecordType(ARecordType recordType) {
        this.recordType = recordType;
    }

    @Override
    public IRecordDataParser<SyndEntryImpl> createRecordParser(IHyracksTaskContext ctx)
            throws AsterixException, IOException {
        RSSParser dataParser = new RSSParser();
        dataParser.configure(configuration, recordType);
        return dataParser;
    }

    @Override
    public Class<? extends SyndEntryImpl> getRecordClass() {
        return SyndEntryImpl.class;
    }

}
