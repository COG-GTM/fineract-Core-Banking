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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Reconciles a MariaDB source against a PostgreSQL target on real engines, because the differences that matter (decimal
 * scale, boolean storage, identifier folding, sequences) do not reproduce against a mock.
 */
class CrossEngineReconciliationTest {

    private static MariaDBContainer<?> mariadb;
    private static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void startEngines() throws SQLException {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for the cross-engine reconciliation test");
        mariadb = new MariaDBContainer<>("mariadb:11.5.2").withDatabaseName("fineract_default");
        postgres = new PostgreSQLContainer<>("postgres:18.3").withDatabaseName("fineract_default");
        mariadb.start();
        postgres.start();
        seedMariaDb();
        seedPostgres();
    }

    @AfterAll
    static void stopEngines() {
        if (mariadb != null) {
            mariadb.stop();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void faithfulMigrationReconcilesAcrossEngines() throws SQLException {
        try (Connection source = source(); Connection target = target()) {
            FinancialSnapshot sourceFinancials = SnapshotReader.financials(source);
            FinancialSnapshot targetFinancials = SnapshotReader.financials(target);

            assertThat(sourceFinancials.aggregates()).isNotEmpty();
            assertThat(ReconciliationComparator.compareFinancials(sourceFinancials, targetFinancials)).isEmpty();
            assertThat(ReconciliationComparator.checkTrialBalance("target", targetFinancials)).isEmpty();
            assertThat(ReconciliationComparator.compareRowCounts(rowCounts(source, DatabaseDialect.MARIADB, "fineract_default"),
                    rowCounts(target, DatabaseDialect.POSTGRESQL, "public"))).isEmpty();
        }
    }

    @Test
    void aRepaymentLostInTransitFailsTheReconciliation() throws SQLException {
        try (Connection source = source(); Connection target = target(); Statement statement = target.createStatement()) {
            statement.executeUpdate("update m_loan set principal_repaid_derived = principal_repaid_derived - 0.000001 where id = 1");
            try {
                List<Variance> variances = ReconciliationComparator.compareFinancials(SnapshotReader.financials(source),
                        SnapshotReader.financials(target));

                assertThat(variances).extracting(Variance::kind, Variance::detail)
                        .contains(org.assertj.core.groups.Tuple.tuple(Variance.Kind.AMOUNT_MISMATCH, "principal_repaid"));
            } finally {
                statement.executeUpdate("update m_loan set principal_repaid_derived = principal_repaid_derived + 0.000001 where id = 1");
            }
        }
    }

    @Test
    void sequenceNotAdvancedAfterTheLoadIsFound() throws SQLException {
        try (Connection target = target()) {
            List<String> tables = List.of("m_loan", "acc_gl_journal_entry", "m_savings_account");
            Map<String, Long> maxIds = SnapshotReader.maxIds(target, DatabaseDialect.POSTGRESQL, tables);
            Map<String, Long> nextValues = SnapshotReader.nextSequenceValues(target, DatabaseDialect.POSTGRESQL, "public", tables);

            // the loader inserted explicit ids, which is exactly what a dump/restore or a DMS full load does
            assertThat(ReconciliationComparator.checkSequences(maxIds, nextValues)).extracting(Variance::kind)
                    .containsOnly(Variance.Kind.SEQUENCE_BEHIND_MAX_ID);

            try (Statement statement = target.createStatement()) {
                for (String table : tables) {
                    statement.execute("select setval('" + table + "_id_seq', (select coalesce(max(id), 0) + 1 from " + table + "), false)");
                }
            }
            assertThat(ReconciliationComparator.checkSequences(SnapshotReader.maxIds(target, DatabaseDialect.POSTGRESQL, tables),
                    SnapshotReader.nextSequenceValues(target, DatabaseDialect.POSTGRESQL, "public", tables))).isEmpty();
        }
    }

    private static Map<String, Long> rowCounts(Connection connection, DatabaseDialect dialect, String schema) throws SQLException {
        return SnapshotReader.rowCounts(connection, dialect, schema);
    }

    private static Connection source() throws SQLException {
        return DriverManager.getConnection(mariadb.getJdbcUrl(), mariadb.getUsername(), mariadb.getPassword());
    }

    private static Connection target() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static void seedMariaDb() throws SQLException {
        try (Connection connection = source(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table m_loan (
                        id bigint not null auto_increment primary key,
                        currency_code varchar(3) not null,
                        principal_disbursed_derived decimal(19,6),
                        principal_repaid_derived decimal(19,6),
                        principal_outstanding_derived decimal(19,6),
                        interest_charged_derived decimal(19,6),
                        interest_repaid_derived decimal(19,6),
                        interest_outstanding_derived decimal(19,6),
                        total_outstanding_derived decimal(19,6))
                    """);
            statement.execute("""
                    create table acc_gl_journal_entry (
                        id bigint not null auto_increment primary key,
                        office_id bigint not null,
                        account_id bigint not null,
                        currency_code varchar(3) not null,
                        type_enum smallint not null,
                        reversed tinyint(1) not null default 0,
                        amount decimal(19,6) not null)
                    """);
            statement.execute("""
                    create table m_savings_account (
                        id bigint not null auto_increment primary key,
                        currency_code varchar(3) not null,
                        account_balance_derived decimal(19,6),
                        total_deposits_derived decimal(19,6),
                        total_withdrawals_derived decimal(19,6))
                    """);
            seedRows(statement);
        }
    }

    private static void seedPostgres() throws SQLException {
        try (Connection connection = target(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table m_loan (
                        id bigint generated by default as identity primary key,
                        currency_code varchar(3) not null,
                        principal_disbursed_derived numeric(19,6),
                        principal_repaid_derived numeric(19,6),
                        principal_outstanding_derived numeric(19,6),
                        interest_charged_derived numeric(19,6),
                        interest_repaid_derived numeric(19,6),
                        interest_outstanding_derived numeric(19,6),
                        total_outstanding_derived numeric(19,6))
                    """);
            statement.execute("""
                    create table acc_gl_journal_entry (
                        id bigint generated by default as identity primary key,
                        office_id bigint not null,
                        account_id bigint not null,
                        currency_code varchar(3) not null,
                        type_enum smallint not null,
                        reversed boolean not null default false,
                        amount numeric(19,6) not null)
                    """);
            statement.execute("""
                    create table m_savings_account (
                        id bigint generated by default as identity primary key,
                        currency_code varchar(3) not null,
                        account_balance_derived numeric(19,6),
                        total_deposits_derived numeric(19,6),
                        total_withdrawals_derived numeric(19,6))
                    """);
            seedRows(statement);
        }
    }

    private static void seedRows(Statement statement) throws SQLException {
        statement.executeUpdate("""
                insert into m_loan (id, currency_code, principal_disbursed_derived, principal_repaid_derived,
                    principal_outstanding_derived, interest_charged_derived, interest_repaid_derived,
                    interest_outstanding_derived, total_outstanding_derived) values
                    (1, 'USD', 1000.000000, 250.000000, 750.000000, 100.000000, 25.000000, 75.000000, 825.000000),
                    (2, 'USD', 2000.000000, 500.000000, 1500.000000, 200.000000, 50.000000, 150.000000, 1650.000000),
                    (3, 'EUR', 500.000000, 0.000000, 500.000000, 50.000000, 0.000000, 50.000000, 550.000000)
                """);
        statement.executeUpdate("""
                insert into acc_gl_journal_entry (id, office_id, account_id, currency_code, type_enum, amount) values
                    (1, 1, 4001, 'USD', 1, 900.000000),
                    (2, 1, 5001, 'USD', 2, 900.000000),
                    (3, 1, 4001, 'EUR', 1, 500.000000),
                    (4, 1, 5001, 'EUR', 2, 500.000000)
                """);
        statement.executeUpdate("""
                insert into m_savings_account (id, currency_code, account_balance_derived, total_deposits_derived,
                    total_withdrawals_derived) values
                    (1, 'USD', 250.000000, 400.000000, 150.000000),
                    (2, 'EUR', 100.000000, 100.000000, 0.000000)
                """);
    }
}
