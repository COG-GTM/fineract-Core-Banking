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
package org.apache.fineract.infrastructure.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.awspring.cloud.autoconfigure.core.CredentialsProperties;
import io.awspring.cloud.autoconfigure.core.Profile;
import org.junit.jupiter.api.Test;

class AwsCredentialsPropertiesConfigTest {

    @Test
    void blankProfileNameIsDropped() {
        CredentialsProperties properties = properties("", "");

        new AwsCredentialsPropertiesConfig().postProcessAfterInitialization(properties, "credentials");

        assertNull(properties.getProfile().getName());
        assertNull(properties.getProfile().getPath());
    }

    @Test
    void blankPathFallsBackToDefaultProfileFile() {
        CredentialsProperties properties = properties("default", " ");

        AwsCredentialsPropertiesConfig.resetBlankProfile(properties);

        assertEquals("default", properties.getProfile().getName());
        assertNull(properties.getProfile().getPath());
    }

    @Test
    void configuredProfileIsKept() {
        CredentialsProperties properties = properties("default", "/etc/aws/credentials");

        AwsCredentialsPropertiesConfig.resetBlankProfile(properties);

        assertEquals("default", properties.getProfile().getName());
        assertEquals("/etc/aws/credentials", properties.getProfile().getPath());
    }

    private static CredentialsProperties properties(String name, String path) {
        Profile profile = new Profile();
        profile.setName(name);
        profile.setPath(path);
        CredentialsProperties properties = new CredentialsProperties();
        properties.setProfile(profile);
        return properties;
    }
}
