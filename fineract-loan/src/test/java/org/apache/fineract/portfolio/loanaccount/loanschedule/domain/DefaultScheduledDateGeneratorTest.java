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
package org.apache.fineract.portfolio.loanaccount.loanschedule.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.apache.fineract.portfolio.common.domain.PeriodFrequencyType;
import org.junit.jupiter.api.Test;

class DefaultScheduledDateGeneratorTest {

    private final DefaultScheduledDateGenerator generator = new DefaultScheduledDateGenerator();

    @Test
    void generatesRepaymentDatesForEachSupportedPeriod() {
        LocalDate start = LocalDate.of(2025, 1, 31);

        assertThat(generator.getRepaymentPeriodDate(PeriodFrequencyType.DAYS, 3, start)).isEqualTo(start.plusDays(3));
        assertThat(generator.getRepaymentPeriodDate(PeriodFrequencyType.WEEKS, 2, start)).isEqualTo(start.plusWeeks(2));
        assertThat(generator.getRepaymentPeriodDate(PeriodFrequencyType.MONTHS, 1, start)).isEqualTo(start.plusMonths(1));
        assertThat(generator.getRepaymentPeriodDate(PeriodFrequencyType.YEARS, 1, start)).isEqualTo(start.plusYears(1));
    }

    @Test
    void recognizesDatesThatFallOnARepaymentSchedule() {
        LocalDate start = LocalDate.of(2025, 1, 1);

        assertThat(generator.isDateFallsInSchedule(PeriodFrequencyType.WEEKS, 2, start, start.plusWeeks(4))).isTrue();
        assertThat(generator.isDateFallsInSchedule(PeriodFrequencyType.WEEKS, 2, start, start.plusWeeks(3))).isFalse();
    }
}
