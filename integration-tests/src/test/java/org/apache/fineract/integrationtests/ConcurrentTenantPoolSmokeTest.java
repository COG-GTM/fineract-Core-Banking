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
package org.apache.fineract.integrationtests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Connection pool smoke test for the managed database: it opens one pool per tenant database, the way
 * {@code DataSourcePerTenantServiceFactory} does at runtime, and drives them concurrently.
 *
 * <p>
 * It only runs when {@code FINERACT_DB_HOST} points the build at a managed instance, because it asserts that the pools
 * of all tenants together stay inside the server's connection limit, which is the constraint a container on the build
 * machine does not have.
 * </p>
 */
@Slf4j
@EnabledIfEnvironmentVariable(named = "FINERACT_DB_HOST", matches = ".+")
public class ConcurrentTenantPoolSmokeTest {

    private static final String HOST = System.getenv("FINERACT_DB_HOST");
    private static final String PORT = envOrDefault("FINERACT_DB_PORT", "5432");
    private static final String USER = envOrDefault("FINERACT_DB_USER", "fineract");
    private static final String PASSWORD = System.getenv("FINERACT_DB_PASSWORD");
    private static final List<String> TENANT_DATABASES = Arrays
            .asList(envOrDefault("FINERACT_SMOKE_TENANT_DATABASES", "fineract_default").split(","));
    private static final int MAX_POOL_SIZE = Integer.parseInt(envOrDefault("FINERACT_SMOKE_MAX_POOL_SIZE", "10"));
    private static final int SERVER_MAX_CONNECTIONS = Integer.parseInt(envOrDefault("FINERACT_SMOKE_SERVER_MAX_CONNECTIONS", "400"));
    private static final int REQUESTS_PER_TENANT = Integer.parseInt(envOrDefault("FINERACT_SMOKE_REQUESTS_PER_TENANT", "50"));

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    @Test
    public void tenantPoolsServeConcurrentTrafficWithinTheServerConnectionLimit() throws Exception {
        assertTrue(TENANT_DATABASES.size() * MAX_POOL_SIZE <= SERVER_MAX_CONNECTIONS,
                "The configured tenant pools may exhaust the connection limit of the server");

        List<HikariDataSource> dataSources = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(TENANT_DATABASES.size() * MAX_POOL_SIZE);
        AtomicInteger successfulQueries = new AtomicInteger();
        try {
            for (String tenantDatabase : TENANT_DATABASES) {
                dataSources.add(createPool(tenantDatabase.trim()));
            }

            CountDownLatch start = new CountDownLatch(1);
            List<Callable<Void>> queries = new ArrayList<>();
            for (HikariDataSource dataSource : dataSources) {
                for (int i = 0; i < REQUESTS_PER_TENANT; i++) {
                    queries.add(() -> {
                        start.await();
                        try (Connection connection = dataSource.getConnection();
                                Statement statement = connection.createStatement();
                                ResultSet resultSet = statement.executeQuery("SELECT 1")) {
                            assertTrue(resultSet.next());
                            successfulQueries.incrementAndGet();
                        }
                        return null;
                    });
                }
            }

            List<Future<Void>> futures = new ArrayList<>();
            for (Callable<Void> query : queries) {
                futures.add(executor.submit(query));
            }
            start.countDown();
            for (Future<Void> future : futures) {
                future.get(2, TimeUnit.MINUTES);
            }

            assertEquals(queries.size(), successfulQueries.get());

            int totalConnections = 0;
            for (HikariDataSource dataSource : dataSources) {
                int poolConnections = dataSource.getHikariPoolMXBean().getTotalConnections();
                log.info("Pool {} held {} connections", dataSource.getPoolName(), poolConnections);
                assertTrue(poolConnections <= MAX_POOL_SIZE, "Pool " + dataSource.getPoolName() + " exceeded its maximum size");
                totalConnections += poolConnections;
            }
            assertTrue(totalConnections <= SERVER_MAX_CONNECTIONS,
                    "The tenant pools held " + totalConnections + " connections, more than the server allows");
        } finally {
            executor.shutdownNow();
            dataSources.forEach(HikariDataSource::close);
        }
    }

    private HikariDataSource createPool(String tenantDatabase) {
        HikariConfig config = new HikariConfig();
        config.setPoolName(tenantDatabase + "_pool");
        config.setDriverClassName("org.postgresql.Driver");
        config.setJdbcUrl("jdbc:postgresql://" + HOST + ":" + PORT + "/" + tenantDatabase);
        config.setUsername(USER);
        config.setPassword(PASSWORD);
        config.setMinimumIdle(1);
        config.setMaximumPoolSize(MAX_POOL_SIZE);
        config.setConnectionTimeout(20000);
        config.setMaxLifetime(1800000);
        return new HikariDataSource(config);
    }
}
