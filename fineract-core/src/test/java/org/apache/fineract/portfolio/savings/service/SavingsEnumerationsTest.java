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
package org.apache.fineract.portfolio.savings.service;

import java.lang.reflect.Method;
import java.util.stream.Stream;
import org.apache.fineract.accounting.common.AccountingEnumerations;
import org.apache.fineract.accounting.common.AccountingRuleType;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.portfolio.savings.PreClosurePenalInterestOnType;
import org.apache.fineract.portfolio.savings.SavingsCompoundingInterestPeriodType;
import org.apache.fineract.portfolio.savings.SavingsInterestCalculationDaysInYearType;
import org.apache.fineract.portfolio.savings.SavingsInterestCalculationType;
import org.apache.fineract.portfolio.savings.SavingsPeriodFrequencyType;
import org.apache.fineract.portfolio.savings.SavingsPostingInterestPeriodType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class SavingsEnumerationsTest {

    @Test
    void savingEnumerationMapsCompoundingInterestPeriodType() {
        int id = SavingsCompoundingInterestPeriodType.MONTHLY.getValue();

        EnumOptionData data = SavingsEnumerations.savingEnumeration(SavingsEnumerations.INTEREST_COMPOUNDING_PERIOD_TYPE, id);
        EnumOptionData expected = SavingsEnumerations.compoundingInterestPeriodType(id);

        Assertions.assertEquals(data, expected);
    }

    @Test
    void savingEnumerationMapsPostingInterestPeriodType() {
        int id = SavingsPostingInterestPeriodType.BIANNUAL.getValue();

        EnumOptionData data = SavingsEnumerations.savingEnumeration(SavingsEnumerations.INTEREST_POSTING_PERIOD_TYPE, id);
        EnumOptionData expected = SavingsEnumerations.interestPostingPeriodType(id);

        Assertions.assertEquals(data, expected);
    }

    @Test
    void savingEnumerationMapsInterestCalculationType() {
        int id = SavingsInterestCalculationType.DAILY_BALANCE.getValue();

        EnumOptionData data = SavingsEnumerations.savingEnumeration(SavingsEnumerations.INTEREST_CALCULATION_TYPE, id);
        EnumOptionData expected = SavingsEnumerations.interestCalculationType(id);

        Assertions.assertEquals(data, expected);
    }

    @Test
    void savingEnumerationMapsMinDepositTermType() {
        int id = SavingsPeriodFrequencyType.WEEKS.getValue();

        EnumOptionData data = SavingsEnumerations.savingEnumeration(SavingsEnumerations.MIN_DEPOSIT_TERM_TYPE, id);
        EnumOptionData expected = SavingsEnumerations.depositTermFrequencyType(id);

        Assertions.assertEquals(data, expected);
    }

    @Test
    void savingEnumerationMapsMaxDepositTermType() {
        int id = SavingsPeriodFrequencyType.YEARS.getValue();

        EnumOptionData data = SavingsEnumerations.savingEnumeration(SavingsEnumerations.MAX_DEPOSIT_TERM_TYPE, id);
        EnumOptionData expected = SavingsEnumerations.depositTermFrequencyType(id);

        Assertions.assertEquals(data, expected);
    }

    @Test
    void savingEnumerationMapsInMultiplesOfDepositTermType() {
        int id = SavingsPeriodFrequencyType.DAYS.getValue();

        EnumOptionData data = SavingsEnumerations.savingEnumeration(SavingsEnumerations.IN_MULTIPLES_OF_DEPOSIT_TERM_TYPE, id);
        EnumOptionData expected = SavingsEnumerations.inMultiplesOfDepositTermFrequencyType(id);

        Assertions.assertEquals(data, expected);
    }

    @Test
    void savingEnumerationMapsDepositPeriodFrequencyType() {
        int id = SavingsPeriodFrequencyType.MONTHS.getValue();

        EnumOptionData data = SavingsEnumerations.savingEnumeration(SavingsEnumerations.DEPOSIT_PERIOD_FREQUNCY_TYPE, id);
        EnumOptionData expected = SavingsEnumerations.depositPeriodFrequency(id);

        Assertions.assertEquals(data, expected);
    }

    @Test
    void savingEnumerationMapsLockinPeriodFrequencyType() {
        int id = SavingsPeriodFrequencyType.MONTHS.getValue();

        EnumOptionData data = SavingsEnumerations.savingEnumeration(SavingsEnumerations.LOCKIN_PERIOD_FREQUNCY_TYPE, id);
        EnumOptionData expected = SavingsEnumerations.lockinPeriodFrequencyType(id);

        Assertions.assertEquals(data, expected);
    }

    @Test
    void savingEnumerationMapsAccountingRuleType() {
        int id = AccountingRuleType.CASH_BASED.getValue();

        EnumOptionData data = SavingsEnumerations.savingEnumeration(SavingsEnumerations.ACCOUNTING_RULE_TYPE, id);
        EnumOptionData expected = AccountingEnumerations.accountingRuleType(id);

        Assertions.assertEquals(data, expected);
    }

    @Test
    void savingEnumerationMapsPreClosurePenaltyInterestType() {
        int id = PreClosurePenalInterestOnType.WHOLE_TERM.getValue();

        EnumOptionData data = SavingsEnumerations.savingEnumeration(SavingsEnumerations.PRE_CLOSURE_PENAL_INTEREST_TYPE, id);
        EnumOptionData expected = SavingsEnumerations.preClosurePenaltyInterestOnType(id);

        Assertions.assertEquals(data, expected);
    }

    @Test
    void savingEnumerationMapsInterestCalculationDaysInYearType() {
        int id = SavingsInterestCalculationDaysInYearType.DAYS_365.getValue();

        EnumOptionData data = SavingsEnumerations.savingEnumeration(SavingsEnumerations.INTEREST_CALCULATION_DAYS_IN_YEAR, id);
        EnumOptionData expected = SavingsEnumerations.interestCalculationDaysInYearType(id);

        Assertions.assertEquals(data, expected);
    }

    @Test
    void savingEnumerationMapsRecurringFrequencyType() {
        int id = SavingsPeriodFrequencyType.WEEKS.getValue();

        EnumOptionData data = SavingsEnumerations.savingEnumeration(SavingsEnumerations.RECURRING_FREQUENCY_TYPE, id);
        EnumOptionData expected = SavingsEnumerations.depositPeriodFrequency(id);

        Assertions.assertEquals(data, expected);
    }

    @Test
    void savingEnumerationReturnsNullForUnknownType() {
        EnumOptionData data = SavingsEnumerations.savingEnumeration("unknownType", 1);

        Assertions.assertNull(data);
    }

    @ParameterizedTest
    @MethodSource("allMappings")
    void mapsEveryValueOfEachExposedEnum(String enumClassName, String mapperName) throws Exception {
        Class<?> enumType = Class.forName(enumClassName);
        Method mapper = SavingsEnumerations.class.getMethod(mapperName, enumType);
        for (Object enumValue : enumType.getEnumConstants()) {
            Object option = mapper.invoke(null, enumValue);
            Assertions.assertNotNull(option, mapperName + "(" + enumValue + ")");
            Assertions.assertEquals(((Number) read(enumValue, "getValue", "getId")).longValue(),
                    ((Number) read(option, "getId")).longValue());
            Assertions.assertNotNull(read(enumValue, "getCode"));
            Assertions.assertNotNull(read(option, "getCode"));
        }
    }

    private static Stream<String[]> allMappings() {
        return Stream.of(new String[] { "org.apache.fineract.portfolio.savings.SavingsPeriodFrequencyType", "lockinPeriodFrequencyType" },
                new String[] { "org.apache.fineract.portfolio.savings.SavingsPeriodFrequencyType", "recurringDepositFrequencyType" },
                new String[] { "org.apache.fineract.portfolio.savings.SavingsPeriodFrequencyType", "depositTermFrequencyType" },
                new String[] { "org.apache.fineract.portfolio.savings.SavingsPeriodFrequencyType",
                        "inMultiplesOfDepositTermFrequencyType" },
                new String[] { "org.apache.fineract.portfolio.savings.SavingsPeriodFrequencyType", "depositPeriodFrequency" },
                new String[] { "org.apache.fineract.portfolio.savings.SavingsAccountTransactionType", "transactionType" },
                new String[] { "org.apache.fineract.portfolio.savings.domain.SavingsAccountStatusType", "status" },
                new String[] { "org.apache.fineract.portfolio.savings.domain.SavingsAccountSubStatusEnum", "subStatus" },
                new String[] { "org.apache.fineract.portfolio.savings.SavingsPostingInterestPeriodType", "interestPostingPeriodType" },
                new String[] { "org.apache.fineract.portfolio.savings.SavingsCompoundingInterestPeriodType",
                        "compoundingInterestPeriodType" },
                new String[] { "org.apache.fineract.portfolio.savings.SavingsInterestCalculationType", "interestCalculationType" },
                new String[] { "org.apache.fineract.portfolio.savings.SavingsInterestCalculationDaysInYearType",
                        "interestCalculationDaysInYearType" },
                new String[] { "org.apache.fineract.portfolio.savings.SavingsWithdrawalFeesType", "withdrawalFeeType" },
                new String[] { "org.apache.fineract.portfolio.savings.PreClosurePenalInterestOnType", "preClosurePenaltyInterestOnType" },
                new String[] { "org.apache.fineract.portfolio.savings.RecurringDepositType", "recurringDepositType" },
                new String[] { "org.apache.fineract.portfolio.savings.DepositAccountType", "depositType" },
                new String[] { "org.apache.fineract.portfolio.savings.DepositAccountOnClosureType", "depositAccountOnClosureType" },
                new String[] { "org.apache.fineract.portfolio.savings.DepositAccountOnHoldTransactionType", "onHoldTransactionType" });
    }

    private static Object read(Object value, String... methodNames) throws Exception {
        for (String methodName : methodNames) {
            try {
                return value.getClass().getMethod(methodName).invoke(value);
            } catch (NoSuchMethodException ignored) {
                // Enum data classes expose either id or value.
            }
        }
        throw new NoSuchMethodException(String.join(",", methodNames));
    }

}
