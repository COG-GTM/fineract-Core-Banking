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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
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
 * Unit tests for {@link CompoundInterestHelper#calculateInterestForAllPostingPeriods}.
 *
 * Two behaviours are pinned here:
 *
 * 1. Aggregation and rounding. The total is the sum of the per posting period amounts, each rounded to the currency
 * scale first, so it is a sum of rounded values rather than a rounded sum.
 *
 * 2. The condition that decides whether interest already earned keeps compounding into the next posting period. It is
 * driven by the interest transfer flag on the account, whether this period's interest was already transferred out, and
 * the account's locked until date.
 *
 * Fixtures use an annual rate of 0.0365 over a 365 day year, i.e. exactly 0.0001 per day, so every expected value is
 * exact. All dates are fixed literals and the rounding mode is set explicitly by every test that depends on it.
 */
class CompoundInterestHelperTest {

    private static final String TENANT_ID = "compound-interest-helper-test-tenant";
    private static final MonetaryCurrency USD = new MonetaryCurrency("USD", 2, null);

    /** 3.65% per annum over a 365 day year, i.e. exactly 0.0001 per day. */
    private static final BigDecimal ANNUAL_RATE = new BigDecimal("0.0365");
    private static final long DAYS_IN_YEAR = 365L;
    private static final int FINANCIAL_YEAR_BEGINNING_MONTH = 1;

    private static final LocalDate JANUARY_START = LocalDate.of(2024, 1, 1);
    private static final LocalDate JANUARY_END = LocalDate.of(2024, 1, 31);
    private static final LocalDate FEBRUARY_START = LocalDate.of(2024, 2, 1);
    private static final LocalDate FEBRUARY_END = LocalDate.of(2024, 2, 29);
    /** January's interest is posted the day after the period ends. */
    private static final LocalDate JANUARY_POSTING_DATE = FEBRUARY_START;

    private static final BigDecimal COMPOUNDED = new BigDecimal("6.01");
    private static final BigDecimal NOT_COMPOUNDED = new BigDecimal("6.00");

    private final CompoundInterestHelper helper = new CompoundInterestHelper();

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
     * A posting period with no transactions in it: a single flat balance held for the whole period. Monthly
     * compounding, so interest is compounded when the period ends on a month end.
     */
    private PostingPeriod flatBalancePeriod(final LocalDate startDate, final LocalDate endDate, final String balance) {
        return PostingPeriod.createFrom(LocalDateInterval.create(startDate, endDate), Money.of(USD, new BigDecimal(balance)),
                Collections.emptyList(), USD, SavingsCompoundingInterestPeriodType.MONTHLY, SavingsInterestCalculationType.DAILY_BALANCE,
                ANNUAL_RATE, DAYS_IN_YEAR, endDate, Collections.emptyList(), false, Money.zero(USD), false, false,
                FINANCIAL_YEAR_BEGINNING_MONTH);
    }

    /**
     * Two consecutive monthly posting periods over a flat 1000.00 balance.
     *
     * January: 1000.00 x 0.0001 x 31 = 3.10, compounded at the 31 Jan month end.
     *
     * February, if January's interest is still compounded: (1000.00 + 3.10) x 0.0001 x 29 = 2.90899, which rounds to
     * 2.91, giving a total of 6.01.
     *
     * February, if January's interest was reset: 1000.00 x 0.0001 x 29 = 2.90, giving a total of 6.00.
     */
    private List<PostingPeriod> januaryAndFebruary(final boolean interestAlreadyTransferred) {
        final PostingPeriod january = flatBalancePeriod(JANUARY_START, JANUARY_END, "1000.00");
        january.setInterestTransfered(interestAlreadyTransferred);
        final PostingPeriod february = flatBalancePeriod(FEBRUARY_START, FEBRUARY_END, "1000.00");
        february.setInterestTransfered(interestAlreadyTransferred);
        return List.of(january, february);
    }

    @Test
    @DisplayName("No posting periods yields zero rather than null")
    void noPostingPeriodsYieldsZero() {
        final Money interest = helper.calculateInterestForAllPostingPeriods(USD, Collections.emptyList(), null, true);

        assertEquals(0, BigDecimal.ZERO.compareTo(interest.getAmount()), "expected 0 but was " + interest.getAmount());
        assertEquals("USD", interest.getCurrencyCode(), "the zero amount should still carry the account currency");
    }

    @Test
    @DisplayName("The total is the sum of the per period rounded amounts, not the rounding of the true total")
    void totalIsTheSumOfPerPeriodRoundedAmounts() {
        useRoundingMode(RoundingMode.HALF_EVEN);
        // two single day posting periods over a 1140.00 balance
        // each earns 1140.00 x 0.0001 x 1 = 0.114, which rounds to 0.11
        final List<PostingPeriod> periods = List.of(flatBalancePeriod(JANUARY_START, JANUARY_START, "1140.00"),
                flatBalancePeriod(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 2), "1140.00"));

        final Money interest = helper.calculateInterestForAllPostingPeriods(USD, periods, null, false);

        // 0.11 + 0.11 = 0.22, whereas rounding the true total 0.228 would have given 0.23
        assertEquals(0, new BigDecimal("0.22").compareTo(interest.getAmount()), "expected 0.22 but was " + interest.getAmount());
    }

    @Test
    @DisplayName("Per period rounding honours the tenant rounding mode")
    void perPeriodRoundingHonoursTheTenantRoundingMode() {
        // two single day posting periods, earning 1250.00 x 0.0001 = 0.125 and 1150.00 x 0.0001 = 0.115,
        // both exact half way cases at two decimal places
        useRoundingMode(RoundingMode.HALF_UP);
        final Money halfUpInterest = helper.calculateInterestForAllPostingPeriods(USD, twoHalfWayPeriods(), null, false);

        // HALF_UP: 0.125 -> 0.13 and 0.115 -> 0.12, total 0.25
        assertEquals(0, new BigDecimal("0.25").compareTo(halfUpInterest.getAmount()),
                "expected 0.25 under HALF_UP but was " + halfUpInterest.getAmount());

        useRoundingMode(RoundingMode.HALF_EVEN);
        final Money halfEvenInterest = helper.calculateInterestForAllPostingPeriods(USD, twoHalfWayPeriods(), null, false);

        // HALF_EVEN: 0.125 -> 0.12 (2 is even) and 0.115 -> 0.12, total 0.24
        assertEquals(0, new BigDecimal("0.24").compareTo(halfEvenInterest.getAmount()),
                "expected 0.24 under HALF_EVEN but was " + halfEvenInterest.getAmount());
    }

    private List<PostingPeriod> twoHalfWayPeriods() {
        return List.of(flatBalancePeriod(JANUARY_START, JANUARY_START, "1250.00"),
                flatBalancePeriod(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 2), "1150.00"));
    }

    @Test
    @DisplayName("Interest keeps compounding when interest transfer is switched off")
    void interestKeepsCompoundingWhenInterestTransferIsOff() {
        useRoundingMode(RoundingMode.HALF_EVEN);

        final Money interest = helper.calculateInterestForAllPostingPeriods(USD, januaryAndFebruary(false), null, false);

        // nothing is transferred out of the account, so January's 3.10 earns interest in February:
        // 3.10 + (1000.00 + 3.10) x 0.0001 x 29 = 3.10 + 2.91 = 6.01
        assertEquals(0, COMPOUNDED.compareTo(interest.getAmount()), "expected 6.01 but was " + interest.getAmount());
    }

    @Test
    @DisplayName("Interest stops compounding once it is transferred to the linked account")
    void interestStopsCompoundingOnceTransferredToLinkedAccount() {
        useRoundingMode(RoundingMode.HALF_EVEN);

        final Money interest = helper.calculateInterestForAllPostingPeriods(USD, januaryAndFebruary(false), null, true);

        // interest transfer is on and January's interest has not already been transferred, so it is
        // swept to the linked account and no longer sits in this account earning interest:
        // 3.10 + 1000.00 x 0.0001 x 29 = 3.10 + 2.90 = 6.00
        assertEquals(0, NOT_COMPOUNDED.compareTo(interest.getAmount()), "expected 6.00 but was " + interest.getAmount());
    }

    @Test
    @DisplayName("Interest already transferred in an earlier run is not swept a second time")
    void interestAlreadyTransferredIsNotSweptASecondTime() {
        useRoundingMode(RoundingMode.HALF_EVEN);

        final Money interest = helper.calculateInterestForAllPostingPeriods(USD, januaryAndFebruary(true), null, true);

        // the period is already marked as transferred, so the accumulator is left alone and
        // January's interest continues to compound: 3.10 + 2.91 = 6.01
        assertEquals(0, COMPOUNDED.compareTo(interest.getAmount()), "expected 6.01 but was " + interest.getAmount());
    }

    @Test
    @DisplayName("Interest posted while the account is still locked keeps compounding")
    void interestPostedWhileAccountIsStillLockedKeepsCompounding() {
        useRoundingMode(RoundingMode.HALF_EVEN);
        final LocalDate lockUntilAfterPosting = JANUARY_POSTING_DATE.plusDays(1);

        final Money interest = helper.calculateInterestForAllPostingPeriods(USD, januaryAndFebruary(false), lockUntilAfterPosting, true);

        // the account is locked beyond the 1 Feb posting date, so the interest cannot be transferred
        // out and keeps compounding: 3.10 + 2.91 = 6.01
        assertEquals(0, COMPOUNDED.compareTo(interest.getAmount()), "expected 6.01 but was " + interest.getAmount());
    }

    @Test
    @DisplayName("A lock expiring exactly on the posting date still blocks the transfer (locked until is inclusive)")
    void lockExpiringExactlyOnThePostingDateStillBlocksTheTransfer() {
        useRoundingMode(RoundingMode.HALF_EVEN);

        final Money interest = helper.calculateInterestForAllPostingPeriods(USD, januaryAndFebruary(false), JANUARY_POSTING_DATE, true);

        // the posting date must be strictly after the locked until date for the sweep to happen,
        // so a lock ending on the posting date itself leaves the interest compounding: 3.10 + 2.91 = 6.01
        assertEquals(0, COMPOUNDED.compareTo(interest.getAmount()), "expected 6.01 but was " + interest.getAmount());
    }

    @Test
    @DisplayName("A lock that expired the day before the posting date allows the transfer")
    void lockExpiredBeforeThePostingDateAllowsTheTransfer() {
        useRoundingMode(RoundingMode.HALF_EVEN);
        final LocalDate lockUntilBeforePosting = JANUARY_POSTING_DATE.minusDays(1);

        final Money interest = helper.calculateInterestForAllPostingPeriods(USD, januaryAndFebruary(false), lockUntilBeforePosting, true);

        // the lock has expired by the 1 Feb posting date, so the interest is swept out and stops
        // compounding: 3.10 + 2.90 = 6.00
        assertEquals(0, NOT_COMPOUNDED.compareTo(interest.getAmount()), "expected 6.00 but was " + interest.getAmount());
    }

    @Test
    @DisplayName("CHARACTERISATION, PENDING RULING: a null interest transfer flag throws NullPointerException")
    void characterisesNullInterestTransferEnabledThrowsNullPointerException() {
        useRoundingMode(RoundingMode.HALF_EVEN);
        final List<PostingPeriod> periods = januaryAndFebruary(false);

        // CURRENT BEHAVIOUR: interestTransferEnabled is a boxed Boolean and is unboxed by the
        // !interestTransferEnabled term of the condition, so a null flag throws NullPointerException
        // in the middle of an interest posting run.
        // WHY THIS MAY BE WRONG: the parameter is nullable at the type level and every other input
        // to this method is tolerated, so a null here plausibly ought to mean "interest transfer is
        // not configured", i.e. behave like false, rather than abort the posting run.
        // This test records the behaviour as it is today. It is NOT an endorsement, and production
        // code has deliberately not been changed. Pending a ruling on the intended contract.
        assertThrows(NullPointerException.class, () -> helper.calculateInterestForAllPostingPeriods(USD, periods, null, null),
                "expected the null flag to be unboxed and throw");
    }

    @Test
    @DisplayName("CHARACTERISATION, PENDING RULING: a null interest transfer flag is tolerated if every period was transferred")
    void characterisesNullInterestTransferEnabledIsToleratedWhenAllPeriodsAlreadyTransferred() {
        useRoundingMode(RoundingMode.HALF_EVEN);

        // CURRENT BEHAVIOUR: the condition short circuits on isInterestTransfered(), so the same null
        // flag that throws above is harmless when every posting period is already marked transferred.
        // WHY THIS MAY BE WRONG: whether a null flag blows up depends on account data rather than on
        // configuration, which makes the failure above intermittent and hard to reproduce in support.
        // This test records the behaviour as it is today. It is NOT an endorsement, and production
        // code has deliberately not been changed. Pending a ruling on the intended contract.
        final Money interest = helper.calculateInterestForAllPostingPeriods(USD, januaryAndFebruary(true), null, null);

        // 3.10 + 2.91 = 6.01
        assertEquals(0, COMPOUNDED.compareTo(interest.getAmount()), "expected 6.01 but was " + interest.getAmount());
    }
}
