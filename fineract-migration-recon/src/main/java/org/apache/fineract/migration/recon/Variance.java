/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.migration.recon;

/**
 * A single difference between source and target. Any variance at all fails the reconciliation: there is no acceptable
 * tolerance on a ledger.
 */
public record Variance(Kind kind, String scope, String detail, String sourceValue, String targetValue) {

    public enum Kind {
        MISSING_IN_TARGET, EXTRA_IN_TARGET, ROW_COUNT_MISMATCH, AMOUNT_MISMATCH, TRIAL_BALANCE_IMBALANCE, SEQUENCE_BEHIND_MAX_ID
    }

    @Override
    public String toString() {
        return kind + " [" + scope + "] " + detail + ": source=" + sourceValue + " target=" + targetValue;
    }
}
