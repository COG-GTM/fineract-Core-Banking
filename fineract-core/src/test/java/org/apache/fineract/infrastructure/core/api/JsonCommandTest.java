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
package org.apache.fineract.infrastructure.core.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Locale;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.junit.jupiter.api.Test;

class JsonCommandTest {

    @Test
    void extractsValuesAndDetectsChangedParameters() {
        JsonCommand command = JsonCommand.fromJsonElement(null,
                JsonParser.parseString("{\"name\":\"Alice\",\"count\":3,\"amount\":12.50,\"date\":\"2025-02-03\",\"locale\":\"en\","
                        + "\"dateFormat\":\"yyyy-MM-dd\"}"),
                new FromJsonHelper());

        assertThat(command.parameterExists("name")).isTrue();
        assertThat(command.parameterExists("missing")).isFalse();
        assertThat(command.stringValueOfParameterNamed("name")).isEqualTo("Alice");
        assertThat(command.integerValueOfParameterNamed("count")).isEqualTo(3);
        assertThat(command.bigDecimalValueOfParameterNamed("amount")).isEqualByComparingTo(BigDecimal.valueOf(12.5));
        assertThat(command.dateValueOfParameterNamed("date")).isEqualTo(LocalDate.of(2025, 2, 3));
        assertThat(command.isChangeInStringParameterNamed("name", "Bob")).isTrue();
        assertThat(command.isChangeInIntegerParameterNamed("count", 3)).isFalse();
    }

    @Test
    void extractsAllSupportedScalarAndStructuredValues() {
        JsonCommand command = JsonCommand.fromJsonElement(19L,
                JsonParser.parseString("{\"name\":\"Alice\",\"count\":3,\"amount\":12.50,\"enabled\":true,"
                        + "\"date\":\"2025-02-03\",\"monthDay\":\"02-03\",\"time\":\"11:22:33\",\"dateTime\":\"2025-02-03T11:22:33\","
                        + "\"tags\":[\"a\",\"b\"],\"items\":[{\"id\":1}],\"locale\":\"en\",\"dateFormat\":\"yyyy-MM-dd\","
                        + "\"monthDayFormat\":\"MM-dd\",\"timeFormat\":\"HH:mm:ss\"}"),
                new FromJsonHelper());

        assertThat(command.longValueOfParameterNamed("count")).isEqualTo(3L);
        assertThat(command.integerValueOfParameterNamed("count")).isEqualTo(3);
        assertThat(command.integerValueOfParameterNamed("count", Locale.ENGLISH)).isEqualTo(3);
        assertThat(command.integerValueSansLocaleOfParameterNamed("count")).isEqualTo(3);
        assertThat(command.bigDecimalValueOfParameterNamed("amount")).isEqualByComparingTo("12.5");
        assertThat(command.bigDecimalValueOfParameterNamed("amount", Locale.ENGLISH)).isEqualByComparingTo("12.5");
        assertThat(command.booleanObjectValueOfParameterNamed("enabled")).isTrue();
        assertThat(command.booleanPrimitiveValueOfParameterNamed("enabled")).isTrue();
        assertThat(command.localDateValueOfParameterNamed("date")).isEqualTo(LocalDate.of(2025, 2, 3));
        assertThat(command.localTimeValueOfParameterNamed("time")).isEqualTo(LocalTime.of(11, 22, 33));
        assertThat(command.extractMonthDayNamed("monthDay")).isEqualTo(java.time.MonthDay.of(2, 3));
        assertThat(command.arrayValueOfParameterNamed("tags")).containsExactly("a", "b");
        assertThat(command.arrayOfParameterNamed("tags")).hasSize(2);
        assertThat(command.jsonElement("items").isJsonArray()).isTrue();
        assertThat(command.jsonFragment("items")).contains("\"id\":1");
        assertThat(command.mapValueOfParameterNamed("{\"a\":\"b\"}")).containsEntry("a", "b");
        assertThat(command.mapObjectValueOfParameterNamed("{\"a\":1}")).containsEntry("a", 1.0);
        assertThat(command.dateFormat()).isEqualTo("yyyy-MM-dd");
        assertThat(command.locale()).isEqualTo("en");
        assertThat(command.json()).contains("\"name\":\"Alice\"");
    }

    @Test
    void detectsChangesIncludingNullAndDefaultZeroCases() {
        JsonCommand command = JsonCommand.fromJsonElement(null,
                JsonParser.parseString("{\"locale\":\"en\",\"dateFormat\":\"yyyy-MM-dd\",\"count\":0,\"amount\":0,\"enabled\":false,"
                        + "\"date\":\"2025-02-03\",\"tags\":[\"a\"]}"),
                new FromJsonHelper());

        assertThat(command.hasParameter("count")).isTrue();
        assertThat(command.hasParameter("missing")).isFalse();
        assertThat(command.hasParameterValue("count")).isTrue();
        assertThat(command.isChangeInLongParameterNamed("count", null)).isTrue();
        assertThat(command.isChangeInLongParameterNamed("missing", null)).isFalse();
        assertThat(command.isChangeInIntegerParameterNamedDefaultingZeroToNull("count", null)).isFalse();
        assertThat(command.isChangeInIntegerParameterNamedWithNullCheck("count", 1)).isTrue();
        assertThat(command.isChangeInBigDecimalParameterNamedDefaultingZeroToNull("amount", null)).isFalse();
        assertThat(command.isChangeInBigDecimalParameterNamedWithNullCheck("amount", BigDecimal.ONE)).isTrue();
        assertThat(command.isChangeInBooleanParameterNamed("enabled", true)).isTrue();
        assertThat(command.isChangeInBooleanParameterNamed("missing", null)).isFalse();
        assertThat(command.isChangeInDateParameterNamed("date", LocalDate.of(2025, 2, 2))).isTrue();
        assertThat(command.isChangeInLocalDateParameterNamed("date", LocalDate.of(2025, 2, 3))).isFalse();
        assertThat(command.isChangeInArrayParameterNamed("tags", new String[] { "a" })).isFalse();
        assertThat(command.isChangeInArrayParameterNamed("tags", new String[] { "b" })).isTrue();
        assertThat(command.stringValueOfParameterNamedAllowingNull("missing")).isNull();
        assertThat(command.integerValueOfParameterNamedDefaultToNullIfZero("count")).isNull();
        assertThat(command.bigDecimalValueOfParameterNamedDefaultToNullIfZero("amount")).isNull();
    }

    @Test
    void preservesCommandMetadataWhenCreatingFromExistingCommand() {
        FromJsonHelper helper = new FromJsonHelper();
        JsonCommand original = JsonCommand.from("{}", JsonParser.parseString("{\"name\":\"before\"}"), helper, "CLIENT", 10L, 11L, 12L, 13L,
                14L, 15L, "tx", "/clients/10", 16L, 17L, 18L, "job", null);
        JsonCommand existing = JsonCommand.fromExistingCommand(99L, "{\"name\":\"before\"}",
                JsonParser.parseString("{\"name\":\"before\"}"), helper, "CLIENT", 10L, 11L, "/clients/10", 16L, 17L, 18L, "job", null);
        JsonCommand copied = JsonCommand.fromExistingCommand(original, JsonParser.parseString("{\"name\":\"after\"}"));
        JsonCommand reassigned = JsonCommand.fromExistingCommand(original, JsonParser.parseString("{\"name\":\"after\"}"), 42L);

        assertThat(original.commandId()).isNull();
        assertThat(existing.commandId()).isEqualTo(99L);
        assertThat(existing.entityId()).isEqualTo(10L);
        assertThat(existing.subentityId()).isEqualTo(11L);
        assertThat(copied.json()).contains("after");
        assertThat(copied.entityId()).isEqualTo(10L);
        assertThat(reassigned.entityId()).isEqualTo(10L);
        assertThat(Arrays.asList(copied.entityId(), copied.subentityId())).containsExactly(10L, 11L);
    }
}
