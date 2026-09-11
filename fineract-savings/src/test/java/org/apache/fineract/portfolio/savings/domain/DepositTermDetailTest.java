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
package org.apache.fineract.portfolio.savings.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonParser;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Map;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.savings.SavingsPeriodFrequencyType;
import org.junit.jupiter.api.Test;

class DepositTermDetailTest {

    @Test
    void checksDepositTermBoundsAndMultiples() {
        DepositTermDetail detail = DepositTermDetail.createFrom(30, 90, SavingsPeriodFrequencyType.DAYS, SavingsPeriodFrequencyType.DAYS,
                30, SavingsPeriodFrequencyType.DAYS);

        assertThat(detail.isMinDepositTermGreaterThanMaxDepositTerm()).isFalse();
        assertThat(detail.isDepositBetweenMinAndMax(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 2))).isTrue();
        assertThat(detail.isDepositBetweenMinAndMax(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 15))).isFalse();
        assertThat(detail.isValidInMultiplesOfPeriod(60, SavingsPeriodFrequencyType.DAYS)).isTrue();
        assertThat(detail.isValidInMultiplesOfPeriod(45, SavingsPeriodFrequencyType.DAYS)).isFalse();
    }

    @Test
    void convertsPeriodsAndCopiesAllValues() {
        DepositTermDetail detail = DepositTermDetail.createFrom(2, 4, SavingsPeriodFrequencyType.WEEKS, SavingsPeriodFrequencyType.MONTHS,
                1, SavingsPeriodFrequencyType.YEARS);

        assertThat(detail.depositPeriod(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 15), SavingsPeriodFrequencyType.WEEKS))
                .isEqualTo(2);
        DepositTermDetail copy = detail.copy();
        assertThat(copy.minDepositTerm()).isEqualTo(2);
        assertThat(copy.maxDepositTerm()).isEqualTo(4);
        assertThat(copy.inMultiplesOfDepositTerm()).isEqualTo(1);
    }

    @Test
    void validatesEverySupportedPeriodFrequencyForMultiples() {
        for (SavingsPeriodFrequencyType type : SavingsPeriodFrequencyType.values()) {
            DepositTermDetail detail = DepositTermDetail.createFrom(2, 100, type, type, 2, type);
            assertThat(detail.isValidInMultiplesOfPeriod(4, type)).isTrue();
            assertThat(detail.depositPeriod(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 2), type)).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void returnsAllChangedFieldsFromUpdate() {
        DepositTermDetail detail = DepositTermDetail.createFrom(2, 4, SavingsPeriodFrequencyType.DAYS, SavingsPeriodFrequencyType.WEEKS, 2,
                SavingsPeriodFrequencyType.MONTHS);
        JsonCommand command = JsonCommand.fromJsonElement(null,
                JsonParser.parseString("{\"locale\":\"en\",\"minDepositTerm\":3,\"maxDepositTerm\":5,\"minDepositTermTypeId\":1,"
                        + "\"maxDepositTermTypeId\":2,\"inMultiplesOfDepositTerm\":1,\"inMultiplesOfDepositTermTypeId\":3}"),
                new FromJsonHelper());
        DataValidatorBuilder validator = new DataValidatorBuilder(new ArrayList<ApiParameterError>());

        Map<String, Object> changes = detail.update(command, validator);

        assertThat(changes).containsKeys("minDepositTerm", "maxDepositTerm", "minDepositTermTypeId", "maxDepositTermTypeId",
                "inMultiplesOfDepositTerm", "inMultiplesOfDepositTermTypeId");
        assertThat(detail.minDepositTerm()).isEqualTo(3);
        assertThat(detail.maxDepositTerm()).isEqualTo(5);
        assertThat(detail.minDepositTermType()).isEqualTo(1);
        assertThat(detail.maxDepositTermType()).isEqualTo(2);
        assertThat(detail.inMultiplesOfDepositTerm()).isEqualTo(1);
        assertThat(detail.inMultiplesOfDepositTermType()).isEqualTo(3);
    }
}
