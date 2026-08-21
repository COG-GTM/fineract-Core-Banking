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

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertThrows

import org.junit.jupiter.api.Test

class TestDatabaseSettingsTest {

    @Test
    void 'postgresql defaults match the containerised CI database'() {
        def settings = TestDatabaseSettings.resolve('postgresql', [:], [:])

        assertEquals('jdbc:postgresql://localhost:5432/fineract_tenants', settings.jdbcUrl())
        assertEquals('org.postgresql.Driver', settings.driverClassName)
        assertEquals('root', settings.username)
        assertEquals('postgres', settings.password)
    }

    @Test
    void 'a null dbType keeps the mariadb build default'() {
        def settings = TestDatabaseSettings.resolve(null, [:], [:])

        assertEquals('jdbc:mariadb://localhost:3306/fineract_tenants', settings.jdbcUrl())
        assertEquals('org.mariadb.jdbc.Driver', settings.driverClassName)
    }

    @Test
    void 'an unsupported dbType is rejected'() {
        assertThrows(IllegalArgumentException) {
            TestDatabaseSettings.resolve('oracle', [:], [:])
        }
    }

    @Test
    void 'gradle properties point the suites at a managed instance'() {
        def settings = TestDatabaseSettings.resolve('postgresql', [
            dbHost: 'fineract-dev.cluster-abc.us-east-1.rds.amazonaws.com',
            dbPort: '5432',
            dbUser: 'fineract',
            dbPassword: 'secret',
            dbConnectionParams: 'sslmode=require'
        ], [:])

        assertEquals('jdbc:postgresql://fineract-dev.cluster-abc.us-east-1.rds.amazonaws.com:5432/fineract_tenants?sslmode=require',
                settings.jdbcUrl())
        assertEquals('jdbc:postgresql://fineract-dev.cluster-abc.us-east-1.rds.amazonaws.com:5432/', settings.serverJdbcUrl())
        assertEquals('fineract', settings.username)
    }

    @Test
    void 'the create and drop tasks connect to the configured maintenance database'() {
        def settings = TestDatabaseSettings.resolve('postgresql', [dbHost: 'aurora.internal', dbMaintenanceName: 'fineract_tenants'], [:])

        assertEquals('jdbc:postgresql://aurora.internal:5432/fineract_tenants', settings.serverJdbcUrl())
    }

    @Test
    void 'environment variables are used when no gradle property is set'() {
        def settings = TestDatabaseSettings.resolve('postgresql', [:], [
            FINERACT_DB_HOST: 'aurora.internal',
            FINERACT_DB_USER: 'fineract',
            FINERACT_DB_PASSWORD: 'secret',
            FINERACT_DB_NAME: 'tenants'
        ])

        assertEquals('jdbc:postgresql://aurora.internal:5432/tenants', settings.jdbcUrl())
        assertEquals('fineract', settings.username)
        assertEquals('secret', settings.password)
    }

    @Test
    void 'gradle properties win over environment variables'() {
        def settings = TestDatabaseSettings.resolve('postgresql', [dbHost: 'from-property'], [FINERACT_DB_HOST: 'from-environment'])

        assertEquals('from-property', settings.host)
    }

    @Test
    void 'jvm args carry both the registry datasource and the tenant connection'() {
        def settings = TestDatabaseSettings.resolve('postgresql', [dbHost: 'aurora.internal', dbUser: 'fineract', dbPassword: 'secret'], [:])

        assertEquals('-Dspring.datasource.hikari.driverClassName=org.postgresql.Driver '
                + '-Dspring.datasource.hikari.jdbcUrl=jdbc:postgresql://aurora.internal:5432/fineract_tenants '
                + '-Dspring.datasource.hikari.username=fineract -Dspring.datasource.hikari.password=secret '
                + '-Dfineract.tenant.host=aurora.internal -Dfineract.tenant.port=5432 '
                + '-Dfineract.tenant.username=fineract -Dfineract.tenant.password=secret', settings.toJvmArgs())
    }
}
