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

import java.time.Instant;
import java.util.List;

/**
 * The reconciliation artefact. It is the evidence the DBA signs, so it records what was compared as well as what
 * differed: a report with no variances but no tenants is not a pass.
 */
public record ReconciliationReport(Instant generatedAt, String sourceDescription, String targetDescription, List<TenantResult> tenants) {

    public record TenantResult(String tenant, int tablesCompared, long sourceRows, long targetRows, int aggregatesCompared,
            List<Variance> variances) {

        public boolean clean() {
            return variances.isEmpty();
        }
    }

    public List<Variance> allVariances() {
        return tenants.stream().flatMap(tenant -> tenant.variances().stream()).toList();
    }

    public boolean zeroVariance() {
        return !tenants.isEmpty() && allVariances().isEmpty();
    }

    public String summary() {
        StringBuilder text = new StringBuilder();
        text.append("Fineract migration reconciliation\n");
        text.append("Generated: ").append(generatedAt).append('\n');
        text.append("Source:    ").append(sourceDescription).append('\n');
        text.append("Target:    ").append(targetDescription).append('\n');
        text.append("Tenants:   ").append(tenants.size()).append('\n');
        text.append('\n');
        for (TenantResult tenant : tenants) {
            text.append(String.format("%-24s tables=%d rows=%d/%d aggregates=%d variances=%d%n", tenant.tenant(), tenant.tablesCompared(),
                    tenant.sourceRows(), tenant.targetRows(), tenant.aggregatesCompared(), tenant.variances().size()));
            for (Variance variance : tenant.variances()) {
                text.append("    ").append(variance).append('\n');
            }
        }
        text.append('\n');
        text.append(zeroVariance() ? "RESULT: ZERO VARIANCE\n" : "RESULT: VARIANCE FOUND (" + allVariances().size() + ")\n");
        text.append('\n');
        text.append("Signed off by (DBA): ______________________  Date: ____________\n");
        return text.toString();
    }
}
