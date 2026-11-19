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

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenantConnection;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.core.service.tenant.TenantDetailsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.event.ContextRefreshedEvent;

class TomcatJdbcDataSourcePerTenantServiceTest {

    private static final long CLOSE_TIMEOUT_MILLIS = 5_000;

    private final DataSource defaultDataSource = mock(DataSource.class);
    private final TenantDetailsService tenantDetailsService = mock(TenantDetailsService.class);
    private final DataSourcePerTenantServiceFactory factory = mock(DataSourcePerTenantServiceFactory.class);
    private final List<HikariDataSource> createdPools = new ArrayList<>();
    private final List<HikariPoolMXBean> createdPoolBeans = new ArrayList<>();

    private TomcatJdbcDataSourcePerTenantService underTest;

    @BeforeEach
    void setUp() {
        when(factory.createNewDataSourceFor(any(), any(), anyLong())).thenAnswer(invocation -> newPool());
        underTest = new TomcatJdbcDataSourcePerTenantService(defaultDataSource, tenantDetailsService, factory);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
        underTest.destroy();
    }

    @Test
    void returnsDefaultDataSourceWithoutTenant() {
        ThreadLocalContextUtil.clearTenant();

        assertSame(defaultDataSource, underTest.retrieveDataSource());
    }

    @Test
    void reusesPoolWhileConnectionIsUnchanged() {
        DataSource first = retrieveFor(tenant("default", connection(1L, "db-a")));
        DataSource second = retrieveFor(tenant("default", connection(1L, "db-a")));

        assertSame(first, second);
        verify(factory, times(1)).createNewDataSourceFor(any(), any(), anyLong());
        verify(createdPools.get(0), after(200).never()).close();
    }

    @Test
    void replacesPoolWhenConnectionRowChangesInPlace() {
        DataSource oldPool = retrieveFor(tenant("default", connection(1L, "db-a")));

        FineractPlatformTenantConnection changed = connection(1L, "db-b");
        DataSource newPool = retrieveFor(tenant("default", changed));

        assertNotSame(oldPool, newPool);
        verify(factory).createNewDataSourceFor(any(), eq(changed), eq(1L));
        verify(createdPoolBeans.get(0)).softEvictConnections();
        verify(createdPools.get(0), timeout(CLOSE_TIMEOUT_MILLIS)).close();
        verify(createdPools.get(1), never()).close();
        assertSame(newPool, retrieveFor(tenant("default", connection(1L, "db-b"))));
    }

    @Test
    void replacesPoolWhenCredentialsChange() {
        DataSource oldPool = retrieveFor(tenant("default", connection(1L, "db-a", "user", "secret")));
        DataSource newPool = retrieveFor(tenant("default", connection(1L, "db-a", "user", "rotated")));

        assertNotSame(oldPool, newPool);
        verify(createdPools.get(0), timeout(CLOSE_TIMEOUT_MILLIS)).close();
    }

    @Test
    void retiresOldPoolWhenTenantMovesToNewConnectionRow() {
        DataSource oldPool = retrieveFor(tenant("default", connection(1L, "db-a")));
        DataSource newPool = retrieveFor(tenant("default", connection(2L, "db-b")));

        assertNotSame(oldPool, newPool);
        verify(factory).createNewDataSourceFor(any(), any(), eq(1L));
        verify(createdPools.get(0), timeout(CLOSE_TIMEOUT_MILLIS)).close();
        verify(createdPools.get(1), never()).close();
    }

    @Test
    void keepsSharedConnectionPoolWhileAnotherTenantStillUsesIt() {
        DataSource shared = retrieveFor(tenant("first", connection(1L, "db-a")));
        retrieveFor(tenant("second", connection(1L, "db-a")));

        retrieveFor(tenant("first", connection(2L, "db-b")));

        verify(createdPools.get(0), after(200).never()).close();
        assertSame(shared, retrieveFor(tenant("second", connection(1L, "db-a"))));
    }

    @Test
    void staleRequestAfterInPlaceChangeDoesNotRevertPool() {
        FineractPlatformTenant before = tenant("default", connection(1L, "db-a"));
        FineractPlatformTenant after = tenant("default", connection(1L, "db-b"));
        retrieveFor(before);
        when(tenantDetailsService.loadTenantById("default")).thenReturn(after);
        DataSource newPool = retrieveFor(after);

        assertSame(newPool, retrieveFor(before));
        verify(factory, times(2)).createNewDataSourceFor(any(), any(), anyLong());
        verify(createdPools.get(0), timeout(CLOSE_TIMEOUT_MILLIS)).close();
        verify(createdPools.get(1), after(200).never()).close();
    }

    @Test
    void staleRequestAfterMoveDoesNotRevertPool() {
        FineractPlatformTenant before = tenant("default", connection(1L, "db-a"));
        FineractPlatformTenant after = tenant("default", connection(2L, "db-b"));
        retrieveFor(before);
        when(tenantDetailsService.loadTenantById("default")).thenReturn(after);
        DataSource newPool = retrieveFor(after);

        assertSame(newPool, retrieveFor(before));
        verify(factory, times(2)).createNewDataSourceFor(any(), any(), anyLong());
        verify(createdPools.get(0), timeout(CLOSE_TIMEOUT_MILLIS)).close();
        verify(createdPools.get(1), after(200).never()).close();
    }

    @Test
    void movingToAnExistingSharedPoolRetiresTheOldOne() {
        retrieveFor(tenant("first", connection(1L, "db-a")));
        DataSource shared = retrieveFor(tenant("second", connection(2L, "db-b")));
        FineractPlatformTenant moved = tenant("first", connection(2L, "db-b"));
        when(tenantDetailsService.loadTenantById("first")).thenReturn(moved);

        assertSame(shared, retrieveFor(moved));
        verify(createdPools.get(0), timeout(CLOSE_TIMEOUT_MILLIS)).close();
        verify(createdPools.get(1), never()).close();
    }

    @Test
    void waitsForInUseConnectionsBeforeClosingRetiredPool() {
        retrieveFor(tenant("default", connection(1L, "db-a")));
        HikariPoolMXBean oldPoolBean = createdPoolBeans.get(0);
        AtomicInteger activeConnections = new AtomicInteger(1);
        when(oldPoolBean.getActiveConnections()).thenAnswer(invocation -> activeConnections.get());

        retrieveFor(tenant("default", connection(1L, "db-b")));

        verify(oldPoolBean).softEvictConnections();
        verify(createdPools.get(0), after(1_000).never()).close();

        activeConnections.set(0);
        verify(createdPools.get(0), timeout(CLOSE_TIMEOUT_MILLIS)).close();
    }

    @Test
    void doesNotLeakPoolsUnderRepeatedChanges() {
        int changes = 25;
        for (int i = 0; i <= changes; i++) {
            retrieveFor(tenant("default", connection(1L, "db-" + i)));
        }

        verify(factory, times(changes + 1)).createNewDataSourceFor(any(), any(), anyLong());
        for (int i = 0; i < changes; i++) {
            verify(createdPools.get(i), timeout(CLOSE_TIMEOUT_MILLIS)).close();
        }
        verify(createdPools.get(changes), never()).close();
    }

    @Test
    void startupInitializationIsReusedByRequests() throws SQLException {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metaData);
        doAnswer(invocation -> {
            HikariDataSource pool = newPool();
            when(pool.getConnection()).thenReturn(connection);
            return pool;
        }).when(factory).createNewDataSourceFor(any(), any(), anyLong());
        FineractPlatformTenant tenant = tenant("default", connection(1L, "db-a"));
        when(tenantDetailsService.findAllTenants()).thenReturn(List.of(tenant));

        underTest.onApplicationEvent(mock(ContextRefreshedEvent.class));

        assertSame(createdPools.get(0), retrieveFor(tenant("default", connection(1L, "db-a"))));
        verify(factory).createNewDataSourceFor(any(), any(), eq(0L));
        verify(connection).close();
    }

    private DataSource retrieveFor(FineractPlatformTenant tenant) {
        ThreadLocalContextUtil.setTenant(tenant);
        return underTest.retrieveDataSource();
    }

    private HikariDataSource newPool() {
        HikariDataSource pool = mock(HikariDataSource.class);
        HikariPoolMXBean poolBean = mock(HikariPoolMXBean.class);
        when(pool.getHikariPoolMXBean()).thenReturn(poolBean);
        when(pool.getPoolName()).thenReturn("pool-" + createdPools.size());
        createdPools.add(pool);
        createdPoolBeans.add(poolBean);
        return pool;
    }

    private static FineractPlatformTenant tenant(String identifier, FineractPlatformTenantConnection connection) {
        return new FineractPlatformTenant(1L, identifier, identifier, "UTC", connection);
    }

    private static FineractPlatformTenantConnection connection(Long connectionId, String server) {
        return connection(connectionId, server, "user", "secret");
    }

    private static FineractPlatformTenantConnection connection(Long connectionId, String server, String username, String password) {
        return FineractPlatformTenantConnection.builder().connectionId(connectionId).schemaServer(server).schemaServerPort("5432")
                .schemaName("fineract_default").schemaUsername(username).schemaPassword(password).initialSize(1).maxActive(10)
                .validationInterval(30_000).masterPasswordHash("hash").build();
    }
}
