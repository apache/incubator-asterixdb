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
package org.apache.asterix.dataflow.data.nontagged.printers.csv;

import java.io.PrintStream;

import org.apache.asterix.dataflow.data.nontagged.serde.ADoubleSerializerDeserializer;
import org.apache.asterix.dataflow.data.nontagged.serde.AInt16SerializerDeserializer;
import org.apache.hyracks.algebricks.data.IPrinter;
import org.apache.hyracks.api.exceptions.HyracksDataException;

public class APolygonPrinter implements IPrinter {

    public static final APolygonPrinter INSTANCE = new APolygonPrinter();

    @Override
    public void init() {
    }

    @Override
    public void print(byte[] b, int s, int l, PrintStream ps) throws HyracksDataException {
        short numberOfPoints = AInt16SerializerDeserializer.getShort(b, s + 1);
        s += 3;

        ps.print("\"[ ");

        for (int i = 0; i < numberOfPoints; i++) {
            if (i > 0)
                ps.print(", ");

            ps.print("[");
            ps.print(ADoubleSerializerDeserializer.getDouble(b, s));
            ps.print(", ");
            ps.print(ADoubleSerializerDeserializer.getDouble(b, s + 8));
            ps.print("]");

            s += 16;
        }

        ps.print(" ]\"");
    }
}
