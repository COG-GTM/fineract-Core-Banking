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
package org.apache.fineract.portfolio.loanaccount.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
import org.apache.fineract.portfolio.loanaccount.repository.LoanBuyDownFeeBalanceRepository;
import org.apache.fineract.portfolio.loanaccount.repository.LoanCapitalizedIncomeBalanceRepository;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanTransactionValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProgressiveLoanTransactionValidatorImplTest {

    @Mock
    private FromJsonHelper fromJsonHelper;

    @Mock
    private LoanTransactionValidator loanTransactionValidator;

    @Mock
    private LoanRepositoryWrapper loanRepositoryWrapper;

    @Mock
    private LoanCapitalizedIncomeBalanceRepository loanCapitalizedIncomeBalanceRepository;

    @Mock
    private LoanBuyDownFeeBalanceRepository loanBuyDownFeeBalanceRepository;

    @Mock
    private LoanTransactionRepository loanTransactionRepository;

    @Mock
    private LoanMaximumAmountCalculator loanMaximumAmountCalculator;

    @Test
    void delegatesTransactionValidation() {
        ProgressiveLoanTransactionValidatorImpl validator = newValidator();
        String json = "{\"transactionDate\":\"2025-02-03\"}";

        validator.validateTransaction(json);

        verify(loanTransactionValidator).validateTransaction(json);
    }

    @Test
    void propagatesValidationFailures() {
        ProgressiveLoanTransactionValidatorImpl validator = newValidator();
        String json = "{}";
        IllegalArgumentException failure = new IllegalArgumentException("invalid transaction");
        doThrow(failure).when(loanTransactionValidator).validateTransaction(json);

        assertThatThrownBy(() -> validator.validateTransaction(json)).isSameAs(failure);
    }

    private ProgressiveLoanTransactionValidatorImpl newValidator() {
        return new ProgressiveLoanTransactionValidatorImpl(fromJsonHelper, loanTransactionValidator, loanRepositoryWrapper,
                loanCapitalizedIncomeBalanceRepository, loanBuyDownFeeBalanceRepository, loanTransactionRepository,
                loanMaximumAmountCalculator);
    }
}
