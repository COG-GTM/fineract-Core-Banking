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
package org.apache.fineract.migration.recon;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The reconciliation is only evidence if it fails when it should, so every case here seeds a known discrepancy. */
class ReconciliationComparatorTest {

    private static final Map<String, Long> SOURCE_COUNTS = Map.of("m_loan", 1200L, "acc_gl_journal_entry", 98_000L, "m_savings_account",
            450L);

    @Test
    void identicalRowCountsReconcile() {
        assertThat(ReconciliationComparator.compareRowCounts(SOURCE_COUNTS, Map.copyOf(SOURCE_COUNTS))).isEmpty();
    }

    @Test
    void oneMissingRowIsFound() {
        Map<String, Long> target = Map.of("m_loan", 1200L, "acc_gl_journal_entry", 97_999L, "m_savings_account", 450L);

        List<Variance> variances = ReconciliationComparator.compareRowCounts(SOURCE_COUNTS, target);

        assertThat(variances).singleElement().satisfies(variance -> {
            assertThat(variance.kind()).isEqualTo(Variance.Kind.ROW_COUNT_MISMATCH);
            assertThat(variance.scope()).isEqualTo("acc_gl_journal_entry");
            assertThat(variance.sourceValue()).isEqualTo("98000");
            assertThat(variance.targetValue()).isEqualTo("97999");
        });
    }

    @Test
    void tableMissingFromTargetIsFound() {
        Map<String, Long> target = Map.of("m_loan", 1200L, "acc_gl_journal_entry", 98_000L);

        assertThat(ReconciliationComparator.compareRowCounts(SOURCE_COUNTS, target)).extracting(Variance::kind)
                .containsExactly(Variance.Kind.MISSING_IN_TARGET);
    }

    @Test
    void tableOnlyInTargetIsFound() {
        Map<String, Long> target = Map.of("m_loan", 1200L, "acc_gl_journal_entry", 98_000L, "m_savings_account", 450L, "recon_scratch", 1L);

        assertThat(ReconciliationComparator.compareRowCounts(SOURCE_COUNTS, target)).extracting(Variance::scope)
                .containsExactly("recon_scratch");
    }

    @Test
    void identicalFinancialsReconcile() {
        assertThat(ReconciliationComparator.compareFinancials(ledger(), ledger())).isEmpty();
    }

    @Test
    void decimalScaleDifferencesAreNotVariances() {
        FinancialSnapshot target = FinancialSnapshot.builder()
                .put(AggregateKey.of("loans", "USD"),
                        Map.of("loan_count", new BigDecimal("2"), "principal_outstanding", new BigDecimal("1500.000000"),
                                "total_outstanding", new BigDecimal("1650.000000")))
                .put(AggregateKey.of(ReconciliationComparator.JOURNAL_ENTRIES, "USD", "1", "4001", ReconciliationComparator.CREDIT),
                        Map.of("entry_count", new BigDecimal("3"), "amount", new BigDecimal("900.000000")))
                .put(AggregateKey.of(ReconciliationComparator.JOURNAL_ENTRIES, "USD", "1", "5001", ReconciliationComparator.DEBIT),
                        Map.of("entry_count", new BigDecimal("3"), "amount", new BigDecimal("900.000000")))
                .put(AggregateKey.of("savings", "USD"),
                        Map.of("savings_count", new BigDecimal("1"), "account_balance", new BigDecimal("250.000000")))
                .build();

        assertThat(ReconciliationComparator.compareFinancials(ledger(), target)).isEmpty();
    }

    @Test
    void oneCentOfLostPrincipalIsFound() {
        FinancialSnapshot target = mutateLoanOutstanding(new BigDecimal("1499.999999"));

        assertThat(ReconciliationComparator.compareFinancials(ledger(), target)).singleElement().satisfies(variance -> {
            assertThat(variance.kind()).isEqualTo(Variance.Kind.AMOUNT_MISMATCH);
            assertThat(variance.detail()).isEqualTo("principal_outstanding");
            assertThat(variance.sourceValue()).isEqualTo("1500");
            assertThat(variance.targetValue()).isEqualTo("1499.999999");
        });
    }

    @Test
    void currencyDroppedByTheMigrationIsFound() {
        FinancialSnapshot source = FinancialSnapshot.builder()
                .put(AggregateKey.of("loans", "USD"), Map.of("total_outstanding", BigDecimal.TEN))
                .put(AggregateKey.of("loans", "EUR"), Map.of("total_outstanding", BigDecimal.ONE)).build();
        FinancialSnapshot target = FinancialSnapshot.builder()
                .put(AggregateKey.of("loans", "USD"), Map.of("total_outstanding", BigDecimal.TEN)).build();

        assertThat(ReconciliationComparator.compareFinancials(source, target)).extracting(Variance::kind, Variance::scope)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(Variance.Kind.MISSING_IN_TARGET, "loans(EUR)"));
    }

    @Test
    void balancedLedgerPassesTheTrialBalance() {
        assertThat(ReconciliationComparator.checkTrialBalance("target", ledger())).isEmpty();
    }

    @Test
    void droppedJournalLegBreaksTheTrialBalance() {
        FinancialSnapshot unbalanced = FinancialSnapshot.builder()
                .put(AggregateKey.of(ReconciliationComparator.JOURNAL_ENTRIES, "USD", "1", "4001", ReconciliationComparator.CREDIT),
                        Map.of("amount", new BigDecimal("900.00")))
                .put(AggregateKey.of(ReconciliationComparator.JOURNAL_ENTRIES, "USD", "1", "5001", ReconciliationComparator.DEBIT),
                        Map.of("amount", new BigDecimal("850.00")))
                .build();

        assertThat(ReconciliationComparator.checkTrialBalance("target", unbalanced)).singleElement().satisfies(variance -> {
            assertThat(variance.kind()).isEqualTo(Variance.Kind.TRIAL_BALANCE_IMBALANCE);
            assertThat(variance.sourceValue()).isEqualTo("850");
            assertThat(variance.targetValue()).isEqualTo("900");
        });
    }

    @Test
    void sequenceLeftBehindTheMigratedDataIsFound() {
        Map<String, Long> maxIds = Map.of("m_loan", 1200L, "acc_gl_journal_entry", 98_000L);
        Map<String, Long> nextValues = Map.of("m_loan", 1201L, "acc_gl_journal_entry", 1L);

        assertThat(ReconciliationComparator.checkSequences(maxIds, nextValues)).singleElement().satisfies(variance -> {
            assertThat(variance.kind()).isEqualTo(Variance.Kind.SEQUENCE_BEHIND_MAX_ID);
            assertThat(variance.scope()).isEqualTo("acc_gl_journal_entry");
        });
    }

    @Test
    void tableWithoutASequenceIsFound() {
        assertThat(ReconciliationComparator.checkSequences(Map.of("m_loan", 1200L), Map.of())).extracting(Variance::kind)
                .containsExactly(Variance.Kind.MISSING_IN_TARGET);
    }

    private static FinancialSnapshot ledger() {
        return FinancialSnapshot.builder()
                .put(AggregateKey.of("loans", "USD"),
                        Map.of("loan_count", new BigDecimal("2"), "principal_outstanding", new BigDecimal("1500.00"), "total_outstanding",
                                new BigDecimal("1650.00")))
                .put(AggregateKey.of(ReconciliationComparator.JOURNAL_ENTRIES, "USD", "1", "4001", ReconciliationComparator.CREDIT),
                        Map.of("entry_count", new BigDecimal("3"), "amount", new BigDecimal("900.00")))
                .put(AggregateKey.of(ReconciliationComparator.JOURNAL_ENTRIES, "USD", "1", "5001", ReconciliationComparator.DEBIT),
                        Map.of("entry_count", new BigDecimal("3"), "amount", new BigDecimal("900.00")))
                .put(AggregateKey.of("savings", "USD"),
                        Map.of("savings_count", new BigDecimal("1"), "account_balance", new BigDecimal("250.00")))
                .build();
    }

    private static FinancialSnapshot mutateLoanOutstanding(BigDecimal principalOutstanding) {
        FinancialSnapshot.Builder builder = FinancialSnapshot.builder();
        ledger().aggregates().forEach((key, measures) -> {
            if (key.equals(AggregateKey.of("loans", "USD"))) {
                builder.put(key, Map.of("loan_count", measures.get("loan_count"), "principal_outstanding", principalOutstanding,
                        "total_outstanding", measures.get("total_outstanding")));
            } else {
                builder.put(key, measures);
            }
        });
        return builder.build();
    }
}
