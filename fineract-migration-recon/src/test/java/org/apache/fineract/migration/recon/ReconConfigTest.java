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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class ReconConfigTest {

    @Test
    void buildsEndpointsAndDefaults() {
        ReconConfig config = ReconConfig.of(properties());

        assertThat(config.source().jdbcUrl("fineract_default")).isEqualTo("jdbc:mariadb://source-host:3306/fineract_default");
        assertThat(config.target().jdbcUrl("fineract_default")).isEqualTo("jdbc:postgresql://target-host:5432/fineract_default");
        assertThat(config.source().registryDatabase()).isEqualTo("fineract_tenants");
        assertThat(config.sequenceTables()).isEqualTo(ReconConfig.DEFAULT_SEQUENCE_TABLES);
        assertThat(config.tenantFilter()).isEmpty();
    }

    @Test
    void missingCredentialsFailFast() {
        Properties properties = properties();
        properties.remove("target.password");

        assertThatThrownBy(() -> ReconConfig.of(properties)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("target.password");
    }

    private static Properties properties() {
        Properties properties = new Properties();
        properties.setProperty("source.engine", "mariadb");
        properties.setProperty("source.host", "source-host");
        properties.setProperty("source.port", "3306");
        properties.setProperty("source.username", "recon");
        properties.setProperty("source.password", "secret");
        properties.setProperty("target.engine", "postgresql");
        properties.setProperty("target.host", "target-host");
        properties.setProperty("target.port", "5432");
        properties.setProperty("target.username", "recon");
        properties.setProperty("target.password", "secret");
        return properties;
    }
}
