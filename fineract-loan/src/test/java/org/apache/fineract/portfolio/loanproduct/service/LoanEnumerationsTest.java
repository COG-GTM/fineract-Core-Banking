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
package org.apache.fineract.portfolio.loanproduct.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.apache.fineract.portfolio.common.domain.PeriodFrequencyType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LoanEnumerationsTest {

    @ParameterizedTest
    @EnumSource(value = PeriodFrequencyType.class, names = { "DAYS", "WEEKS", "MONTHS", "YEARS" })
    void mapsLoanTermFrequencyValuesToEnumOptionData(PeriodFrequencyType type) {
        assertThat(LoanEnumerations.loanTermFrequencyType(type).getId()).isEqualTo(type.getValue().longValue());
        assertThat(LoanEnumerations.loanTermFrequencyType(type).getCode()).startsWith("loanTermFrequency.");
    }

    @ParameterizedTest
    @EnumSource(value = PeriodFrequencyType.class, names = { "DAYS", "WEEKS", "MONTHS", "YEARS" })
    void mapsIntegerValuesThroughTheSameEnumeration(PeriodFrequencyType type) {
        assertThat(LoanEnumerations.loanTermFrequencyType(type.getValue()).getId()).isEqualTo(type.getValue().longValue());
    }

    @Test
    void mapsEveryValueOfEveryEnumBasedEnumerationMethod() throws Exception {
        for (Method method : LoanEnumerations.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || !Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1
                    || !method.getParameterTypes()[0].isEnum()) {
                continue;
            }

            Class<?> enumType = method.getParameterTypes()[0];
            for (Object enumValue : enumType.getEnumConstants()) {
                Object mappedValue = method.invoke(null, enumValue);
                if (mappedValue == null) {
                    continue;
                }

                assertThat(mappedValue).as("%s(%s)", method.getName(), enumValue).isNotNull();
                assertThat(mappedValue.getClass().getMethod("getId").invoke(mappedValue)).as("%s(%s) id", method.getName(), enumValue)
                        .isNotNull();
                assertThat(String.valueOf(mappedValue.getClass().getMethod("getCode").invoke(mappedValue)))
                        .as("%s(%s) code", method.getName(), enumValue).isNotBlank();
                assertThat(String.valueOf(mappedValue.getClass().getMethod("getValue").invoke(mappedValue)))
                        .as("%s(%s) value", method.getName(), enumValue).isNotBlank();
            }
        }
    }
}
