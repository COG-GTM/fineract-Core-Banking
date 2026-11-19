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
package org.apache.fineract.infrastructure.core.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.contrib.jackson.JacksonJsonFormatter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TenantAwareJsonLayoutTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LoggerContext loggerContext = new LoggerContext();
    private final Logger logger = loggerContext.getLogger(TenantAwareJsonLayoutTest.class);
    private TenantAwareJsonLayout layout;
    private FineractPlatformTenant originalTenant;

    @BeforeEach
    void setUp() {
        originalTenant = ThreadLocalContextUtil.getTenant();
        layout = new TenantAwareJsonLayout();
        layout.setContext(loggerContext);
        layout.setJsonFormatter(new JacksonJsonFormatter());
        layout.setAppendLineSeparator(true);
        layout.start();
    }

    @AfterEach
    void tearDown() {
        layout.stop();
        ThreadLocalContextUtil.setTenant(originalTenant);
    }

    @Test
    void addsTenantIdentifierOfCurrentTenant() throws Exception {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "acme", "Acme", "UTC", null));

        JsonNode json = format(event("loan approved", Map.of("correlationId", "abc-123", "traceId", "4bf92f3577b34da6")));

        assertEquals("acme", json.get("tenantId").asText());
        assertEquals("loan approved", json.get("message").asText());
        assertEquals("INFO", json.get("level").asText());
        assertEquals("abc-123", json.get("mdc").get("correlationId").asText());
        assertEquals("4bf92f3577b34da6", json.get("mdc").get("traceId").asText());
    }

    @Test
    void marksEventsWithoutTenant() throws Exception {
        ThreadLocalContextUtil.setTenant(null);

        JsonNode json = format(event("startup", Map.of()));

        assertEquals("no-tenant", json.get("tenantId").asText());
    }

    @Test
    void writesExactlyOneLinePerEventIncludingStackTraces() throws Exception {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "acme", "Acme", "UTC", null));
        LoggingEvent event = new LoggingEvent(Logger.FQCN, logger, Level.ERROR, "multi\nline", new IllegalStateException("boom"), null);
        event.setMDCPropertyMap(Map.of());

        String line = layout.doLayout(event);

        assertTrue(line.endsWith("\n"));
        assertFalse(line.substring(0, line.length() - 1).contains("\n"));
        JsonNode json = objectMapper.readTree(line);
        assertEquals("multi\nline", json.get("message").asText());
        assertTrue(json.get("exception").asText().contains("IllegalStateException: boom"));
    }

    private LoggingEvent event(String message, Map<String, String> mdc) {
        LoggingEvent event = new LoggingEvent(Logger.FQCN, logger, Level.INFO, message, null, null);
        event.setMDCPropertyMap(mdc);
        return event;
    }

    private JsonNode format(LoggingEvent event) throws Exception {
        return objectMapper.readTree(layout.doLayout(event));
    }
}
