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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the tenant list from a {@code fineract_tenants} registry database.
 *
 * Only connection metadata is read. The registry's {@code schema_password} is AES-encrypted with the tenant master
 * password, so the reconciliation takes its credentials from configuration instead of decrypting anything.
 */
public final class TenantRegistry {

    private static final String TENANTS_SQL = """
            select t.identifier, c.schema_server, c.schema_server_port, c.schema_name
            from tenants t
            join tenant_server_connections c on c.id = t.oltp_id
            order by t.identifier
            """;

    private TenantRegistry() {}

    public static List<TenantConnection> tenants(Connection registryConnection) throws SQLException {
        List<TenantConnection> tenants = new ArrayList<>();
        try (Statement statement = registryConnection.createStatement(); ResultSet resultSet = statement.executeQuery(TENANTS_SQL)) {
            while (resultSet.next()) {
                tenants.add(new TenantConnection(resultSet.getString(1), resultSet.getString(2), resultSet.getString(3),
                        resultSet.getString(4)));
            }
        }
        return tenants;
    }
}
