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
package org.apache.asterix.lang.common.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.asterix.common.exceptions.CompilationException;
import org.apache.asterix.common.exceptions.ErrorCode;
import org.apache.asterix.object.base.AdmObjectNode;
import org.apache.asterix.object.base.AdmStringNode;
import org.apache.asterix.object.base.IAdmNode;

public class MergePolicyUtils {
    public static final String MERGE_POLICY_PARAMETER_NAME = "merge-policy";
    public static final String MERGE_POLICY_NAME_PARAMETER_NAME = "name";
    public static final String MERGE_POLICY_PARAMETERS_PARAMETER_NAME = "parameters";

    private MergePolicyUtils() {
    }

    /**
     * Convert the parameters object to a Map<String,String>
     * This method should go away once we store the with object as it is in storage
     *
     * @param parameters
     *            the parameters passed for the merge policy in the with clause
     * @return the parameters as a map
     */
    public static Map<String, String> toProperties(AdmObjectNode parameters) throws CompilationException {
        Map<String, String> map = new HashMap<>();
        for (Entry<String, IAdmNode> field : parameters.getFields()) {
            IAdmNode value = field.getValue();
            switch (value.getType()) {
                case BOOLEAN:
                case DOUBLE:
                case BIGINT:
                    map.put(field.getKey(), value.toString());
                    break;
                case STRING:
                    map.put(field.getKey(), ((AdmStringNode) value).get());
                    break;
                default:
                    throw new CompilationException(ErrorCode.MERGE_POLICY_PARAMETER_INVALID_TYPE, value.getType());
            }
        }
        return map;
    }

}
