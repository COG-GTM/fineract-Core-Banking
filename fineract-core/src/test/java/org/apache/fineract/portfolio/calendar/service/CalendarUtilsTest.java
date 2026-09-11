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
package org.apache.fineract.portfolio.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.portfolio.common.domain.PeriodFrequencyType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CalendarUtilsTest {

    private static final String BIWEEKLY_MONDAY = "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO";
    private static final LocalDate SEED = LocalDate.of(2025, 1, 6);

    @AfterEach
    void clearTenant() {
        ThreadLocalContextUtil.clearTenant();
    }

    @Test
    void parsesRecurrenceAndComparesItsFrequencyAndInterval() {
        assertThat(CalendarUtils.getICalRecur(BIWEEKLY_MONDAY)).isNotNull();
        assertThat(CalendarUtils.isFrequencySame(BIWEEKLY_MONDAY, "FREQ=WEEKLY;INTERVAL=1;BYDAY=MO")).isTrue();
        assertThat(CalendarUtils.isIntervalSame(BIWEEKLY_MONDAY, "FREQ=WEEKLY;INTERVAL=2;BYDAY=FR")).isTrue();
        assertThat(CalendarUtils.getInterval(BIWEEKLY_MONDAY)).isEqualTo(2);
    }

    @Test
    void createsCalendarTypeListsAndRecurringDates() {
        assertThat(CalendarUtils.createIntegerListFromQueryParameter("collection,audit")).containsExactly(1, 3);
        assertThat(CalendarUtils.createIntegerListFromQueryParameter("all")).containsExactly(1, 2, 3, 4);
        assertThat(CalendarUtils.getSqlCalendarTypeOptionsInString(List.of(1, 3, 4))).isEqualTo("1,3,4");
        assertThat(CalendarUtils.isFrequencySame(BIWEEKLY_MONDAY, "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO")).isTrue();
    }

    @Test
    void evaluatesRRuleFrequencyAndDayHelpers() {
        assertThat(CalendarUtils.getMeetingPeriodFrequencyType("FREQ=DAILY;INTERVAL=1")).isEqualTo(PeriodFrequencyType.DAYS);
        assertThat(CalendarUtils.getMeetingPeriodFrequencyType(BIWEEKLY_MONDAY)).isEqualTo(PeriodFrequencyType.WEEKS);
        assertThat(CalendarUtils.getMeetingPeriodFrequencyType("FREQ=MONTHLY;BYMONTHDAY=15")).isEqualTo(PeriodFrequencyType.MONTHS);
        assertThat(CalendarUtils.getMeetingPeriodFrequencyType("FREQ=YEARLY;BYMONTH=1;BYMONTHDAY=15")).isEqualTo(PeriodFrequencyType.YEARS);
        assertThat(CalendarUtils.getMeetingFrequencyFromPeriodFrequencyType(PeriodFrequencyType.DAYS)).isEqualTo("DAILY");
        assertThat(CalendarUtils.getMeetingFrequencyFromPeriodFrequencyType(PeriodFrequencyType.WEEKS)).isEqualTo("WEEKLY");
        assertThat(CalendarUtils.getMeetingFrequencyFromPeriodFrequencyType(PeriodFrequencyType.MONTHS)).isEqualTo("MONTHLY");
        assertThat(CalendarUtils.getMeetingFrequencyFromPeriodFrequencyType(PeriodFrequencyType.YEARS)).isEqualTo("YEARLY");
        assertThat(CalendarUtils.getInterval(BIWEEKLY_MONDAY)).isEqualTo(2);
        assertThat(CalendarUtils.getFrequency(BIWEEKLY_MONDAY).name()).isEqualTo("WEEKLY");
        assertThat(CalendarUtils.getRepeatsOnDay(BIWEEKLY_MONDAY).name()).isEqualTo("MO");
        assertThat(CalendarUtils.getRepeatsOnNthDayOfMonth("FREQ=MONTHLY;BYMONTHDAY=15").name()).isEqualTo("ONDAY");
        assertThat(CalendarUtils.getMonthOnDay("FREQ=MONTHLY;BYMONTHDAY=15")).isEqualTo(15);
        assertThat(CalendarUtils.getRRuleReadable(SEED, BIWEEKLY_MONDAY)).contains("Every 2 weeks");
    }

    @Test
    void computesRecurringDatesAndValidityWithTenantTimezone() {
        FineractPlatformTenant tenant = Mockito.mock(FineractPlatformTenant.class);
        Mockito.when(tenant.getTimezoneId()).thenReturn("UTC");
        ThreadLocalContextUtil.setTenant(tenant);

        LocalDate next = CalendarUtils.getNextRecurringDate("FREQ=WEEKLY;BYDAY=MO", SEED, SEED.plusDays(1));
        assertThat(next).isEqualTo(SEED.plusWeeks(1));
        LocalDateTime nextDateTime = CalendarUtils.getNextRecurringDate("FREQ=DAILY;INTERVAL=2", SEED.atStartOfDay(),
                SEED.plusDays(1).atStartOfDay());
        assertThat(nextDateTime.toLocalDate()).isEqualTo(SEED.plusDays(2));

        assertThat(CalendarUtils.getRecurringDates(BIWEEKLY_MONDAY, SEED, SEED, SEED.plusWeeks(7), 5, false, 0)).containsExactly(SEED,
                SEED.plusWeeks(2), SEED.plusWeeks(4), SEED.plusWeeks(6));
        assertThat(CalendarUtils.isValidRecurringDate(BIWEEKLY_MONDAY, SEED, SEED.plusWeeks(2))).isTrue();
        assertThat(CalendarUtils.isValidRecurringDate(BIWEEKLY_MONDAY, SEED, SEED.plusDays(1))).isFalse();
        assertThat(CalendarUtils.adjustRecurringDate(LocalDate.of(2025, 2, 1), 2)).isEqualTo(LocalDate.of(2025, 2, 3));
        assertThat(CalendarUtils.adjustRecurringDate(LocalDate.of(2025, 2, 2), 2)).isEqualTo(LocalDate.of(2025, 2, 2));
    }
}
