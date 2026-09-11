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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class SavingsAccountTransactionSummaryWrapperTest {

    private static final MonetaryCurrency CURRENCY = new MonetaryCurrency("USD", 2, null);
    private static MockedStatic<org.apache.fineract.organisation.monetary.domain.MoneyHelper> moneyHelper;

    @BeforeAll
    static void setUp() {
        moneyHelper = Mockito.mockStatic(org.apache.fineract.organisation.monetary.domain.MoneyHelper.class);
        moneyHelper.when(org.apache.fineract.organisation.monetary.domain.MoneyHelper::getMathContext)
                .thenReturn(java.math.MathContext.DECIMAL64);
    }

    @AfterAll
    static void tearDown() {
        moneyHelper.close();
    }

    @Test
    void sumsOnlyEligibleDepositsAndWithdrawals() {
        SavingsAccountTransaction deposit = mock(SavingsAccountTransaction.class);
        Money depositAmount = Money.of(CURRENCY, new BigDecimal("25.00"));
        when(deposit.isDepositAndNotReversed()).thenReturn(true);
        when(deposit.isReversalTransaction()).thenReturn(false);
        when(deposit.getAmount(CURRENCY)).thenReturn(depositAmount);

        SavingsAccountTransaction withdrawal = mock(SavingsAccountTransaction.class);
        Money withdrawalAmount = Money.of(CURRENCY, new BigDecimal("7.50"));
        when(withdrawal.isWithdrawal()).thenReturn(true);
        when(withdrawal.isNotReversed()).thenReturn(true);
        when(withdrawal.isReversalTransaction()).thenReturn(false);
        when(withdrawal.getAmount(CURRENCY)).thenReturn(withdrawalAmount);

        SavingsAccountTransactionSummaryWrapper wrapper = new SavingsAccountTransactionSummaryWrapper();
        assertThat(wrapper.calculateTotalDeposits(CURRENCY, List.of(deposit, withdrawal))).isEqualByComparingTo("25.00");
        assertThat(wrapper.calculateTotalWithdrawals(CURRENCY, List.of(deposit, withdrawal))).isEqualByComparingTo("7.50");
    }

    @Test
    void returnsNullWhenNoTransactionsMatchTheSummary() {
        SavingsAccountTransaction transaction = mock(SavingsAccountTransaction.class);
        when(transaction.isDepositAndNotReversed()).thenReturn(false);
        when(transaction.isReversalTransaction()).thenReturn(false);

        assertThat(new SavingsAccountTransactionSummaryWrapper().calculateTotalDeposits(CURRENCY, List.of(transaction))).isNull();
    }

    @Test
    void calculatesEverySummaryWithMixedTransactionTypesAndExcludesReversals() {
        SavingsAccountTransaction deposit = transaction("10.00");
        when(deposit.isDepositAndNotReversed()).thenReturn(true);

        SavingsAccountTransaction withdrawal = transaction("4.00");
        when(withdrawal.isWithdrawal()).thenReturn(true);
        when(withdrawal.isNotReversed()).thenReturn(true);

        SavingsAccountTransaction interest = transaction("3.00");
        when(interest.isInterestPostingAndNotReversed()).thenReturn(true);
        when(interest.isNotReversed()).thenReturn(true);

        SavingsAccountTransaction withdrawalFee = transaction("1.00");
        when(withdrawalFee.isWithdrawalFeeAndNotReversed()).thenReturn(true);
        when(withdrawalFee.isNotReversed()).thenReturn(true);

        SavingsAccountTransaction annualFee = transaction("2.00");
        when(annualFee.isAnnualFeeAndNotReversed()).thenReturn(true);
        when(annualFee.isNotReversed()).thenReturn(true);

        SavingsAccountTransaction fee = transaction("5.00");
        when(fee.isFeeChargeAndNotReversed()).thenReturn(true);

        SavingsAccountTransaction waivedFee = transaction("6.00");
        when(waivedFee.isWaiveFeeChargeAndNotReversed()).thenReturn(true);

        SavingsAccountTransaction penalty = transaction("7.00");
        when(penalty.isPenaltyChargeAndNotReversed()).thenReturn(true);

        SavingsAccountTransaction waivedPenalty = transaction("8.00");
        when(waivedPenalty.isWaivePenaltyChargeAndNotReversed()).thenReturn(true);

        SavingsAccountTransaction overdraft = transaction("9.00");
        when(overdraft.isOverdraftInterestAndNotReversed()).thenReturn(true);

        SavingsAccountTransaction tax = transaction("11.00");
        when(tax.isWithHoldTaxAndNotReversed()).thenReturn(true);

        SavingsAccountTransaction reversed = transaction("100.00");
        when(reversed.isDepositAndNotReversed()).thenReturn(true);
        when(reversed.isReversalTransaction()).thenReturn(true);

        List<SavingsAccountTransaction> transactions = List.of(deposit, withdrawal, interest, withdrawalFee, annualFee, fee, waivedFee,
                penalty, waivedPenalty, overdraft, tax, reversed);
        SavingsAccountTransactionSummaryWrapper wrapper = new SavingsAccountTransactionSummaryWrapper();

        assertThat(wrapper.calculateTotalDeposits(CURRENCY, transactions)).isEqualByComparingTo("10.00");
        assertThat(wrapper.calculateTotalWithdrawals(CURRENCY, transactions)).isEqualByComparingTo("4.00");
        assertThat(wrapper.calculateTotalInterestPosted(CURRENCY, transactions)).isEqualByComparingTo("3.00");
        assertThat(wrapper.calculateTotalInterestPosted(CURRENCY, BigDecimal.ONE, transactions)).isEqualByComparingTo("4.00");
        assertThat(wrapper.calculateTotalWithdrawalFees(CURRENCY, transactions)).isEqualByComparingTo("1.00");
        assertThat(wrapper.calculateTotalAnnualFees(CURRENCY, transactions)).isEqualByComparingTo("2.00");
        assertThat(wrapper.calculateTotalFeesCharge(CURRENCY, transactions)).isEqualByComparingTo("5.00");
        assertThat(wrapper.calculateTotalFeesChargeWaived(CURRENCY, transactions)).isEqualByComparingTo("6.00");
        assertThat(wrapper.calculateTotalPenaltyCharge(CURRENCY, transactions)).isEqualByComparingTo("7.00");
        assertThat(wrapper.calculateTotalPenaltyChargeWaived(CURRENCY, transactions)).isEqualByComparingTo("8.00");
        assertThat(wrapper.calculateTotalOverdraftInterest(CURRENCY, transactions)).isEqualByComparingTo("9.00");
        assertThat(wrapper.calculateTotalOverdraftInterest(CURRENCY, BigDecimal.ONE, transactions)).isEqualByComparingTo("10.00");
        assertThat(wrapper.calculateTotalWithholdTaxWithdrawal(CURRENCY, transactions)).isEqualByComparingTo("11.00");
    }

    private SavingsAccountTransaction transaction(String amount) {
        SavingsAccountTransaction transaction = mock(SavingsAccountTransaction.class);
        Money transactionAmount = Money.of(CURRENCY, new BigDecimal(amount));
        when(transaction.isReversalTransaction()).thenReturn(false);
        when(transaction.getAmount(CURRENCY)).thenReturn(transactionAmount);
        return transaction;
    }
}
