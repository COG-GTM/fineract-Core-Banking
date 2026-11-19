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
package org.apache.fineract.infrastructure.core.service.database;

import com.google.common.collect.Sets;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.configuration.service.MoneyHelperInitializationService;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenantConnection;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.core.service.tenant.TenantDetailsService;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Service;

/**
 * Implementation that returns a new or existing connection pool datasource based on the tenant details stored in a
 * {@link ThreadLocal} variable for this request.
 *
 * {@link ThreadLocalContextUtil} is used to retrieve the {@link FineractPlatformTenant} for the request.
 *
 * Pools are cached by connection id. When the connection details of a cached connection id change, or a tenant moves to
 * a different connection id, the cached pool is replaced and the old one is retired: its idle connections are closed at
 * once and the pool itself is closed when its in-use connections are returned, or after
 * {@link #RETIRED_POOL_GRACE_MILLIS}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TomcatJdbcDataSourcePerTenantService
        implements RoutingDataSourceService, ApplicationListener<ContextRefreshedEvent>, DisposableBean {

    static final long RETIRED_POOL_GRACE_MILLIS = TimeUnit.SECONDS.toMillis(30);
    static final long RETIRED_POOL_POLL_MILLIS = 500;

    private final Map<Long, TenantPool> tenantPools = new ConcurrentHashMap<>();
    private final Map<String, Long> tenantConnectionIds = new ConcurrentHashMap<>();
    private final AtomicLong replacementPoolGeneration = new AtomicLong();
    private final ScheduledExecutorService retiredPoolCloser = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tenant-retired-pool-closer");
        thread.setDaemon(true);
        return thread;
    });

    @Qualifier("hikariTenantDataSource")
    private final DataSource tenantDataSource;
    private final TenantDetailsService tenantDetailsService;

    private final DataSourcePerTenantServiceFactory dataSourcePerTenantServiceFactory;

    private final Set<Long> tenantMoneyInitializingSet = Sets.newConcurrentHashSet();
    @Autowired(required = false)
    private MoneyHelperInitializationService moneyHelperInitializationService;

    @Override
    public DataSource retrieveDataSource() {
        // default to tenant database datasource
        DataSource actualDataSource = this.tenantDataSource;

        final FineractPlatformTenant tenant = ThreadLocalContextUtil.getTenant();
        if (tenant != null) {
            // if tenantConnection information available switch to the
            // appropriate datasource for that tenant.
            actualDataSource = dataSourceFor(tenant, false);
        }

        // TODO: This is definitely not the optimal place to initialize the rounding modes
        // Preferably nothing should use a statically referenced context and the initialization
        // should happen within the rounding mode retrieval
        if (moneyHelperInitializationService != null && tenant != null) {
            Long connectionId = tenant.getConnection().getConnectionId();
            if (!tenantMoneyInitializingSet.contains(connectionId) && !moneyHelperInitializationService.isTenantInitialized(tenant)) {
                // Double check to prevent visibility and race-condition issues
                synchronized (tenantMoneyInitializingSet) {
                    if (!tenantMoneyInitializingSet.contains(connectionId)) {
                        tenantMoneyInitializingSet.add(connectionId);
                        moneyHelperInitializationService.initializeTenantRoundingMode(tenant);
                        tenantMoneyInitializingSet.remove(connectionId);
                    }
                }
            }
        }

        return actualDataSource;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        final List<FineractPlatformTenant> allTenants = tenantDetailsService.findAllTenants();
        for (final FineractPlatformTenant tenant : allTenants) {
            initializeDataSourceConnection(tenant);
        }
    }

    @Override
    public void destroy() {
        retiredPoolCloser.shutdownNow();
    }

    private void initializeDataSourceConnection(FineractPlatformTenant tenant) {
        log.debug("Initializing database connection for {}", tenant.getName());
        dataSourceFor(tenant, true);
        log.debug("Database connection for {} initialized", tenant.getName());
    }

    private DataSource dataSourceFor(FineractPlatformTenant tenant, boolean verifyNewConnection) {
        final FineractPlatformTenantConnection tenantConnection = tenant.getConnection();
        final Long connectionId = tenantConnection.getConnectionId();
        final ConnectionFingerprint fingerprint = ConnectionFingerprint.of(tenantConnection);

        TenantPool pool = tenantPools.get(connectionId);
        if (pool == null || !pool.fingerprint().equals(fingerprint)) {
            pool = replacePool(tenant, tenantConnection, fingerprint, verifyNewConnection);
        }
        retirePreviousConnectionOf(tenant, connectionId);
        return pool.dataSource();
    }

    private TenantPool replacePool(FineractPlatformTenant tenant, FineractPlatformTenantConnection tenantConnection,
            ConnectionFingerprint fingerprint, boolean verifyNewConnection) {
        final List<DataSource> replaced = new ArrayList<>(1);
        final TenantPool pool = tenantPools.compute(tenantConnection.getConnectionId(), (connectionId, existing) -> {
            if (existing != null && existing.fingerprint().equals(fingerprint)) {
                return existing;
            }
            Long previousConnectionId = tenantConnectionIds.get(tenant.getTenantIdentifier());
            boolean replacesPool = existing != null || (previousConnectionId != null && !previousConnectionId.equals(connectionId));
            long generation = replacesPool ? replacementPoolGeneration.incrementAndGet() : 0;
            DataSource dataSource = dataSourcePerTenantServiceFactory.createNewDataSourceFor(tenant, tenantConnection, generation);
            if (verifyNewConnection) {
                verifyConnection(tenant, dataSource);
            }
            if (existing != null) {
                log.info("Connection details of tenant connection {} changed; replacing its connection pool", connectionId);
                replaced.add(existing.dataSource());
            }
            return new TenantPool(fingerprint, dataSource);
        });
        replaced.forEach(this::retire);
        return pool;
    }

    private void retirePreviousConnectionOf(FineractPlatformTenant tenant, Long connectionId) {
        final Long previousConnectionId = tenantConnectionIds.put(tenant.getTenantIdentifier(), connectionId);
        if (previousConnectionId == null || previousConnectionId.equals(connectionId)
                || tenantConnectionIds.containsValue(previousConnectionId)) {
            return;
        }
        final TenantPool previous = tenantPools.remove(previousConnectionId);
        if (previous != null) {
            log.info("Tenant {} moved from connection {} to {}; retiring the old connection pool", tenant.getTenantIdentifier(),
                    previousConnectionId, connectionId);
            retire(previous.dataSource());
        }
    }

    private void verifyConnection(FineractPlatformTenant tenant, DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            log.debug("Established database connection with URL {}", url);
        } catch (SQLException e) {
            log.error("Error while initializing database connection for {}", tenant.getName(), e);
        }
    }

    private void retire(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            HikariPoolMXBean pool = hikariDataSource.getHikariPoolMXBean();
            if (pool != null) {
                pool.softEvictConnections();
            }
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RETIRED_POOL_GRACE_MILLIS);
            retiredPoolCloser.execute(() -> closeWhenIdle(hikariDataSource, deadline));
        } else if (dataSource instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                log.warn("Error while closing retired tenant data source", e);
            }
        }
    }

    private void closeWhenIdle(HikariDataSource dataSource, long deadline) {
        try {
            HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
            int activeConnections = pool == null ? 0 : pool.getActiveConnections();
            if (activeConnections > 0 && System.nanoTime() - deadline < 0) {
                retiredPoolCloser.schedule(() -> closeWhenIdle(dataSource, deadline), RETIRED_POOL_POLL_MILLIS, TimeUnit.MILLISECONDS);
                return;
            }
            if (activeConnections > 0) {
                log.warn("Closing retired connection pool {} with {} connections still in use", dataSource.getPoolName(),
                        activeConnections);
            }
            dataSource.close();
            log.info("Closed retired connection pool {}", dataSource.getPoolName());
        } catch (RuntimeException e) {
            log.warn("Error while closing retired connection pool {}", dataSource.getPoolName(), e);
        }
    }

    private record TenantPool(ConnectionFingerprint fingerprint, DataSource dataSource) {
    }

    /**
     * The connection details a tenant pool is built from; a pool is replaced when any of them changes.
     */
    private record ConnectionFingerprint(String schemaServer, String schemaServerPort, String schemaConnectionParameters,
            String schemaUsername, String schemaPassword, String schemaName, String readOnlySchemaServer, String readOnlySchemaServerPort,
            String readOnlySchemaName, String readOnlySchemaUsername, String readOnlySchemaPassword,
            String readOnlySchemaConnectionParameters, int initialSize, int maxActive, long validationInterval, String masterPasswordHash) {

        static ConnectionFingerprint of(FineractPlatformTenantConnection connection) {
            return new ConnectionFingerprint(connection.getSchemaServer(), connection.getSchemaServerPort(),
                    connection.getSchemaConnectionParameters(), connection.getSchemaUsername(), connection.getSchemaPassword(),
                    connection.getSchemaName(), connection.getReadOnlySchemaServer(), connection.getReadOnlySchemaServerPort(),
                    connection.getReadOnlySchemaName(), connection.getReadOnlySchemaUsername(), connection.getReadOnlySchemaPassword(),
                    connection.getReadOnlySchemaConnectionParameters(), connection.getInitialSize(), connection.getMaxActive(),
                    connection.getValidationInterval(), connection.getMasterPasswordHash());
        }
    }
}
