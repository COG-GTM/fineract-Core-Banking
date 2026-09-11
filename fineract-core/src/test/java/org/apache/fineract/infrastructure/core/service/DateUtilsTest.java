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
package org.apache.fineract.infrastructure.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DateUtilsTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2025, 3, 4);

    @BeforeEach
    void setBusinessDate() {
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, BUSINESS_DATE)));
    }

    @Test
    void comparesDateTimesWithNullOrderingAndOptionalTruncation() {
        LocalDateTime first = LocalDateTime.of(2025, 1, 1, 10, 15, 30);
        LocalDateTime second = LocalDateTime.of(2025, 1, 1, 10, 15, 45);

        assertThat(DateUtils.compare((LocalDateTime) null, (LocalDateTime) null)).isZero();
        assertThat(DateUtils.compare((LocalDateTime) null, first)).isNegative();
        assertThat(DateUtils.compare(first, (LocalDateTime) null)).isPositive();
        assertThat(DateUtils.compare(first, second, ChronoUnit.MINUTES)).isZero();
        assertThat(DateUtils.isBefore(first, second)).isTrue();
        assertThat(DateUtils.isAfter(second, first)).isTrue();
    }

    @Test
    void parsesFormatsAndCalculatesDateRanges() {
        LocalDate date = DateUtils.parseLocalDate("2025-03-04");
        assertThat(date).isEqualTo(LocalDate.of(2025, 3, 4));
        assertThat(DateUtils.format(date)).isEqualTo("2025-03-04");
        assertThat(DateUtils.getExactDifferenceInDays(date, date.plusDays(9))).isEqualTo(9);
        assertThat(DateUtils.isDateInRangeFromInclusiveToExclusive(date, date.plusDays(1), date)).isTrue();
        assertThat(DateUtils.isDateInRangeFromInclusiveToExclusive(date, date.plusDays(1), date.plusDays(1))).isFalse();
        assertThat(DateUtils.isAfterInclusive(date.plusDays(1), date)).isTrue();
    }

    @Test
    void coversOffsetDateTimeComparisonsAndNullOrdering() {
        OffsetDateTime first = OffsetDateTime.of(2025, 1, 1, 10, 15, 30, 0, ZoneOffset.UTC);
        OffsetDateTime second = first.plusSeconds(15);

        assertThat(DateUtils.compare((OffsetDateTime) null, (OffsetDateTime) null)).isZero();
        assertThat(DateUtils.compareWithNullsLast((OffsetDateTime) null, second)).isPositive();
        assertThat(DateUtils.compareWithNullsLast(Optional.empty(), Optional.of(second))).isPositive();
        assertThat(DateUtils.compare(first, second, ChronoUnit.MINUTES)).isZero();
        assertThat(DateUtils.compare(first, second, ChronoUnit.MINUTES, true)).isZero();
        assertThat(DateUtils.isEqual(first, first)).isTrue();
        assertThat(DateUtils.isBefore(first, second)).isTrue();
        assertThat(DateUtils.isAfter(second, first, ChronoUnit.SECONDS)).isTrue();
        assertThat(DateUtils.isEqual((OffsetDateTime) null, (OffsetDateTime) null)).isTrue();
    }

    @Test
    void coversBusinessDateAndLocalDateComparisons() {
        LocalDate before = BUSINESS_DATE.minusDays(1);
        LocalDate after = BUSINESS_DATE.plusDays(1);

        assertThat(DateUtils.getBusinessLocalDate()).isEqualTo(BUSINESS_DATE);
        assertThat(DateUtils.isEqualBusinessDate(BUSINESS_DATE)).isTrue();
        assertThat(DateUtils.isBeforeBusinessDate(before)).isTrue();
        assertThat(DateUtils.isAfterBusinessDate(after)).isTrue();
        assertThat(DateUtils.isDateInTheFuture(after)).isTrue();
        assertThat(DateUtils.compare((LocalDate) null, before)).isNegative();
        assertThat(DateUtils.compareWithNullsLast((LocalDate) null, before)).isPositive();
        assertThat(DateUtils.compare(null, before, false)).isPositive();
        assertThat(DateUtils.isEqual((LocalDate) null, (LocalDate) null)).isTrue();
        assertThat(DateUtils.isBefore(null, before)).isTrue();
        assertThat(DateUtils.isAfter(after, null)).isTrue();
        assertThat(DateUtils.isAfterInclusive(BUSINESS_DATE, BUSINESS_DATE)).isTrue();
        assertThat(DateUtils.getDifference(BUSINESS_DATE, after, ChronoUnit.DAYS)).isEqualTo(1);
        assertThat(DateUtils.getExactDifference(BUSINESS_DATE, after, ChronoUnit.DAYS)).isEqualTo(1);
        assertThat(DateUtils.getDifferenceInDays(BUSINESS_DATE, after)).isEqualTo(1);
        assertThat(DateUtils.getExactDifferenceInDays(BUSINESS_DATE, after)).isEqualTo(1);
        assertThat(DateUtils.minusDays(BUSINESS_DATE, 2)).isEqualTo(BUSINESS_DATE.minusDays(2));
        assertThat(DateUtils.minusDays(null, 2)).isNull();
        assertThat(DateUtils.isDateWithinRange(BUSINESS_DATE, before, after)).isTrue();
        assertThat(DateUtils.isDateInRangeInclusive(BUSINESS_DATE, before, after)).isTrue();
        assertThat(DateUtils.isDateInRangeExclusive(BUSINESS_DATE, before, after)).isTrue();
        assertThat(DateUtils.isDateInRangeFromExclusiveToInclusive(after, before, after)).isTrue();
        assertThat(DateUtils.min(after, before)).isEqualTo(before);
    }

    @Test
    void coversLocalizedParsingFormattingAndDateTimeConversion() {
        LocalDate date = LocalDate.of(2025, 3, 4);
        LocalDateTime dateTime = LocalDateTime.of(2025, 3, 4, 11, 22, 33);

        assertThat(DateUtils.parseLocalDate(null)).isNull();
        assertThat(DateUtils.parseLocalDate("04/03/2025", "dd/MM/uuuu", Locale.US)).isEqualTo(date);
        assertThat(DateUtils.toLocalDate("en", "2025-03-04 11:22:33", "yyyy-MM-dd")).isEqualTo(date);
        assertThat(DateUtils.format(date, "dd MMM uuuu", Locale.US)).isEqualTo("04 Mar 2025");
        assertThat(DateUtils.format((LocalDate) null)).isNull();
        assertThat(DateUtils.format(dateTime)).isEqualTo("2025-03-04 11:22:33");
        assertThat(DateUtils.format(dateTime, "HH:mm", Locale.US)).isEqualTo("11:22");
        assertThat(DateUtils.format((LocalDateTime) null)).isNull();
        assertThat(DateUtils.convertDateTimeStringToLocalDateTime("2025-03-04", "yyyy-MM-dd", "en", LocalTime.NOON))
                .isEqualTo(LocalDateTime.of(date, LocalTime.NOON));
        assertThat(DateUtils.convertDateTimeStringToLocalDateTime("2025-03-04 11:22:33", "yyyy-MM-dd HH:mm:ss", null, LocalTime.NOON))
                .isEqualTo(dateTime);
    }
}
