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
package org.apache.fineract.commands.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CommandWrapperBuilderTest {

    @Test
    void allNoArgumentBuildersProduceIndependentCommandWrappers() {
        long builderCount = Stream.of(CommandWrapperBuilder.class.getMethods()).filter(this::isNoArgumentBuilder).peek(method -> {
            CommandWrapperBuilder builder = new CommandWrapperBuilder();
            assertThatCode(() -> method.invoke(builder)).doesNotThrowAnyException();
            CommandWrapper first = builder.build();
            CommandWrapper second = builder.build();

            assertThat(first.getActionName()).isEqualTo(second.getActionName());
            assertThat(first.getEntityName()).isEqualTo(second.getEntityName());
            assertThat(first.getHref()).isEqualTo(second.getHref());
            assertThat(first.getJson()).isEqualTo(second.getJson());
        }).count();

        assertThat(builderCount).isGreaterThanOrEqualTo(60);
    }

    @Test
    void withMethodsPopulateIdsAndBuildSnapshotsAreImmutable() {
        CommandWrapperBuilder builder = new CommandWrapperBuilder().withJson("{\"name\":\"before\"}").withEntityName("CLIENT")
                .withClientId(11L).withGroupId(12L).withLoanId(13L).withSavingsId(14L).withSubEntityId(15L);
        CommandWrapper first = builder.build();

        builder.withJson("{\"name\":\"after\"}").withEntityName("LOAN").withClientId(21L);
        CommandWrapper second = builder.build("request-2");

        assertThat(first.getJson()).isEqualTo("{\"name\":\"before\"}");
        assertThat(first.getEntityName()).isEqualTo("CLIENT");
        assertThat(first.getClientId()).isEqualTo(11L);
        assertThat(first.getGroupId()).isEqualTo(12L);
        assertThat(first.getLoanId()).isEqualTo(13L);
        assertThat(first.getSavingsId()).isEqualTo(14L);
        assertThat(first.getSubentityId()).isEqualTo(15L);
        assertThat(second.getJson()).isEqualTo("{\"name\":\"after\"}");
        assertThat(second.getEntityName()).isEqualTo("LOAN");
        assertThat(second.getClientId()).isEqualTo(21L);
        assertThat(second.getIdempotencyKey()).isEqualTo("request-2");
    }

    private boolean isNoArgumentBuilder(Method method) {
        return Modifier.isPublic(method.getModifiers()) && method.getReturnType().equals(CommandWrapperBuilder.class)
                && method.getParameterCount() == 0 && !method.getName().equals("build");
    }

    @ParameterizedTest
    @MethodSource("representativeBuilders")
    void representativeBuildersPopulateCommandMetadata(Function<CommandWrapperBuilder, CommandWrapperBuilder> operation) {
        CommandWrapper wrapper = operation.apply(new CommandWrapperBuilder()).build();

        assertThat(wrapper.getActionName()).isNotBlank();
        assertThat(wrapper.getEntityName()).isNotBlank();
        assertThat(wrapper.getHref()).isNotBlank();
        assertThat(wrapper.getEntityId()).isNull();
    }

    private static Stream<Arguments> representativeBuilders() {
        return Stream.of(Arguments.of((Function<CommandWrapperBuilder, CommandWrapperBuilder>) CommandWrapperBuilder::createClient),
                Arguments.of((Function<CommandWrapperBuilder, CommandWrapperBuilder>) CommandWrapperBuilder::createOffice),
                Arguments.of((Function<CommandWrapperBuilder, CommandWrapperBuilder>) CommandWrapperBuilder::createStaff),
                Arguments.of((Function<CommandWrapperBuilder, CommandWrapperBuilder>) CommandWrapperBuilder::createFund),
                Arguments.of((Function<CommandWrapperBuilder, CommandWrapperBuilder>) CommandWrapperBuilder::createReport),
                Arguments.of((Function<CommandWrapperBuilder, CommandWrapperBuilder>) CommandWrapperBuilder::createSms),
                Arguments.of((Function<CommandWrapperBuilder, CommandWrapperBuilder>) CommandWrapperBuilder::createCode),
                Arguments.of((Function<CommandWrapperBuilder, CommandWrapperBuilder>) CommandWrapperBuilder::createHook),
                Arguments.of((Function<CommandWrapperBuilder, CommandWrapperBuilder>) CommandWrapperBuilder::createCharge),
                Arguments.of((Function<CommandWrapperBuilder, CommandWrapperBuilder>) CommandWrapperBuilder::createCollateral),
                Arguments.of((Function<CommandWrapperBuilder, CommandWrapperBuilder>) CommandWrapperBuilder::createLoanProduct),
                Arguments.of(
                        (Function<CommandWrapperBuilder, CommandWrapperBuilder>) CommandWrapperBuilder::createWorkingCapitalLoanProduct),
                Arguments.of((Function<CommandWrapperBuilder, CommandWrapperBuilder>) CommandWrapperBuilder::getCreditReport),
                Arguments.of((Function<CommandWrapperBuilder, CommandWrapperBuilder>) CommandWrapperBuilder::updatePermissions),
                Arguments.of((Function<CommandWrapperBuilder, CommandWrapperBuilder>) CommandWrapperBuilder::createRole));
    }
}
