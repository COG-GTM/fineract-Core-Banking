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
package org.apache.fineract.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Supplier;
import org.apache.fineract.command.core.Command;
import org.apache.fineract.command.core.CommandAuditor;
import org.apache.fineract.command.core.CommandHandler;
import org.apache.fineract.command.core.CommandProperties;
import org.apache.fineract.command.core.CommandRouter;
import org.apache.fineract.command.implementation.DefaultCommandPipeline;
import org.apache.fineract.command.implementation.DefaultCommandRouter;
import org.apache.fineract.command.implementation.SynchronousCommandExecutor;
import org.junit.jupiter.api.Test;

class CommandCoreUnitTest {

    @Test
    void routerReturnsMatchingHandlerAndRejectsUnknownCommands() {
        CommandHandler<String, String> handler = mock(CommandHandler.class);
        Command<String> command = new Command<>();
        command.setPayload("payload");
        when(handler.matches(command)).thenReturn(true);
        DefaultCommandRouter router = new DefaultCommandRouter(List.of(handler));

        assertThat(router.route(command)).isSameAs(handler);
        assertThatThrownBy(() -> router.route(null)).isInstanceOf(RuntimeException.class);

        when(handler.matches(command)).thenReturn(false);
        assertThatThrownBy(() -> router.route(command)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void synchronousExecutorAuditsSuccessAndFailure() {
        CommandRouter router = mock(CommandRouter.class);
        CommandAuditor auditor = mock(CommandAuditor.class);
        CommandHandler<String, String> handler = mock(CommandHandler.class);
        Command<String> command = new Command<>();
        command.setPayload("payload");
        doReturn(handler).when(router).route(command);
        when(handler.handle(command)).thenReturn("response");
        SynchronousCommandExecutor executor = new SynchronousCommandExecutor(router, auditor);

        Supplier<String> result = executor.execute(command);

        assertThat(result.get()).isEqualTo("response");
        verify(auditor).processing(command);
        verify(auditor).processed(command, "response");

        RuntimeException failure = new RuntimeException("failure");
        doThrow(failure).when(handler).handle(command);
        assertThatThrownBy(result::get).isSameAs(failure);
        assertThat(command.getError()).isEqualTo("failure");
        verify(auditor).error(command);
    }

    @Test
    void pipelineRejectsNullAndPopulatesExecutionAttributes() {
        CommandProperties properties = CommandProperties.builder().build();
        CommandExecutorStub executor = new CommandExecutorStub();
        DefaultCommandPipeline pipeline = new DefaultCommandPipeline(executor, properties);
        Command<String> command = new Command<>();
        command.setPayload("payload");

        assertThatThrownBy(() -> pipeline.send(null)).isInstanceOf(NullPointerException.class);
        assertThat(pipeline.send(command)).isSameAs(executor.result);
        assertThat(command.getCreatedAt()).isNotNull();
        assertThat(executor.command).isSameAs(command);
    }

    private static final class CommandExecutorStub implements org.apache.fineract.command.core.CommandExecutor {

        private final Supplier<String> result = () -> "ok";
        private Command<?> command;

        @Override
        public <RequestT, ResponseT> Supplier<ResponseT> execute(Command<RequestT> command) {
            this.command = command;
            return (Supplier<ResponseT>) result;
        }
    }
}
