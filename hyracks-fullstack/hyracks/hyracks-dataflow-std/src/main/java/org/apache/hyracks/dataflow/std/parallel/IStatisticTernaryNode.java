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

package org.apache.hyracks.dataflow.std.parallel;

/**
 * @author michael
 */
public interface IStatisticTernaryNode<T> extends IStatisticNode {
    public int getId();

    public void setId(int id);

    public short getLimit();

    public void setLimit(int limit);

    public short getLevel();

    public void setLevel(int limit);

    public char getKey();

    public void setKey(char key);

    public boolean isActive();

    public IStatisticTernaryNode<T> getLeft();

    public void setLeft(IStatisticTernaryNode<T> left);

    public IStatisticTernaryNode<T> getRight();

    public void setRight(IStatisticTernaryNode<T> right);

    public IStatisticTernaryNode<T> getMiddle();

    public void setMiddle(IStatisticTernaryNode<T> middle);

    public void setPayload(T payload);

    public T getPayload();

    public void setGrown();

    public boolean isGrown();
}
