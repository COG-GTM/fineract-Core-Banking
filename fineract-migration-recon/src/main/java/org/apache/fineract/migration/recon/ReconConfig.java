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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/** Where the two databases are and which tenants to reconcile. Credentials are never read from the tenant registry. */
public record ReconConfig(Endpoint source, Endpoint target, List<String> tenantFilter, List<String> sequenceTables, Path outputDirectory) {

    public static final List<String> DEFAULT_SEQUENCE_TABLES = List.of("m_loan", "m_loan_transaction", "m_savings_account",
            "m_savings_account_transaction", "acc_gl_journal_entry", "m_client", "m_office");

    public record Endpoint(DatabaseDialect dialect, String host, String port, String registryDatabase, String username, String password) {

        public String jdbcUrl(String database) {
            return dialect.jdbcUrl(host, port, database);
        }

        public String describe() {
            return dialect.name().toLowerCase(Locale.ROOT) + "://" + host + ":" + port;
        }
    }

    public static ReconConfig load(Path propertiesFile) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(propertiesFile)) {
            properties.load(input);
        }
        return of(properties);
    }

    public static ReconConfig of(Properties properties) {
        return new ReconConfig(endpoint(properties, "source"), endpoint(properties, "target"), list(properties.getProperty("tenants", "")),
                sequenceTables(properties), Path.of(properties.getProperty("output.directory", "build/recon")));
    }

    private static Endpoint endpoint(Properties properties, String prefix) {
        String engine = required(properties, prefix + ".engine");
        DatabaseDialect dialect = DatabaseDialect.valueOf(engine.toUpperCase(Locale.ROOT));
        return new Endpoint(dialect, required(properties, prefix + ".host"), required(properties, prefix + ".port"),
                properties.getProperty(prefix + ".registryDatabase", "fineract_tenants"), required(properties, prefix + ".username"),
                required(properties, prefix + ".password"));
    }

    private static List<String> sequenceTables(Properties properties) {
        List<String> configured = list(properties.getProperty("sequence.tables", ""));
        return configured.isEmpty() ? DEFAULT_SEQUENCE_TABLES : configured;
    }

    private static List<String> list(String value) {
        return Arrays.stream(value.split(",")).map(String::trim).filter(entry -> !entry.isEmpty()).toList();
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required reconciliation property: " + key);
        }
        return value;
    }
}
