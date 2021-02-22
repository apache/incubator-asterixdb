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
package org.apache.asterix.optimizer.rules.util;

import org.apache.asterix.common.annotations.RangeAnnotation;
import org.apache.asterix.common.annotations.SpatialJoinAnnotation;
import org.apache.asterix.om.base.AInt64;
import org.apache.asterix.om.base.APoint;
import org.apache.asterix.om.base.ARectangle;
import org.apache.asterix.om.constants.AsterixConstantValue;
import org.apache.asterix.om.functions.BuiltinFunctions;
import org.apache.asterix.om.types.ATypeTag;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.hyracks.algebricks.common.exceptions.AlgebricksException;
import org.apache.hyracks.algebricks.core.algebra.base.ILogicalExpression;
import org.apache.hyracks.algebricks.core.algebra.base.ILogicalOperator;
import org.apache.hyracks.algebricks.core.algebra.base.IOptimizationContext;
import org.apache.hyracks.algebricks.core.algebra.base.LogicalExpressionTag;
import org.apache.hyracks.algebricks.core.algebra.base.LogicalVariable;
import org.apache.hyracks.algebricks.core.algebra.expressions.AbstractFunctionCallExpression;
import org.apache.hyracks.algebricks.core.algebra.expressions.ConstantExpression;
import org.apache.hyracks.algebricks.core.algebra.expressions.ScalarFunctionCallExpression;
import org.apache.hyracks.algebricks.core.algebra.expressions.VariableReferenceExpression;
import org.apache.hyracks.algebricks.core.algebra.functions.FunctionIdentifier;
import org.apache.hyracks.algebricks.core.algebra.operators.logical.AbstractBinaryJoinOperator;
import org.apache.hyracks.api.exceptions.ErrorCode;
import org.apache.hyracks.api.exceptions.IWarningCollector;
import org.apache.hyracks.api.exceptions.Warning;
import org.apache.hyracks.dataflow.common.data.partition.range.RangeMap;

import java.util.ArrayList;
import java.util.List;

public class AsterixJoinUtils {

    private static final int LEFT = 0;
    private static final int RIGHT = 1;

    private AsterixJoinUtils() {
    }

    public static void setJoinAlgorithmAndExchangeAlgo(AbstractBinaryJoinOperator op, Boolean topLevelOp,
            IOptimizationContext context) throws AlgebricksException {
        if (!topLevelOp) {
            return;
        }
        ILogicalExpression conditionLE = op.getCondition().getValue();
        if (conditionLE.getExpressionTag() != LogicalExpressionTag.FUNCTION_CALL) {
            return;
        }

        List<LogicalVariable> sideLeft = new ArrayList<>(1);
        List<LogicalVariable> sideRight = new ArrayList<>(1);
        List<LogicalVariable> varsLeft = op.getInputs().get(LEFT).getValue().getSchema();
        List<LogicalVariable> varsRight = op.getInputs().get(RIGHT).getValue().getSchema();

        // Check if the join condition contains spatial join
        SpatialJoinAnnotation spatialJoinAnn = null;
        AbstractFunctionCallExpression spatialJoinFuncExpr = null;
        List<Mutable<ILogicalExpression>> conditionExprs = new ArrayList<>();
        AbstractFunctionCallExpression funcExpr = (AbstractFunctionCallExpression) conditionLE;
        if (funcExpr.getFunctionIdentifier().equals(BuiltinFunctions.SPATIAL_INTERSECT)) {
            Mutable<ILogicalExpression> joinConditionRef = op.getCondition();
            spatialJoinFuncExpr = funcExpr;
            spatialJoinAnn = spatialJoinFuncExpr.getAnnotation(SpatialJoinAnnotation.class);
            if (spatialJoinAnn == null) {
                spatialJoinAnn = new SpatialJoinAnnotation(-180.0, -83.0, 180, 90.0, 10, 10);
            }

            // Extracts spatial intersect function's arguments
            List<Mutable<ILogicalExpression>> spatialJoinInputExprs = spatialJoinFuncExpr.getArguments();
            if (spatialJoinInputExprs.size() != 2) {
                return;
            }

            ILogicalExpression leftOperatingExpr = spatialJoinInputExprs.get(LEFT).getValue();
            ILogicalExpression rightOperatingExpr = spatialJoinInputExprs.get(RIGHT).getValue();

            // left and right expressions should be variables.
            if (leftOperatingExpr.getExpressionTag() == LogicalExpressionTag.CONSTANT
                || rightOperatingExpr.getExpressionTag() == LogicalExpressionTag.CONSTANT) {
                return;
            }

            // Gets both input branches of the spatial join.
            Mutable<ILogicalOperator> leftOp = op.getInputs().get(LEFT);
            Mutable<ILogicalOperator> rightOp = op.getInputs().get(RIGHT);

            // Extract left and right variable of the predicate
            LogicalVariable leftInputVar = ((VariableReferenceExpression) leftOperatingExpr).getVariableReference();
            LogicalVariable rightInputVar = ((VariableReferenceExpression) rightOperatingExpr).getVariableReference();

            // Inject unnest operator to the left and right branch of the join operator
            LogicalVariable leftTileIdVar = SpatialJoinUtils.injectUnnestOperator(context, leftOp, leftInputVar, spatialJoinAnn);
            LogicalVariable rightTileIdVar = SpatialJoinUtils.injectUnnestOperator(context, rightOp, rightInputVar, spatialJoinAnn);

            // Compute reference tile ID
            ScalarFunctionCallExpression referenceTileId = new ScalarFunctionCallExpression(
                BuiltinFunctions.getBuiltinFunctionInfo(BuiltinFunctions.REFERENCE_TILE),
                new MutableObject<>(new VariableReferenceExpression(leftInputVar)),
                new MutableObject<>(new VariableReferenceExpression(rightInputVar)),
                new MutableObject<>(new ConstantExpression(new AsterixConstantValue(
                    new ARectangle(new APoint(spatialJoinAnn.getMinX(), spatialJoinAnn.getMinY()),
                        new APoint(spatialJoinAnn.getMaxX(), spatialJoinAnn.getMaxY()))))),
                new MutableObject<>(
                    new ConstantExpression(new AsterixConstantValue(new AInt64(spatialJoinAnn.getNumRows())))),
                new MutableObject<>(
                    new ConstantExpression(new AsterixConstantValue(new AInt64(spatialJoinAnn.getNumColumns())))));

            // Update the join conditions with the tile Id equality condition
            ScalarFunctionCallExpression tileIdEquiJoinCondition =
                new ScalarFunctionCallExpression(BuiltinFunctions.getBuiltinFunctionInfo(BuiltinFunctions.EQ),
                    new MutableObject<>(new VariableReferenceExpression(leftTileIdVar)),
                    new MutableObject<>(new VariableReferenceExpression(rightTileIdVar)));
            ScalarFunctionCallExpression referenceIdEquiJoinCondition =
                new ScalarFunctionCallExpression(BuiltinFunctions.getBuiltinFunctionInfo(BuiltinFunctions.EQ),
                    new MutableObject<>(new VariableReferenceExpression(leftTileIdVar)),
                    new MutableObject<>(referenceTileId));

            conditionExprs.add(new MutableObject<>(tileIdEquiJoinCondition));
            conditionExprs.add(new MutableObject<>(spatialJoinFuncExpr));
            conditionExprs.add(new MutableObject<>(referenceIdEquiJoinCondition));

            ScalarFunctionCallExpression updatedJoinCondition = new ScalarFunctionCallExpression(
                BuiltinFunctions.getBuiltinFunctionInfo(BuiltinFunctions.AND), conditionExprs);
            joinConditionRef.setValue(updatedJoinCondition);

            List<LogicalVariable> keysLeftBranch = new ArrayList<>();
            keysLeftBranch.add(leftTileIdVar);
            keysLeftBranch.add(leftInputVar);
            List<LogicalVariable> keysRightBranch = new ArrayList<>();
            keysRightBranch.add(rightTileIdVar);
            keysRightBranch.add(rightInputVar);
            SpatialJoinUtils.setSpatialJoinOp(op, keysLeftBranch, keysRightBranch, context);
        } else {
            // Existing workflow for interval merge join
            AbstractFunctionCallExpression fexp = (AbstractFunctionCallExpression) conditionLE;
            FunctionIdentifier fi =
                IntervalJoinUtils.isIntervalJoinCondition(fexp, varsLeft, varsRight, sideLeft, sideRight, LEFT, RIGHT);
            if (fi == null) {
                return;
            }
            RangeAnnotation rangeAnnotation = IntervalJoinUtils.findRangeAnnotation(fexp);
            if (rangeAnnotation == null) {
                return;
            }
            //Check RangeMap type
            RangeMap rangeMap = rangeAnnotation.getRangeMap();
            if (rangeMap.getTag(0, 0) != ATypeTag.DATETIME.serialize() && rangeMap.getTag(0, 0) != ATypeTag.DATE.serialize()
                && rangeMap.getTag(0, 0) != ATypeTag.TIME.serialize()) {
                IWarningCollector warningCollector = context.getWarningCollector();
                if (warningCollector.shouldWarn()) {
                    warningCollector.warn(Warning.forHyracks(op.getSourceLocation(), ErrorCode.INAPPLICABLE_HINT,
                        "Date, DateTime, and Time are only range hints types supported for interval joins"));
                }
                return;
            }
            IntervalPartitions intervalPartitions =
                IntervalJoinUtils.createIntervalPartitions(op, fi, sideLeft, sideRight, rangeMap, context, LEFT, RIGHT);
            IntervalJoinUtils.setSortMergeIntervalJoinOp(op, fi, sideLeft, sideRight, context, intervalPartitions);
        }
    }
}
