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

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads the two levels of reconciliation input from one tenant database: exact row counts per table, and the aggregated
 * financial position of loans, journal entries and savings.
 *
 * Every count is a real {@code count(*)}. Catalogue statistics are approximate on both engines and are not evidence.
 */
public final class SnapshotReader {

    private static final String TABLE_LIST_SQL = """
            select table_name from information_schema.tables
            where table_schema = ? and table_type = 'BASE TABLE'
            """;

    private static final String LOANS_SQL = """
            select currency_code,
                   count(*) as loan_count,
                   sum(coalesce(principal_disbursed_derived, 0)) as principal_disbursed,
                   sum(coalesce(principal_repaid_derived, 0)) as principal_repaid,
                   sum(coalesce(principal_outstanding_derived, 0)) as principal_outstanding,
                   sum(coalesce(interest_charged_derived, 0)) as interest_charged,
                   sum(coalesce(interest_repaid_derived, 0)) as interest_repaid,
                   sum(coalesce(interest_outstanding_derived, 0)) as interest_outstanding,
                   sum(coalesce(total_outstanding_derived, 0)) as total_outstanding
            from m_loan
            group by currency_code
            """;

    private static final String JOURNAL_ENTRIES_SQL = """
            select currency_code, office_id, account_id, type_enum,
                   count(*) as entry_count,
                   sum(coalesce(amount, 0)) as amount
            from acc_gl_journal_entry
            group by currency_code, office_id, account_id, type_enum
            """;

    private static final String SAVINGS_SQL = """
            select currency_code,
                   count(*) as savings_count,
                   sum(coalesce(account_balance_derived, 0)) as account_balance,
                   sum(coalesce(total_deposits_derived, 0)) as total_deposits,
                   sum(coalesce(total_withdrawals_derived, 0)) as total_withdrawals
            from m_savings_account
            group by currency_code
            """;

    private SnapshotReader() {}

    public static Map<String, Long> rowCounts(Connection connection, DatabaseDialect dialect, String schema) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(TABLE_LIST_SQL)) {
            statement.setString(1, schema);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    tables.add(resultSet.getString(1).toLowerCase(Locale.ROOT));
                }
            }
        }
        Map<String, Long> counts = new TreeMap<>();
        try (Statement statement = connection.createStatement()) {
            for (String table : tables) {
                try (ResultSet resultSet = statement.executeQuery("select count(*) from " + dialect.quoteIdentifier(table))) {
                    resultSet.next();
                    counts.put(table, resultSet.getLong(1));
                }
            }
        }
        return counts;
    }

    public static FinancialSnapshot financials(Connection connection) throws SQLException {
        FinancialSnapshot.Builder builder = FinancialSnapshot.builder();
        readAggregate(connection, LOANS_SQL, "loans", 1, builder);
        readAggregate(connection, JOURNAL_ENTRIES_SQL, ReconciliationComparator.JOURNAL_ENTRIES, 4, builder);
        readAggregate(connection, SAVINGS_SQL, "savings", 1, builder);
        return builder.build();
    }

    /**
     * Highest primary key per table, used together with {@link #nextSequenceValues} to prove that the target's
     * generators are ahead of the migrated data.
     */
    public static Map<String, Long> maxIds(Connection connection, DatabaseDialect dialect, List<String> tables) throws SQLException {
        Map<String, Long> maxIds = new TreeMap<>();
        try (Statement statement = connection.createStatement()) {
            for (String table : tables) {
                try (ResultSet resultSet = statement.executeQuery("select coalesce(max(id), 0) from " + dialect.quoteIdentifier(table))) {
                    resultSet.next();
                    maxIds.put(table, resultSet.getLong(1));
                }
            }
        }
        return maxIds;
    }

    /**
     * The value the target would hand out next, per table. PostgreSQL identity/serial columns are read from their
     * sequence; MariaDB reports the table's {@code AUTO_INCREMENT}.
     */
    public static Map<String, Long> nextSequenceValues(Connection connection, DatabaseDialect dialect, String schema, List<String> tables)
            throws SQLException {
        Map<String, Long> nextValues = new TreeMap<>();
        if (dialect == DatabaseDialect.POSTGRESQL) {
            // pg_get_serial_sequence resolves the owning sequence of an identity or serial column whatever it is named;
            // only the sequence relation itself carries is_called, which decides whether last_value was handed out
            String catalogSql = """
                    select s.start_value, q.sequence
                    from pg_get_serial_sequence(format('%I.%I', ?::text, ?::text), 'id') as q(sequence)
                    join pg_sequences s on s.schemaname || '.' || s.sequencename = q.sequence
                    """;
            for (String table : tables) {
                long startValue;
                String qualified;
                try (PreparedStatement statement = connection.prepareStatement(catalogSql)) {
                    statement.setString(1, schema);
                    statement.setString(2, table);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            continue;
                        }
                        startValue = resultSet.getLong(1);
                        qualified = resultSet.getString(2);
                    }
                }
                try (Statement statement = connection.createStatement();
                        ResultSet resultSet = statement.executeQuery("select last_value, is_called from " + qualified)) {
                    if (resultSet.next()) {
                        long lastValue = resultSet.getLong(1);
                        boolean lastValueMissing = resultSet.wasNull();
                        boolean isCalled = resultSet.getBoolean(2);
                        nextValues.put(table, lastValueMissing ? startValue : isCalled ? lastValue + 1 : lastValue);
                    }
                }
            }
            return nextValues;
        }
        String sql = "select table_name, auto_increment from information_schema.tables where table_schema = ? and auto_increment is not null";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String table = resultSet.getString(1).toLowerCase(Locale.ROOT);
                    if (tables.contains(table)) {
                        nextValues.put(table, resultSet.getLong(2));
                    }
                }
            }
        }
        return nextValues;
    }

    private static void readAggregate(Connection connection, String sql, String dataset, int keyColumns, FinancialSnapshot.Builder builder)
            throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            int columnCount = resultSet.getMetaData().getColumnCount();
            while (resultSet.next()) {
                String[] keyParts = new String[keyColumns];
                for (int i = 1; i <= keyColumns; i++) {
                    String value = resultSet.getString(i);
                    keyParts[i - 1] = value == null ? "" : value;
                }
                Map<String, BigDecimal> measures = new LinkedHashMap<>();
                for (int i = keyColumns + 1; i <= columnCount; i++) {
                    String name = resultSet.getMetaData().getColumnLabel(i).toLowerCase(Locale.ROOT);
                    BigDecimal value = resultSet.getBigDecimal(i);
                    measures.put(name, value == null ? BigDecimal.ZERO : value);
                }
                builder.put(AggregateKey.of(dataset, keyParts), measures);
            }
        }
    }
}
