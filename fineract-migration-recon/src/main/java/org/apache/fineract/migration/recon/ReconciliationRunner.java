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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Reconciles every tenant of a source deployment against the migrated target. */
public final class ReconciliationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ReconciliationRunner.class);

    private final ReconConfig config;

    public ReconciliationRunner(ReconConfig config) {
        this.config = config;
    }

    public ReconciliationReport run() throws SQLException {
        List<TenantConnection> tenants = tenants();
        List<ReconciliationReport.TenantResult> results = new ArrayList<>();
        for (TenantConnection tenant : tenants) {
            LOG.info("Reconciling tenant {} ({})", tenant.identifier(), tenant.schemaName());
            results.add(reconcileTenant(tenant));
        }
        return new ReconciliationReport(Instant.now(), config.source().describe(), config.target().describe(), results);
    }

    private List<TenantConnection> tenants() throws SQLException {
        try (Connection registry = open(config.source(), config.source().registryDatabase())) {
            List<TenantConnection> tenants = TenantRegistry.tenants(registry);
            if (config.tenantFilter().isEmpty()) {
                return tenants;
            }
            return tenants.stream().filter(tenant -> config.tenantFilter().contains(tenant.identifier())).toList();
        }
    }

    private ReconciliationReport.TenantResult reconcileTenant(TenantConnection tenant) throws SQLException {
        try (Connection source = open(config.source(), tenant.schemaName());
                Connection target = open(config.target(), tenant.schemaName())) {
            DatabaseDialect sourceDialect = config.source().dialect();
            DatabaseDialect targetDialect = config.target().dialect();

            Map<String, Long> sourceCounts = SnapshotReader.rowCounts(source, sourceDialect, sourceDialect.schemaOf(tenant.schemaName()));
            Map<String, Long> targetCounts = SnapshotReader.rowCounts(target, targetDialect, targetDialect.schemaOf(tenant.schemaName()));

            FinancialSnapshot sourceFinancials = SnapshotReader.financials(source);
            FinancialSnapshot targetFinancials = SnapshotReader.financials(target);

            List<Variance> variances = new ArrayList<>();
            variances.addAll(ReconciliationComparator.compareRowCounts(sourceCounts, targetCounts));
            variances.addAll(ReconciliationComparator.compareFinancials(sourceFinancials, targetFinancials));
            variances.addAll(ReconciliationComparator.checkTrialBalance("source", sourceFinancials));
            variances.addAll(ReconciliationComparator.checkTrialBalance("target", targetFinancials));

            List<String> sequenceTables = config.sequenceTables().stream().filter(targetCounts::containsKey).toList();
            variances.addAll(ReconciliationComparator.checkSequences(SnapshotReader.maxIds(target, targetDialect, sequenceTables),
                    SnapshotReader.nextSequenceValues(target, targetDialect, targetDialect.schemaOf(tenant.schemaName()), sequenceTables)));

            return new ReconciliationReport.TenantResult(tenant.identifier(), sourceCounts.size(), total(sourceCounts), total(targetCounts),
                    sourceFinancials.aggregates().size(), variances);
        }
    }

    private static long total(Map<String, Long> counts) {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    private Connection open(ReconConfig.Endpoint endpoint, String database) throws SQLException {
        Connection connection = DriverManager.getConnection(endpoint.jdbcUrl(database), endpoint.username(), endpoint.password());
        connection.setReadOnly(true);
        return connection;
    }
}
