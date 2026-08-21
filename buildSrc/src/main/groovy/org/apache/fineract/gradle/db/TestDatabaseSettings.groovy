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
package org.apache.fineract.gradle.db

import groovy.transform.Immutable

/**
 * Connection settings the test suites use to reach the database under test.
 *
 * The defaults reproduce the containerised databases the CI workflows start, so an invocation that
 * passes nothing but {@code -PdbType} behaves exactly as before. Every field can be overridden with
 * a Gradle property, or with the matching {@code FINERACT_DB_*} environment variable, which is what
 * lets the same suites run against a managed instance such as Aurora PostgreSQL.
 */
@Immutable
class TestDatabaseSettings {

    private static final Map<String, TestDatabaseSettings> DEFAULTS = [
        postgresql: new TestDatabaseSettings(dbType: 'postgresql', driverClassName: 'org.postgresql.Driver', host: 'localhost',
        port: '5432', username: 'root', password: 'postgres', tenantsDatabase: 'fineract_tenants', connectionParameters: '',
        maintenanceDatabase: ''),
        mysql: new TestDatabaseSettings(dbType: 'mysql', driverClassName: 'com.mysql.cj.jdbc.Driver', host: 'localhost', port: '3306',
        username: 'root', password: 'mysql', tenantsDatabase: 'fineract_tenants', connectionParameters: '', maintenanceDatabase: ''),
        mariadb: new TestDatabaseSettings(dbType: 'mariadb', driverClassName: 'org.mariadb.jdbc.Driver', host: 'localhost', port: '3306',
        username: 'root', password: 'mysql', tenantsDatabase: 'fineract_tenants', connectionParameters: '', maintenanceDatabase: '')
    ]

    String dbType
    String driverClassName
    String host
    String port
    String username
    String password
    String tenantsDatabase
    String connectionParameters
    String maintenanceDatabase

    /**
     * Resolves the settings for {@code dbType}, applying Gradle property then environment overrides.
     *
     * @param dbType one of postgresql, mysql, mariadb; null selects mariadb, the build default
     * @param properties Gradle project properties: dbHost, dbPort, dbUser, dbPassword, dbName, dbConnectionParams,
     *        dbMaintenanceName
     * @param environment environment variables: FINERACT_DB_HOST, FINERACT_DB_PORT, FINERACT_DB_USER,
     *        FINERACT_DB_PASSWORD, FINERACT_DB_NAME, FINERACT_DB_CONNECTION_PARAMS, FINERACT_DB_MAINTENANCE_NAME
     */
    static TestDatabaseSettings resolve(String dbType, Map<String, ?> properties, Map<String, String> environment) {
        String type = (dbType ?: 'mariadb').toLowerCase()
        TestDatabaseSettings defaults = DEFAULTS[type]
        if (defaults == null) {
            throw new IllegalArgumentException("Provided dbType is not supported: ${dbType}")
        }
        Map<String, ?> props = properties ?: [:]
        Map<String, String> env = environment ?: [:]

        Closure<String> override = { String property, String variable, String fallback ->
            def value = props[property] ?: env[variable]
            value == null ? fallback : value.toString()
        }

        new TestDatabaseSettings(dbType: defaults.dbType, driverClassName: defaults.driverClassName,
        host: override('dbHost', 'FINERACT_DB_HOST', defaults.host),
        port: override('dbPort', 'FINERACT_DB_PORT', defaults.port),
        username: override('dbUser', 'FINERACT_DB_USER', defaults.username),
        password: override('dbPassword', 'FINERACT_DB_PASSWORD', defaults.password),
        tenantsDatabase: override('dbName', 'FINERACT_DB_NAME', defaults.tenantsDatabase),
        connectionParameters: override('dbConnectionParams', 'FINERACT_DB_CONNECTION_PARAMS', defaults.connectionParameters),
        maintenanceDatabase: override('dbMaintenanceName', 'FINERACT_DB_MAINTENANCE_NAME', defaults.maintenanceDatabase))
    }

    /** JDBC URL for {@code database} on this server. */
    String jdbcUrl(String database) {
        String url = "jdbc:${dbType}://${host}:${port}/${database}"
        connectionParameters ? "${url}?${connectionParameters}" : url
    }

    /** JDBC URL for the tenant registry. */
    String jdbcUrl() {
        jdbcUrl(tenantsDatabase)
    }

    /**
     * JDBC URL used by the tasks that create and drop databases. PostgreSQL always connects to a database,
     * and a managed instance has no database named after the master user, so the maintenance database has to
     * be selectable; an empty value keeps the server URL the containerised databases accept.
     */
    String serverJdbcUrl() {
        "jdbc:${dbType}://${host}:${port}/${maintenanceDatabase}"
    }

    /**
     * System properties that point a Fineract instance under test at this server: the tenant registry
     * datasource, and the tenant connection details Liquibase writes into the registry.
     */
    String toJvmArgs() {
        [
            "-Dspring.datasource.hikari.driverClassName=${driverClassName}",
            "-Dspring.datasource.hikari.jdbcUrl=${jdbcUrl()}",
            "-Dspring.datasource.hikari.username=${username}",
            "-Dspring.datasource.hikari.password=${password}",
            "-Dfineract.tenant.host=${host}",
            "-Dfineract.tenant.port=${port}",
            "-Dfineract.tenant.username=${username}",
            "-Dfineract.tenant.password=${password}"
        ].join(' ')
    }
}
