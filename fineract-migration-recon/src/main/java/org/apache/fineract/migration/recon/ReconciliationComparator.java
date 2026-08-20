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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Compares two snapshots. Pure logic with no database access, so it can be exercised against fixtures carrying
 * deliberately seeded discrepancies.
 */
public final class ReconciliationComparator {

    static final String JOURNAL_ENTRIES = "journalEntries";
    static final String AMOUNT = "amount";
    static final String CREDIT = "1";
    static final String DEBIT = "2";

    private ReconciliationComparator() {}

    public static List<Variance> compareRowCounts(Map<String, Long> source, Map<String, Long> target) {
        List<Variance> variances = new ArrayList<>();
        for (String table : union(source.keySet(), target.keySet())) {
            Long sourceCount = source.get(table);
            Long targetCount = target.get(table);
            if (sourceCount == null) {
                variances.add(
                        new Variance(Variance.Kind.EXTRA_IN_TARGET, table, "table absent from source", null, String.valueOf(targetCount)));
            } else if (targetCount == null) {
                variances.add(new Variance(Variance.Kind.MISSING_IN_TARGET, table, "table absent from target", String.valueOf(sourceCount),
                        null));
            } else if (!sourceCount.equals(targetCount)) {
                variances.add(new Variance(Variance.Kind.ROW_COUNT_MISMATCH, table, "row count differs", String.valueOf(sourceCount),
                        String.valueOf(targetCount)));
            }
        }
        return variances;
    }

    public static List<Variance> compareFinancials(FinancialSnapshot source, FinancialSnapshot target) {
        List<Variance> variances = new ArrayList<>();
        Set<AggregateKey> keys = new TreeSet<>();
        keys.addAll(source.aggregates().keySet());
        keys.addAll(target.aggregates().keySet());
        for (AggregateKey key : keys) {
            Map<String, BigDecimal> sourceMeasures = source.aggregates().get(key);
            Map<String, BigDecimal> targetMeasures = target.aggregates().get(key);
            if (sourceMeasures == null) {
                variances.add(new Variance(Variance.Kind.EXTRA_IN_TARGET, key.label(), "aggregate absent from source", null,
                        String.valueOf(targetMeasures)));
                continue;
            }
            if (targetMeasures == null) {
                variances.add(new Variance(Variance.Kind.MISSING_IN_TARGET, key.label(), "aggregate absent from target",
                        String.valueOf(sourceMeasures), null));
                continue;
            }
            for (String measure : union(sourceMeasures.keySet(), targetMeasures.keySet())) {
                BigDecimal sourceValue = sourceMeasures.get(measure);
                BigDecimal targetValue = targetMeasures.get(measure);
                if (!equalByValue(sourceValue, targetValue)) {
                    variances.add(new Variance(Variance.Kind.AMOUNT_MISMATCH, key.label(), measure, text(sourceValue), text(targetValue)));
                }
            }
        }
        return variances;
    }

    /**
     * Debits must equal credits per currency. A migration can reproduce both sides of the ledger faithfully and still
     * be wrong if it drops one leg of a journal entry, and the row counts alone would not show it.
     */
    public static List<Variance> checkTrialBalance(String side, FinancialSnapshot snapshot) {
        Map<String, BigDecimal> credits = new java.util.TreeMap<>();
        Map<String, BigDecimal> debits = new java.util.TreeMap<>();
        snapshot.aggregates().forEach((key, measures) -> {
            if (!JOURNAL_ENTRIES.equals(key.dataset())) {
                return;
            }
            String currency = key.keyParts().isEmpty() ? "" : key.keyParts().get(0);
            String typeEnum = key.keyParts().get(key.keyParts().size() - 1);
            BigDecimal amount = measures.getOrDefault(AMOUNT, BigDecimal.ZERO);
            Map<String, BigDecimal> bucket = CREDIT.equals(typeEnum) ? credits : DEBIT.equals(typeEnum) ? debits : null;
            if (bucket != null) {
                bucket.merge(currency, amount, BigDecimal::add);
            }
        });
        List<Variance> variances = new ArrayList<>();
        for (String currency : union(credits.keySet(), debits.keySet())) {
            BigDecimal credit = credits.getOrDefault(currency, BigDecimal.ZERO);
            BigDecimal debit = debits.getOrDefault(currency, BigDecimal.ZERO);
            if (credit.compareTo(debit) != 0) {
                variances.add(new Variance(Variance.Kind.TRIAL_BALANCE_IMBALANCE, side + " trial balance " + currency,
                        "debits " + text(debit) + " do not equal credits " + text(credit), text(debit), text(credit)));
            }
        }
        return variances;
    }

    /**
     * Identity/sequence generators are not carried by a logical dump or by DMS. A target whose sequence sits at or
     * below the highest migrated id reconciles perfectly and then collides on the first insert after cutover.
     */
    public static List<Variance> checkSequences(Map<String, Long> maxIdByTable, Map<String, Long> nextSequenceValueByTable) {
        List<Variance> variances = new ArrayList<>();
        for (Map.Entry<String, Long> entry : maxIdByTable.entrySet()) {
            Long nextValue = nextSequenceValueByTable.get(entry.getKey());
            if (nextValue == null) {
                variances.add(new Variance(Variance.Kind.MISSING_IN_TARGET, entry.getKey(), "no sequence found for migrated table",
                        String.valueOf(entry.getValue()), null));
            } else if (nextValue <= entry.getValue()) {
                variances.add(new Variance(Variance.Kind.SEQUENCE_BEHIND_MAX_ID, entry.getKey(), "next sequence value would collide",
                        String.valueOf(entry.getValue()), String.valueOf(nextValue)));
            }
        }
        return variances;
    }

    private static boolean equalByValue(BigDecimal source, BigDecimal target) {
        if (source == null || target == null) {
            return source == target;
        }
        return source.compareTo(target) == 0;
    }

    private static String text(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        Set<String> all = new TreeSet<>(first);
        all.addAll(second);
        return all;
    }
}
