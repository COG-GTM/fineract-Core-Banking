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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReconciliationReportTest {

    @Test
    void cleanTenantsGiveZeroVariance() {
        ReconciliationReport report = report(List.of());

        assertThat(report.zeroVariance()).isTrue();
        assertThat(report.summary()).contains("RESULT: ZERO VARIANCE").contains("Signed off by (DBA)");
    }

    @Test
    void aReportWithNoTenantsIsNotAPass() {
        ReconciliationReport report = new ReconciliationReport(Instant.EPOCH, "source", "target", List.of());

        assertThat(report.zeroVariance()).isFalse();
    }

    @Test
    void varianceIsReportedAndSurvivesSerialisation(@TempDir Path outputDirectory) throws IOException {
        ReconciliationReport report = report(
                List.of(new Variance(Variance.Kind.AMOUNT_MISMATCH, "loans(USD)", "principal_outstanding", "1500", "1499.999999")));

        ReconciliationMain.write(outputDirectory, report);

        assertThat(report.zeroVariance()).isFalse();
        assertThat(Files.readString(outputDirectory.resolve("reconciliation-report.txt"))).contains("RESULT: VARIANCE FOUND (1)");
        assertThat(Files.readString(outputDirectory.resolve("reconciliation-report.json"))).contains("AMOUNT_MISMATCH")
                .contains("1499.999999");
    }

    private static ReconciliationReport report(List<Variance> variances) {
        return new ReconciliationReport(Instant.EPOCH, "mariadb://source:3306", "postgresql://target:5432",
                List.of(new ReconciliationReport.TenantResult("default", 3, 99_650L, 99_650L, 4, variances)));
    }
}
