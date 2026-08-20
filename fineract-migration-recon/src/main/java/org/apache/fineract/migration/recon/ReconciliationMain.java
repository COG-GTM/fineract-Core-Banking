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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entry point. Exits non-zero on any variance so a pipeline stage cannot pass a migration that lost money. */
public final class ReconciliationMain {

    private static final Logger LOG = LoggerFactory.getLogger(ReconciliationMain.class);

    private ReconciliationMain() {}

    public static void main(String[] args) throws IOException, SQLException {
        if (args.length != 1) {
            LOG.error("usage: reconcile <recon.properties>");
            System.exit(2);
        }
        ReconConfig config = ReconConfig.load(Path.of(args[0]));
        ReconciliationReport report = new ReconciliationRunner(config).run();
        write(config.outputDirectory(), report);
        LOG.info("Reconciliation complete:\n{}", report.summary());
        System.exit(report.zeroVariance() ? 0 : 1);
    }

    static void write(Path outputDirectory, ReconciliationReport report) throws IOException {
        Files.createDirectories(outputDirectory);
        Files.writeString(outputDirectory.resolve("reconciliation-report.txt"), report.summary());
        objectMapper().writeValue(outputDirectory.resolve("reconciliation-report.json").toFile(), report);
    }

    static ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }
}
