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

import io.awspring.cloud.autoconfigure.core.CredentialsProperties;
import io.awspring.cloud.autoconfigure.core.Profile;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * application.properties always defines spring.cloud.aws.credentials.profile.name/path, defaulting to blank. Spring
 * Cloud AWS treats a blank (non-null) profile name as configured and a blank path as the working directory, which fails
 * startup. Blank values are reset to unset (null) so the default credentials chain (e.g. the ECS task role) is used.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(CredentialsProperties.class)
public class AwsCredentialsPropertiesConfig implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof CredentialsProperties credentialsProperties) {
            resetBlankProfile(credentialsProperties);
        }
        return bean;
    }

    static void resetBlankProfile(CredentialsProperties credentialsProperties) {
        Profile profile = credentialsProperties.getProfile();
        if (profile == null) {
            return;
        }
        if (!StringUtils.hasText(profile.getName())) {
            credentialsProperties.setProfile(new Profile());
        } else if (!StringUtils.hasText(profile.getPath())) {
            Profile namedProfile = new Profile();
            namedProfile.setName(profile.getName());
            credentialsProperties.setProfile(namedProfile);
        }
    }
}
