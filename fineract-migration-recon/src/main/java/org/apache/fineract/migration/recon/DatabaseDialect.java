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
 * The database engines the reconciliation can read. Both sides of a reconciliation are read through plain JDBC, so the
 * only engine-specific concerns are identifier quoting and how a tenant database maps onto a JDBC URL and a schema.
 */
public enum DatabaseDialect {

    MARIADB('`') {

        @Override
        public String jdbcUrl(String host, String port, String database) {
            return "jdbc:mariadb://" + host + ":" + port + "/" + database;
        }

        @Override
        public String schemaOf(String database) {
            return database;
        }
    },

    POSTGRESQL('"') {

        @Override
        public String jdbcUrl(String host, String port, String database) {
            return "jdbc:postgresql://" + host + ":" + port + "/" + database;
        }

        @Override
        public String schemaOf(String database) {
            return "public";
        }
    };

    private final char quote;

    DatabaseDialect(char quote) {
        this.quote = quote;
    }

    public abstract String jdbcUrl(String host, String port, String database);

    /**
     * The {@code information_schema.tables.table_schema} value that holds a tenant's tables. On MariaDB a tenant is a
     * database; on PostgreSQL it is a database whose tables live in {@code public}.
     */
    public abstract String schemaOf(String database);

    public String quoteIdentifier(String identifier) {
        if (!identifier.matches("[A-Za-z0-9_$]+")) {
            throw new IllegalArgumentException("Refusing to quote suspicious identifier: " + identifier);
        }
        return quote + identifier + quote;
    }

    public static DatabaseDialect fromJdbcUrl(String jdbcUrl) {
        if (jdbcUrl.startsWith("jdbc:postgresql:")) {
            return POSTGRESQL;
        }
        if (jdbcUrl.startsWith("jdbc:mariadb:") || jdbcUrl.startsWith("jdbc:mysql:")) {
            return MARIADB;
        }
        throw new IllegalArgumentException("Unsupported JDBC URL for reconciliation: " + jdbcUrl);
    }
}
