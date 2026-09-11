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

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class MathUtilTest {

    private static final MonetaryCurrency CURRENCY = new MonetaryCurrency("USD", 2, null);
    private static MockedStatic<MoneyHelper> moneyHelper;

    @BeforeAll
    static void setUpMoneyContext() {
        moneyHelper = Mockito.mockStatic(MoneyHelper.class);
        moneyHelper.when(MoneyHelper::getMathContext).thenReturn(MathContext.DECIMAL64);
        moneyHelper.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_EVEN);
    }

    @AfterAll
    static void tearDownMoneyContext() {
        moneyHelper.close();
    }

    @Test
    void nullAndZeroConversionsAreNullSafe() {
        assertThat(MathUtil.nullToZero((Long) null)).isZero();
        assertThat(MathUtil.nullToZero((Integer) null)).isZero();
        assertThat(MathUtil.zeroToNull(0L)).isNull();
        assertThat(MathUtil.zeroToNull(4L)).isEqualTo(4L);
        assertThat(MathUtil.negativeToZero(-1L)).isZero();
        assertThat(MathUtil.negativeToZero(3L)).isEqualTo(3L);
    }

    @Test
    void comparisonsTreatNullAsZero() {
        assertThat(MathUtil.isEqualTo(null, 0L)).isTrue();
        assertThat(MathUtil.isGreaterThan(4L, 2L)).isTrue();
        assertThat(MathUtil.isLessThan(-1L, 0L)).isTrue();
        assertThat(MathUtil.isGreaterThanOrEqualTo(null, 0L)).isTrue();
        assertThat(MathUtil.isLessThanOrEqualZero((Long) null)).isTrue();
    }

    @Test
    void arithmeticHandlesNullsAndPreventsNegativeSubtractToZero() {
        assertThat(MathUtil.add(2L, null)).isEqualTo(2L);
        assertThat(MathUtil.add(1L, 2L, 3L)).isEqualTo(6L);
        assertThat(MathUtil.subtract(8L, 3L, 2L)).isEqualTo(3L);
        assertThat(MathUtil.subtractToZero(2L, 5L)).isZero();
        assertThat(MathUtil.negate(4L)).isEqualTo(-4L);
        assertThat(MathUtil.add(new BigDecimal("1.25"), new BigDecimal("2.75"), MathContext.DECIMAL64)).isEqualByComparingTo("4.00");
        assertThat(MathUtil.percentageOf(new BigDecimal("200"), new BigDecimal("12.5"), MathContext.DECIMAL64))
                .isEqualByComparingTo("25.0");
    }

    @Test
    void decimalHelpersNormalizeAndFormatValues() {
        assertThat(MathUtil.zeroToNull(new BigDecimal("0.00"))).isNull();
        assertThat(MathUtil.negativeToZero(new BigDecimal("-0.1"))).isZero();
        assertThat(MathUtil.stripTrailingZeros(new BigDecimal("12.5000"))).isEqualByComparingTo("12.5");
        assertThat(MathUtil.formatToSql(new BigDecimal("12.50"))).isEqualTo("12.50");
    }

    @Test
    void coversLongBoundariesAndMinima() {
        assertThat(MathUtil.nullToDefault(null, 7L)).isEqualTo(7L);
        assertThat(MathUtil.isEmpty((Long) null)).isTrue();
        assertThat(MathUtil.isEmpty(0L)).isTrue();
        assertThat(MathUtil.isGreaterThanZero((Long) null)).isFalse();
        assertThat(MathUtil.isLessThanZero((Long) null)).isFalse();
        assertThat(MathUtil.isZero(0L)).isTrue();
        assertThat(MathUtil.isLessThanOrEqualZero(java.lang.Long.valueOf(-1L))).isTrue();
        assertThat(MathUtil.abs(-4L)).isEqualTo(4L);
        assertThat(MathUtil.min(null, 4L, true)).isEqualTo(4L);
        assertThat(MathUtil.min(null, 4L, false)).isNull();
        assertThat(MathUtil.min(true, 8L, 3L, null, 5L)).isEqualTo(3L);
        assertThat(MathUtil.add((Long[]) new Long[] { 1L, 2L, 3L })).isEqualTo(6L);
        assertThat(MathUtil.subtract(null, 2L)).isNull();
        assertThat(MathUtil.subtract(10L, (Long[]) new Long[] { 2L, 1L, 3L })).isEqualTo(4L);
        assertThat(MathUtil.subtractToZero(10L, 3L, 9L)).isZero();
        assertThat(MathUtil.negate(0L)).isZero();
    }

    @Test
    void coversIntegerAndBigDecimalBoundariesAndMinima() {
        assertThat(MathUtil.isEqualTo((Integer) null, 0)).isTrue();
        assertThat(MathUtil.nullToDefault((Integer) null, 4)).isEqualTo(4);
        assertThat(MathUtil.isEmpty((BigDecimal) null)).isTrue();
        assertThat(MathUtil.isGreaterThanZero(new BigDecimal("1.0"))).isTrue();
        assertThat(MathUtil.isLessThanZero(new BigDecimal("-1.0"))).isTrue();
        assertThat(MathUtil.isZero(BigDecimal.ZERO)).isTrue();
        assertThat(MathUtil.isLessThanOrEqualTo(new BigDecimal("2"), new BigDecimal("2"))).isTrue();
        assertThat(MathUtil.isGreaterThanOrEqualTo(null, BigDecimal.ZERO)).isTrue();
        assertThat(MathUtil.isLessThanOrEqualZero(new BigDecimal("-1"))).isTrue();
        assertThat(MathUtil.abs(new BigDecimal("-4.20"))).isEqualByComparingTo("4.20");
        assertThat(MathUtil.min(new BigDecimal("2"), new BigDecimal("5"), true)).isEqualByComparingTo("2");
        assertThat(MathUtil.min(false, new BigDecimal("2"), new BigDecimal("5"), null)).isNull();
        assertThat(MathUtil.add(MathContext.DECIMAL64, new BigDecimal("1"), new BigDecimal("2"))).isEqualByComparingTo("3");
        assertThat(MathUtil.subtract(new BigDecimal("10"), new BigDecimal("2"), MathContext.DECIMAL64)).isEqualByComparingTo("8");
        assertThat(MathUtil.subtractToZero(new BigDecimal("2"), new BigDecimal("5"))).isZero();
        assertThat(MathUtil.normalizeAmount(new BigDecimal("12.345"), CURRENCY)).isEqualByComparingTo("12.34");
        assertThat(MathUtil.negate(new BigDecimal("2"), MathContext.DECIMAL64)).isEqualByComparingTo("-2");
        assertThat(MathUtil.formatToSql(null)).isNull();
        assertThat(MathUtil.toMoney(new BigDecimal("2.50"), CURRENCY).getAmount()).isEqualByComparingTo("2.50");
    }

    @Test
    void coversMoneyOperationsAndComparisons() {
        Money one = Money.of(CURRENCY, new BigDecimal("1.00"));
        Money two = Money.of(CURRENCY, new BigDecimal("2.00"));
        Money negative = Money.of(CURRENCY, new BigDecimal("-1.00"));

        assertThat(MathUtil.toBigDecimal(one)).isEqualByComparingTo("1.00");
        assertThat(MathUtil.toBigDecimal(null)).isNull();
        assertThat(MathUtil.nullToZero((Money) null, CURRENCY).isZero()).isTrue();
        assertThat(MathUtil.nullToZero((Money) null, CURRENCY, MathContext.DECIMAL64).isZero()).isTrue();
        assertThat(MathUtil.nullToDefault(null, one)).isSameAs(one);
        assertThat(MathUtil.zeroToNull(one)).isSameAs(one);
        assertThat(MathUtil.zeroToNull(Money.zero(CURRENCY))).isNull();
        assertThat(MathUtil.negativeToZero(negative).isZero()).isTrue();
        assertThat(MathUtil.negativeToZero(negative, MathContext.DECIMAL64).isZero()).isTrue();
        assertThat(MathUtil.isEmpty((Money) null)).isTrue();
        assertThat(MathUtil.isGreaterThanZero(two)).isTrue();
        assertThat(MathUtil.isGreaterThanZero(two, MathContext.DECIMAL64)).isTrue();
        assertThat(MathUtil.isLessThanZero(negative)).isTrue();
        assertThat(MathUtil.isEqualTo(one, Money.of(CURRENCY, BigDecimal.ONE))).isTrue();
        assertThat(MathUtil.isEqualTo((Money) null, (Money) null)).isTrue();
        assertThat(MathUtil.isGreaterThan(two, one)).isTrue();
        assertThat(MathUtil.isLessThan(one, two)).isTrue();
        assertThat(MathUtil.plus(one, two).getAmount()).isEqualByComparingTo("3.00");
        assertThat(MathUtil.plus(one, two, MathContext.DECIMAL64).getAmount()).isEqualByComparingTo("3.00");
        assertThat(MathUtil.plus(one, null)).isSameAs(one);
        assertThat(MathUtil.plus(one, two, null).getAmount()).isEqualByComparingTo("3.00");
        assertThat(MathUtil.plus(MathContext.DECIMAL64, one, two).getAmount()).isEqualByComparingTo("3.00");
        assertThat(MathUtil.minus(two, one).getAmount()).isEqualByComparingTo("1.00");
        assertThat(MathUtil.minus(two, one, one).isZero()).isTrue();
        assertThat(MathUtil.minusToZero(one, two).isZero()).isTrue();
        assertThat(MathUtil.min(one, two, true)).isSameAs(one);
        assertThat(MathUtil.min(true, two, one, null)).isSameAs(one);
        assertThat(MathUtil.negate(two).getAmount()).isEqualByComparingTo("-2.00");
        assertThat(MathUtil.negate(two, MathContext.DECIMAL64).getAmount()).isEqualByComparingTo("-2.00");
        assertThat(MathUtil.max(one, two, true)).isSameAs(two);
    }

    @Test
    void coversPercentageAndTrailingZeroCases() {
        assertThat(MathUtil.percentageOf(BigDecimal.ZERO, new BigDecimal("10"), MathContext.DECIMAL64)).isZero();
        assertThat(MathUtil.percentageOf(BigDecimal.ZERO, new BigDecimal("10"), 8)).isZero();
        assertThat(MathUtil.percentageOf(new BigDecimal("200"), new BigDecimal("12.5"), 8)).isEqualByComparingTo("25");
        assertThat(MathUtil.stripTrailingZeros(null)).isNull();
        assertThat(MathUtil.stripTrailingZeros(new BigDecimal("0.000"))).isEqualByComparingTo("0");
    }
}
