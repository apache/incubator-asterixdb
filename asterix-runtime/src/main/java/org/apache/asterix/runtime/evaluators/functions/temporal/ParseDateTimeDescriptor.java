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
package org.apache.asterix.runtime.evaluators.functions.temporal;

import java.io.DataOutput;

import org.apache.asterix.formats.nontagged.AqlSerializerDeserializerProvider;
import org.apache.asterix.om.base.ADateTime;
import org.apache.asterix.om.base.AMutableDateTime;
import org.apache.asterix.om.base.ANull;
import org.apache.asterix.om.base.temporal.AsterixTemporalTypeParseException;
import org.apache.asterix.om.base.temporal.DateTimeFormatUtils;
import org.apache.asterix.om.base.temporal.DateTimeFormatUtils.DateTimeParseMode;
import org.apache.asterix.om.functions.AsterixBuiltinFunctions;
import org.apache.asterix.om.functions.IFunctionDescriptor;
import org.apache.asterix.om.functions.IFunctionDescriptorFactory;
import org.apache.asterix.om.types.ATypeTag;
import org.apache.asterix.om.types.BuiltinType;
import org.apache.asterix.om.types.EnumDeserializer;
import org.apache.asterix.runtime.evaluators.base.AbstractScalarFunctionDynamicDescriptor;
import org.apache.hyracks.algebricks.common.exceptions.AlgebricksException;
import org.apache.hyracks.algebricks.core.algebra.functions.FunctionIdentifier;
import org.apache.hyracks.algebricks.runtime.base.ICopyEvaluator;
import org.apache.hyracks.algebricks.runtime.base.ICopyEvaluatorFactory;
import org.apache.hyracks.api.dataflow.value.ISerializerDeserializer;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.data.std.api.IDataOutputProvider;
import org.apache.hyracks.data.std.primitive.UTF8StringPointable;
import org.apache.hyracks.data.std.util.ArrayBackedValueStorage;
import org.apache.hyracks.dataflow.common.data.accessors.IFrameTupleReference;

public class ParseDateTimeDescriptor extends AbstractScalarFunctionDynamicDescriptor {
    private static final long serialVersionUID = 1L;
    public final static FunctionIdentifier FID = AsterixBuiltinFunctions.PARSE_DATETIME;
    private final static DateTimeFormatUtils DT_UTILS = DateTimeFormatUtils.getInstance();
    public final static IFunctionDescriptorFactory FACTORY = new IFunctionDescriptorFactory() {

        @Override
        public IFunctionDescriptor createFunctionDescriptor() {
            return new ParseDateTimeDescriptor();
        }
    };

    @Override
    public ICopyEvaluatorFactory createEvaluatorFactory(final ICopyEvaluatorFactory[] args) throws AlgebricksException {
        return new ICopyEvaluatorFactory() {

            private static final long serialVersionUID = 1L;

            @Override
            public ICopyEvaluator createEvaluator(final IDataOutputProvider output) throws AlgebricksException {
                return new ICopyEvaluator() {

                    private DataOutput out = output.getDataOutput();
                    private ArrayBackedValueStorage argOut0 = new ArrayBackedValueStorage();
                    private ArrayBackedValueStorage argOut1 = new ArrayBackedValueStorage();
                    private ICopyEvaluator eval0 = args[0].createEvaluator(argOut0);
                    private ICopyEvaluator eval1 = args[1].createEvaluator(argOut1);

                    @SuppressWarnings("unchecked")
                    private ISerializerDeserializer<ANull> nullSerde = AqlSerializerDeserializerProvider.INSTANCE
                            .getSerializerDeserializer(BuiltinType.ANULL);
                    @SuppressWarnings("unchecked")
                    private ISerializerDeserializer<ADateTime> datetimeSerde = AqlSerializerDeserializerProvider.INSTANCE
                            .getSerializerDeserializer(BuiltinType.ADATETIME);

                    private AMutableDateTime aDateTime = new AMutableDateTime(0);
                    private final UTF8StringPointable utf8Ptr = new UTF8StringPointable();

                    @Override
                    public void evaluate(IFrameTupleReference tuple) throws AlgebricksException {
                        argOut0.reset();
                        eval0.evaluate(tuple);
                        argOut1.reset();
                        eval1.evaluate(tuple);

                        try {
                            if (argOut0.getByteArray()[0] == ATypeTag.SERIALIZED_NULL_TYPE_TAG
                                    || argOut1.getByteArray()[0] == ATypeTag.SERIALIZED_NULL_TYPE_TAG) {
                                nullSerde.serialize(ANull.NULL, out);
                                return;
                            }

                            if (argOut0.getByteArray()[0] != ATypeTag.SERIALIZED_STRING_TYPE_TAG
                                    || argOut1.getByteArray()[0] != ATypeTag.SERIALIZED_STRING_TYPE_TAG) {
                                throw new AlgebricksException(
                                        getIdentifier().getName() + ": expects two strings but got  ("
                                                + EnumDeserializer.ATYPETAGDESERIALIZER.deserialize(
                                                        argOut0.getByteArray()[0])
                                                + ", " + EnumDeserializer.ATYPETAGDESERIALIZER
                                                        .deserialize(argOut1.getByteArray()[0])
                                                + ")");
                            }
                            utf8Ptr.set(argOut0.getByteArray(), 1, argOut0.getLength() - 1);
                            int start0 = utf8Ptr.getCharStartOffset();
                            int length0 = utf8Ptr.getUTF8Length();

                            utf8Ptr.set(argOut1.getByteArray(), 1, argOut1.getLength() - 1);
                            int start1 = utf8Ptr.getCharStartOffset();
                            int length1 = utf8Ptr.getUTF8Length();
                            long chronon = 0;

                            int formatStart = start1;
                            int formatLength = 0;
                            boolean processSuccessfully = false;
                            while (!processSuccessfully && formatStart < start1 + length1) {
                                // search for "|"
                                formatLength = 0;
                                for (; formatStart + formatLength < start1 + length1; formatLength++) {
                                    if (argOut1.getByteArray()[formatStart + formatLength] == '|') {
                                        break;
                                    }
                                }
                                try {
                                    chronon = DT_UTILS.parseDateTime(argOut0.getByteArray(), start0, length0,
                                            argOut1.getByteArray(), formatStart, formatLength,
                                            DateTimeParseMode.DATETIME);
                                } catch (AsterixTemporalTypeParseException ex) {
                                    formatStart += formatLength + 1;
                                    continue;
                                }
                                processSuccessfully = true;
                            }

                            if (!processSuccessfully) {
                                throw new HyracksDataException(
                                        "parse-datetime: Failed to match with any given format string!");
                            }

                            aDateTime.setValue(chronon);
                            datetimeSerde.serialize(aDateTime, out);

                        } catch (HyracksDataException ex) {
                            throw new AlgebricksException(ex);
                        }
                    }
                };
            }

        };
    }

    /* (non-Javadoc)
     * @see org.apache.asterix.om.functions.AbstractFunctionDescriptor#getIdentifier()
     */
    @Override
    public FunctionIdentifier getIdentifier() {
        return FID;
    }

}