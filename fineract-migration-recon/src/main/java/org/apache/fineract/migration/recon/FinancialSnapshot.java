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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The financial position of one tenant database, aggregated so that it can be compared across engines without pulling
 * every row over the wire. Measures are kept as {@link BigDecimal} and compared by value, not by scale, because MariaDB
 * and PostgreSQL do not agree on the trailing zeroes of a {@code DECIMAL(19,6)}.
 */
public record FinancialSnapshot(SortedMap<AggregateKey, Map<String, BigDecimal>> aggregates) {

    public FinancialSnapshot {
        aggregates = new TreeMap<>(aggregates);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final SortedMap<AggregateKey, Map<String, BigDecimal>> aggregates = new TreeMap<>();

        public Builder put(AggregateKey key, Map<String, BigDecimal> measures) {
            aggregates.put(key, new LinkedHashMap<>(measures));
            return this;
        }

        public FinancialSnapshot build() {
            return new FinancialSnapshot(aggregates);
        }
    }
}
