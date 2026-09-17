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
package org.apache.fineract.portfolio.savings.domain.interest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collections;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.domain.LocalDateInterval;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.savings.SavingsCompoundingInterestPeriodType;
import org.apache.fineract.portfolio.savings.SavingsInterestCalculationType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PostingPeriod#calculateInterest(CompoundInterestValues)}, in particular for the way it mutates
 * the {@link CompoundInterestValues} accumulator that {@link CompoundInterestHelper} threads across posting periods.
 *
 * The accumulator is the mechanism by which interest earned in one compounding period starts earning interest itself.
 * Interest is only moved from "uncompounded" to "compounded" when a compounding period ends exactly on the boundary
 * implied by the compounding type, so a posting period that ends mid-month leaves a stub uncompounded.
 *
 * Fixtures use an annual rate of 0.0365 over a 365 day year, i.e. exactly 0.0001 per day, so every expected value is
 * exact at the nine decimal places the production code scales to. All dates are fixed literals.
 */
class PostingPeriodCompoundingTest {

    private static final String TENANT_ID = "posting-period-test-tenant";
    private static final MonetaryCurrency USD = new MonetaryCurrency("USD", 2, null);

    /** 3.65% per annum over a 365 day year, i.e. exactly 0.0001 per day. */
    private static final BigDecimal ANNUAL_RATE = new BigDecimal("0.0365");
    private static final long DAYS_IN_YEAR = 365L;
    private static final int FINANCIAL_YEAR_BEGINNING_MONTH = 1;

    private FineractPlatformTenant originalTenant;

    @BeforeEach
    void setUp() {
        originalTenant = ThreadLocalContextUtil.getTenant();
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, TENANT_ID, "Test Tenant", "Asia/Kolkata", null));
        useRoundingMode(RoundingMode.HALF_EVEN);
    }

    @AfterEach
    void tearDown() {
        MoneyHelper.clearCacheForTenant(TENANT_ID);
        ThreadLocalContextUtil.setTenant(originalTenant);
    }

    private void useRoundingMode(final RoundingMode roundingMode) {
        MoneyHelper.initializeTenantRoundingMode(TENANT_ID, roundingMode.ordinal());
    }

    /**
     * A posting period with no transactions in it: a single flat balance held for the whole period.
     */
    private PostingPeriod flatBalancePeriod(final LocalDate startDate, final LocalDate endDate, final String balance,
            final SavingsCompoundingInterestPeriodType compoundingType, final BigDecimal annualRate) {
        return PostingPeriod.createFrom(LocalDateInterval.create(startDate, endDate), Money.of(USD, new BigDecimal(balance)),
                Collections.emptyList(), USD, compoundingType, SavingsInterestCalculationType.DAILY_BALANCE, annualRate, DAYS_IN_YEAR,
                endDate, Collections.emptyList(), false, Money.zero(USD), false, false, FINANCIAL_YEAR_BEGINNING_MONTH);
    }

    private CompoundInterestValues freshAccumulator() {
        return new CompoundInterestValues(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Monthly compounding inside a quarterly posting period compounds at each month end")
    void monthlyCompoundingInsideQuarterlyPostingPeriodCompoundsAtEachMonthEnd() {
        // 1 Jan 2024 to 31 Mar 2024, monthly compounding: three compounding periods of 31, 29 (2024 is
        // a leap year) and 31 days
        final PostingPeriod postingPeriod = flatBalancePeriod(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31), "1000.00",
                SavingsCompoundingInterestPeriodType.MONTHLY, ANNUAL_RATE);
        final CompoundInterestValues accumulator = freshAccumulator();

        final BigDecimal interest = postingPeriod.calculateInterest(accumulator);

        // January: 1000.00 x 0.0001 x 31 = 3.1 -> compounded balance becomes 1003.1
        // February: 1003.1 x 0.0001 x 29 = 2.90899 -> compounded balance becomes 1006.00899
        // March: 1006.00899 x 0.0001 x 31 = 3.118627869
        // total = 3.1 + 2.90899 + 3.118627869 = 9.127617869
        // without compounding it would have been 1000.00 x 0.0001 x 91 = 9.10, so the
        // 0.027617869 difference is the compounding effect
        assertEquals(0, new BigDecimal("9.127617869").compareTo(interest), "expected 9.127617869 but was " + interest);
        assertEquals(0, new BigDecimal("9.127617869").compareTo(accumulator.getcompoundedInterest()),
                "all interest should be compounded, but compounded was " + accumulator.getcompoundedInterest());
        assertEquals(0, BigDecimal.ZERO.compareTo(accumulator.getuncompoundedInterest()),
                "nothing should be left uncompounded, but uncompounded was " + accumulator.getuncompoundedInterest());
    }

    @Test
    @DisplayName("A posting period ending mid-month leaves the final stub uncompounded")
    void postingPeriodEndingMidMonthLeavesFinalStubUncompounded() {
        // 1 Jan 2024 to 15 Feb 2024, monthly compounding: a full January compounding period and a
        // 15 day February stub that does not reach the month end boundary
        final PostingPeriod postingPeriod = flatBalancePeriod(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 15), "1000.00",
                SavingsCompoundingInterestPeriodType.MONTHLY, ANNUAL_RATE);
        final CompoundInterestValues accumulator = freshAccumulator();

        final BigDecimal interest = postingPeriod.calculateInterest(accumulator);

        // January: 1000.00 x 0.0001 x 31 = 3.1, compounded because 31 Jan is a month end
        // 1 to 15 Feb: 1003.1 x 0.0001 x 15 = 1.50465, NOT compounded because 15 Feb is not a
        // month end, so it stays uncompounded and carries into the next posting period
        // total = 3.1 + 1.50465 = 4.60465
        assertEquals(0, new BigDecimal("4.604650000").compareTo(interest), "expected 4.604650000 but was " + interest);
        assertEquals(0, new BigDecimal("3.100000000").compareTo(accumulator.getcompoundedInterest()),
                "only January's interest should be compounded, but compounded was " + accumulator.getcompoundedInterest());
        assertEquals(0, new BigDecimal("1.504650000").compareTo(accumulator.getuncompoundedInterest()),
                "the February stub should be left uncompounded, but uncompounded was " + accumulator.getuncompoundedInterest());
    }

    @Test
    @DisplayName("Interest compounded in earlier posting periods earns interest in this one")
    void interestCompoundedInEarlierPostingPeriodsEarnsInterest() {
        final PostingPeriod postingPeriod = flatBalancePeriod(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31), "1000.00",
                SavingsCompoundingInterestPeriodType.MONTHLY, ANNUAL_RATE);
        final CompoundInterestValues accumulator = new CompoundInterestValues(new BigDecimal("100.00"), BigDecimal.ZERO);

        final BigDecimal interest = postingPeriod.calculateInterest(accumulator);

        // (1000.00 + 100.00 already compounded) x 0.0001 x 31 = 1100.00 x 0.0031 = 3.41
        assertEquals(0, new BigDecimal("3.410000000").compareTo(interest), "expected 3.410000000 but was " + interest);
        // 100.00 carried in plus 3.41 earned this period
        assertEquals(0, new BigDecimal("103.410000000").compareTo(accumulator.getcompoundedInterest()),
                "expected 103.410000000 but was " + accumulator.getcompoundedInterest());
    }

    @Test
    @DisplayName("Daily compounding applies FV = PV x (1 + r)^n across the posting period")
    void dailyCompoundingAppliesFutureValueFormulaAcrossThePostingPeriod() {
        final PostingPeriod postingPeriod = flatBalancePeriod(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 3), "1000.00",
                SavingsCompoundingInterestPeriodType.DAILY, ANNUAL_RATE);
        final CompoundInterestValues accumulator = freshAccumulator();

        final BigDecimal interest = postingPeriod.calculateInterest(accumulator);

        // r = 0.0001 per day, n = 3 days
        // (1.0001)^3 = 1.000300030001, so interest = 1000.00 x 0.000300030001 = 0.300030001
        assertEquals(0, new BigDecimal("0.300030001").compareTo(interest), "expected 0.300030001 but was " + interest);
        // a daily compounding period always ends on its own boundary, so nothing is left uncompounded
        assertEquals(0, new BigDecimal("0.300030001").compareTo(accumulator.getcompoundedInterest()),
                "expected 0.300030001 but was " + accumulator.getcompoundedInterest());
        assertEquals(0, BigDecimal.ZERO.compareTo(accumulator.getuncompoundedInterest()),
                "nothing should be left uncompounded, but uncompounded was " + accumulator.getuncompoundedInterest());
    }

    @Test
    @DisplayName("A single day posting period earns exactly one day of interest")
    void singleDayPostingPeriodEarnsOneDayOfInterest() {
        final PostingPeriod postingPeriod = flatBalancePeriod(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 1), "1000.00",
                SavingsCompoundingInterestPeriodType.MONTHLY, ANNUAL_RATE);
        final CompoundInterestValues accumulator = freshAccumulator();

        final BigDecimal interest = postingPeriod.calculateInterest(accumulator);

        // 1000.00 x 0.0001 x 1 = 0.10
        assertEquals(0, new BigDecimal("0.100000000").compareTo(interest), "expected 0.100000000 but was " + interest);
        // 1 Jan is not a month end, so the day's interest is not compounded yet
        assertEquals(0, BigDecimal.ZERO.compareTo(accumulator.getcompoundedInterest()),
                "expected nothing compounded but was " + accumulator.getcompoundedInterest());
        assertEquals(0, new BigDecimal("0.100000000").compareTo(accumulator.getuncompoundedInterest()),
                "expected 0.100000000 uncompounded but was " + accumulator.getuncompoundedInterest());
    }

    @Test
    @DisplayName("A zero rate posting period earns nothing and records a zero rounded amount")
    void zeroRatePostingPeriodEarnsNothing() {
        final PostingPeriod postingPeriod = flatBalancePeriod(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31), "1000.00",
                SavingsCompoundingInterestPeriodType.MONTHLY, BigDecimal.ZERO);
        final CompoundInterestValues accumulator = freshAccumulator();

        final BigDecimal interest = postingPeriod.calculateInterest(accumulator);

        // 1000.00 x 0 x 31 = 0
        assertEquals(0, BigDecimal.ZERO.compareTo(interest), "expected 0 but was " + interest);
        assertEquals(0, BigDecimal.ZERO.compareTo(postingPeriod.getInterestEarned().getAmount()),
                "expected a zero rounded amount but was " + postingPeriod.getInterestEarned().getAmount());
    }

    @Test
    @DisplayName("A zero balance posting period earns nothing")
    void zeroBalancePostingPeriodEarnsNothing() {
        final PostingPeriod postingPeriod = flatBalancePeriod(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31), "0.00",
                SavingsCompoundingInterestPeriodType.MONTHLY, ANNUAL_RATE);
        final CompoundInterestValues accumulator = freshAccumulator();

        final BigDecimal interest = postingPeriod.calculateInterest(accumulator);

        // 0.00 x 0.0001 x 31 = 0
        assertEquals(0, BigDecimal.ZERO.compareTo(interest), "expected 0 but was " + interest);
    }

    @Test
    @DisplayName("The rounded interest recorded on the period honours the tenant rounding mode")
    void roundedInterestOnThePeriodHonoursTheTenantRoundingMode() {
        // 1250.00 x 0.0001 x 1 = 0.125 exactly, a half way case at two decimal places
        useRoundingMode(RoundingMode.HALF_UP);
        final PostingPeriod halfUpPeriod = flatBalancePeriod(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 1), "1250.00",
                SavingsCompoundingInterestPeriodType.MONTHLY, ANNUAL_RATE);
        halfUpPeriod.calculateInterest(freshAccumulator());

        assertEquals(0, new BigDecimal("0.13").compareTo(halfUpPeriod.getInterestEarned().getAmount()),
                "HALF_UP should round 0.125 up to 0.13 but was " + halfUpPeriod.getInterestEarned().getAmount());

        useRoundingMode(RoundingMode.HALF_EVEN);
        final PostingPeriod halfEvenPeriod = flatBalancePeriod(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 1), "1250.00",
                SavingsCompoundingInterestPeriodType.MONTHLY, ANNUAL_RATE);
        halfEvenPeriod.calculateInterest(freshAccumulator());

        assertEquals(0, new BigDecimal("0.12").compareTo(halfEvenPeriod.getInterestEarned().getAmount()),
                "HALF_EVEN should round 0.125 to the even 0.12 but was " + halfEvenPeriod.getInterestEarned().getAmount());
    }
}
