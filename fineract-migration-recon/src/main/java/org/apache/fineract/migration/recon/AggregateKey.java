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

import java.util.List;

/**
 * Identifies one aggregated slice of a financial dataset, for example the loans of one currency or the credit entries
 * of one GL account in one office.
 */
public record AggregateKey(String dataset, List<String> keyParts) implements Comparable<AggregateKey> {

    public AggregateKey {
        keyParts = List.copyOf(keyParts);
    }

    public static AggregateKey of(String dataset, String... keyParts) {
        return new AggregateKey(dataset, List.of(keyParts));
    }

    public String label() {
        return dataset + "(" + String.join("/", keyParts) + ")";
    }

    @Override
    public int compareTo(AggregateKey other) {
        return label().compareTo(other.label());
    }
}
