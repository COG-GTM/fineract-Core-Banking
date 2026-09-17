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
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the interest arithmetic of {@link EndOfDayBalance}, the lowest level collaborator of
 * {@link CompoundInterestHelper#calculateInterestForAllPostingPeriods}.
 *
 * All expected values are derived by hand from the documented formulas and the arithmetic is shown in a comment above
 * each assertion. The fixtures deliberately use an annual rate of 0.0365 with a 365 day year so that the daily rate is
 * exactly 0.0001, which keeps every expected value exact at the nine decimal places the production code scales to.
 */
class EndOfDayBalanceInterestTest {

    private static final String TENANT_ID = "end-of-day-balance-test-tenant";
    private static final MonetaryCurrency USD = new MonetaryCurrency("USD", 2, null);

    /** 3.65% per annum over a 365 day year, i.e. exactly 0.0001 per day. */
    private static final BigDecimal ANNUAL_RATE = new BigDecimal("0.0365");
    /** 7.30% per annum over a 365 day year, i.e. exactly 0.0002 per day. */
    private static final BigDecimal OVERDRAFT_ANNUAL_RATE = new BigDecimal("0.0730");
    private static final long DAYS_IN_YEAR = 365L;
    private static final BigDecimal NO_MIN_BALANCE = BigDecimal.ZERO;
    private static final BigDecimal NO_MIN_OVERDRAFT = BigDecimal.ZERO;
    private static final BigDecimal NOTHING_TO_COMPOUND = BigDecimal.ZERO;
    private static final LocalDate JAN_1_2024 = LocalDate.of(2024, 1, 1);

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

    private EndOfDayBalance balanceOf(final String amount, final int numberOfDays) {
        final Money money = Money.of(USD, new BigDecimal(amount));
        return EndOfDayBalance.from(JAN_1_2024, money, money, numberOfDays);
    }

    private BigDecimal creditInterestOn(final EndOfDayBalance balance, final BigDecimal interestToCompound,
            final BigDecimal minBalanceForInterestCalculation) {
        return balance.calculateInterestOnBalance(interestToCompound, ANNUAL_RATE, DAYS_IN_YEAR, minBalanceForInterestCalculation,
                OVERDRAFT_ANNUAL_RATE, NO_MIN_OVERDRAFT);
    }

    @Test
    @DisplayName("Simple interest on a flat balance is balance x rate x days / daysInYear")
    void simpleInterestOnFlatBalanceForOneMonth() {
        final EndOfDayBalance balance = balanceOf("1000.00", 31);

        final BigDecimal interest = creditInterestOn(balance, NOTHING_TO_COMPOUND, NO_MIN_BALANCE);

        // 1000.00 x 0.0365 x 31/365 = 1000.00 x 0.0001 x 31 = 1000.00 x 0.0031 = 3.10
        assertEquals(0, new BigDecimal("3.100000000").compareTo(interest), "expected 3.100000000 but was " + interest);
    }

    @Test
    @DisplayName("Interest carried in from earlier periods is added to the balance before interest is calculated")
    void interestToCompoundIsAddedToTheBalanceUsedForInterest() {
        final EndOfDayBalance balance = balanceOf("1000.00", 31);

        final BigDecimal interest = creditInterestOn(balance, new BigDecimal("100.00"), NO_MIN_BALANCE);

        // (1000.00 + 100.00) x 0.0001 x 31 = 1100.00 x 0.0031 = 3.41
        assertEquals(0, new BigDecimal("3.410000000").compareTo(interest), "expected 3.410000000 but was " + interest);
    }

    @Test
    @DisplayName("A balance exactly equal to the minimum balance still earns interest (threshold is inclusive)")
    void balanceExactlyOnMinimumBalanceThresholdEarnsInterest() {
        final EndOfDayBalance balance = balanceOf("500.00", 10);

        final BigDecimal interest = creditInterestOn(balance, NOTHING_TO_COMPOUND, new BigDecimal("500.00"));

        // 500.00 x 0.0001 x 10 = 500.00 x 0.001 = 0.50
        assertEquals(0, new BigDecimal("0.500000000").compareTo(interest), "expected 0.500000000 but was " + interest);
    }

    @Test
    @DisplayName("A balance one cent below the minimum balance earns nothing")
    void balanceJustBelowMinimumBalanceThresholdEarnsNothing() {
        final EndOfDayBalance balance = balanceOf("499.99", 10);

        final BigDecimal interest = creditInterestOn(balance, NOTHING_TO_COMPOUND, new BigDecimal("500.00"));

        // below the threshold no interest accrues at all; had it accrued it would have been
        // 499.99 x 0.0001 x 10 = 0.499990000
        assertEquals(0, BigDecimal.ZERO.compareTo(interest), "expected 0 but was " + interest);
    }

    @Test
    @DisplayName("An overdrawn balance is charged at the overdraft rate, not the credit rate")
    void overdrawnBalanceIsChargedAtTheOverdraftRate() {
        final EndOfDayBalance balance = balanceOf("-1000.00", 31);

        final BigDecimal interest = balance.calculateInterestOnBalance(NOTHING_TO_COMPOUND, ANNUAL_RATE, DAYS_IN_YEAR, NO_MIN_BALANCE,
                OVERDRAFT_ANNUAL_RATE, NO_MIN_OVERDRAFT);

        // -1000.00 x 0.0730 x 31/365 = -1000.00 x 0.0002 x 31 = -1000.00 x 0.0062 = -6.20
        // at the credit rate of 0.0365 it would have been -3.10, i.e. half the charge
        assertEquals(0, new BigDecimal("-6.200000000").compareTo(interest), "expected -6.200000000 but was " + interest);
    }

    @Test
    @DisplayName("A balance exactly on the free overdraft allowance is not charged (threshold is exclusive)")
    void overdrawnBalanceExactlyOnFreeAllowanceIsNotCharged() {
        final EndOfDayBalance balance = balanceOf("-500.00", 31);

        final BigDecimal interest = balance.calculateInterestOnBalance(NOTHING_TO_COMPOUND, ANNUAL_RATE, DAYS_IN_YEAR, NO_MIN_BALANCE,
                OVERDRAFT_ANNUAL_RATE, new BigDecimal("500.00"));

        // -500.00 is not strictly less than -500.00, so nothing is charged; one cent further
        // overdrawn the charge would be -500.01 x 0.0002 x 31 = -3.100062
        assertEquals(0, BigDecimal.ZERO.compareTo(interest), "expected 0 but was " + interest);
    }

    @Test
    @DisplayName("A balance one cent beyond the free overdraft allowance is charged on the whole balance")
    void overdrawnBalanceJustBeyondFreeAllowanceIsChargedOnTheWholeBalance() {
        final EndOfDayBalance balance = balanceOf("-500.01", 31);

        final BigDecimal interest = balance.calculateInterestOnBalance(NOTHING_TO_COMPOUND, ANNUAL_RATE, DAYS_IN_YEAR, NO_MIN_BALANCE,
                OVERDRAFT_ANNUAL_RATE, new BigDecimal("500.00"));

        // the charge is on the full overdrawn balance, not on the excess over the allowance:
        // -500.01 x 0.0002 x 31 = -500.01 x 0.0062 = -3.100062
        assertEquals(0, new BigDecimal("-3.100062000").compareTo(interest), "expected -3.100062000 but was " + interest);
    }

    @Test
    @DisplayName("Daily compounded interest follows FV = PV x (1 + r)^n")
    void dailyCompoundedInterestUsesTheFutureValueFormula() {
        final EndOfDayBalance balance = balanceOf("1000.00", 3);

        final BigDecimal interest = balance.calculateInterestOnBalanceAndInterest(NOTHING_TO_COMPOUND, ANNUAL_RATE, DAYS_IN_YEAR,
                NO_MIN_BALANCE, OVERDRAFT_ANNUAL_RATE, NO_MIN_OVERDRAFT);

        // r = 0.0365/365 = 0.0001, n = 3
        // (1.0001)^3 = 1 + 3(0.0001) + 3(0.0001)^2 + (0.0001)^3 = 1.000300030001
        // FV - PV = 1000.00 x 1.000300030001 - 1000.00 = 0.300030001
        // simple interest over the same 3 days would be 1000.00 x 0.0003 = 0.300000000,
        // so the 0.000030001 difference is exactly the compounding effect
        assertEquals(0, new BigDecimal("0.300030001").compareTo(interest), "expected 0.300030001 but was " + interest);
    }

    @Test
    @DisplayName("A zero length balance period earns no interest")
    void zeroLengthBalancePeriodEarnsNoInterest() {
        final EndOfDayBalance balance = balanceOf("1000.00", 0);

        final BigDecimal interest = creditInterestOn(balance, NOTHING_TO_COMPOUND, NO_MIN_BALANCE);

        // 1000.00 x 0.0001 x 0 = 0
        assertEquals(0, BigDecimal.ZERO.compareTo(interest), "expected 0 but was " + interest);
    }

    @Test
    @DisplayName("Cumulative balance weights the balance including compounded interest by the number of days")
    void cumulativeBalanceWeightsBalanceAndCompoundedInterestByDays() {
        final EndOfDayBalance balance = balanceOf("1000.00", 31);

        final BigDecimal cumulativeBalance = balance.cumulativeBalance(new BigDecimal("100.00"));

        // (1000.00 + 100.00) x 31 = 34100
        assertEquals(0, new BigDecimal("34100.000000000").compareTo(cumulativeBalance),
                "expected 34100.000000000 but was " + cumulativeBalance);
    }

    @Test
    @DisplayName("A zero interest rate earns nothing on a positive balance")
    void zeroInterestRateEarnsNothing() {
        final EndOfDayBalance balance = balanceOf("1000.00", 31);

        final BigDecimal interest = balance.calculateInterestOnBalance(NOTHING_TO_COMPOUND, BigDecimal.ZERO, DAYS_IN_YEAR, NO_MIN_BALANCE,
                OVERDRAFT_ANNUAL_RATE, NO_MIN_OVERDRAFT);

        // 1000.00 x 0 x 31/365 = 0
        assertEquals(0, BigDecimal.ZERO.compareTo(interest), "expected 0 but was " + interest);
    }
}
