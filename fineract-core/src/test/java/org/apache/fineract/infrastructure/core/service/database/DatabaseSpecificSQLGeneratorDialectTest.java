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
package org.apache.fineract.infrastructure.core.service.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Per-statement checks of {@link DatabaseSpecificSQLGenerator} on every {@link DatabaseType}.
 */
public class DatabaseSpecificSQLGeneratorDialectTest {

    private DatabaseTypeResolver databaseTypeResolver;
    private DatabaseSpecificSQLGenerator generator;

    @BeforeEach
    void setUp() {
        databaseTypeResolver = mock(DatabaseTypeResolver.class);
        generator = new DatabaseSpecificSQLGenerator(databaseTypeResolver, mock(RoutingDataSource.class));
    }

    private void useDialect(DatabaseType type) {
        when(databaseTypeResolver.databaseType()).thenReturn(type);
        when(databaseTypeResolver.isMySQL()).thenReturn(type.isMySql());
        when(databaseTypeResolver.isPostgreSQL()).thenReturn(type.isPostgres());
    }

    @ParameterizedTest
    @EnumSource(DatabaseType.class)
    void limitEmitsStandardFormOnEveryDialect(DatabaseType type) {
        useDialect(type);
        assertEquals("LIMIT 10 OFFSET 0", generator.limit(10));
        assertEquals("LIMIT 15 OFFSET 30", generator.limit(15, 30));
    }

    @ParameterizedTest
    @EnumSource(DatabaseType.class)
    void escapeUsesDialectIdentifierQuoting(DatabaseType type) {
        useDialect(type);
        String expected = type.isMySql() ? "`m_office`" : "\"m_office\"";
        assertEquals(expected, generator.escape("m_office"));
    }

    @ParameterizedTest
    @EnumSource(DatabaseType.class)
    void buildersQuoteEveryIdentifier(DatabaseType type) {
        useDialect(type);
        String q = type.isMySql() ? "`" : "\"";
        assertEquals("SELECT t.%1$sid%1$s, t.%1$sname%1$s".formatted(q), generator.buildSelect(List.of("id", "name"), "t", false));
        assertEquals("FROM %1$sm_office%1$s o".formatted(q), generator.buildFrom("m_office", "o", false));
        assertEquals("LEFT JOIN %1$sm_staff%1$s s ON  s.%1$soffice_id%1$s = o.%1$sid%1$s".formatted(q),
                generator.buildJoin("m_staff", "s", "office_id", "o", "id", "LEFT"));
        assertEquals("INSERT INTO %1$sdt%1$s(%1$sa%1$s, %1$sb%1$s) VALUES (?, ?)".formatted(q),
                generator.buildInsert("dt", List.of("a", "b"), Map.of()));
        assertEquals("UPDATE %1$sdt%1$s SET %1$sa%1$s = ?, %1$sb%1$s = ?".formatted(q),
                generator.buildUpdate("dt", List.of("a", "b"), Map.of()));
    }

    @Test
    void unresolvedDialectIsRejected() {
        when(databaseTypeResolver.isMySQL()).thenReturn(false);
        when(databaseTypeResolver.isPostgreSQL()).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> generator.escape("x"));
        assertThrows(IllegalStateException.class, () -> generator.limit(1, 0));
        assertThrows(IllegalStateException.class, () -> generator.groupConcat("x"));
        assertThrows(IllegalStateException.class, () -> generator.castChar("x"));
        assertThrows(IllegalStateException.class, () -> generator.castInteger("x"));
        assertThrows(IllegalStateException.class, () -> generator.castJson("x"));
        assertThrows(IllegalStateException.class, () -> generator.currentSchema());
        assertThrows(IllegalStateException.class, () -> generator.subDate("d", "1", "DAY"));
        assertThrows(IllegalStateException.class, () -> generator.dateDiff("a", "b"));
    }

    @ParameterizedTest
    @EnumSource(DatabaseType.class)
    void groupConcat(DatabaseType type) {
        useDialect(type);
        assertEquals(type.isMySql() ? "GROUP_CONCAT(x)" : "STRING_AGG(x::varchar, ',')", generator.groupConcat("x"));
    }

    @ParameterizedTest
    @EnumSource(DatabaseType.class)
    void casts(DatabaseType type) {
        useDialect(type);
        assertEquals(type.isMySql() ? "CAST(x AS CHAR) COLLATE utf8mb4_unicode_ci" : "x::CHAR", generator.castChar("x"));
        assertEquals(type.isMySql() ? "CAST(x AS SIGNED INTEGER)" : "x::INTEGER", generator.castInteger("x"));
        assertEquals(type.isMySql() ? "x" : "x ::json", generator.castJson("x"));
    }

    @ParameterizedTest
    @EnumSource(DatabaseType.class)
    void schemaAndDates(DatabaseType type) {
        useDialect(type);
        assertEquals(type.isMySql() ? "SCHEMA()" : "CURRENT_SCHEMA()", generator.currentSchema());
        assertEquals(type.isMySql() ? "DATE_SUB(d, INTERVAL 3 DAY)" : "(d::TIMESTAMP - 3 * INTERVAL '1 DAY')",
                generator.subDate("d", "3", "DAY"));
        assertEquals(type.isMySql() ? "DATEDIFF(a, b)" : "EXTRACT(DAY FROM (a::TIMESTAMP - b::TIMESTAMP))", generator.dateDiff("a", "b"));
        assertEquals(type.isMySql() ? " DATE_ADD(d, INTERVAL 1 DAY) " : " d+1", generator.incrementDateByOneDay("d"));
    }

    @ParameterizedTest
    @EnumSource(DatabaseType.class)
    void inClause(DatabaseType type) {
        useDialect(type);
        assertEquals(type.isMySql() ? "id IN (?,?,?)" : "id = ANY (?)", generator.in("id", List.of(1L, 2L, 3L)));
    }

    @ParameterizedTest
    @EnumSource(DatabaseType.class)
    void foundRowsOnlyOnMySql(DatabaseType type) {
        useDialect(type);
        assertEquals(type.isMySql() ? "SQL_CALC_FOUND_ROWS" : "", generator.calcFoundRows());
        assertEquals(type.isMySql() ? "SELECT FOUND_ROWS()" : "SELECT COUNT(*) FROM (SELECT 1 FROM t) AS temp",
                generator.countLastExecutedQueryResult("SELECT 1 FROM t LIMIT 5 OFFSET 10"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "SELECT 1 FROM t LIMIT 5 OFFSET 10", "SELECT 1 FROM t limit 5 offset 10", "SELECT 1 FROM t Limit 5 Offset 10",
            "SELECT 1 FROM t LIMIT 10,5", "SELECT 1 FROM t LIMIT 10 , 5", "SELECT 1 FROM t  limit  5",
            "SELECT 1 FROM t OFFSET 10 LIMIT 5" })
    void countQueryResultStripsEveryLimitOffsetForm(String sql) {
        assertEquals("SELECT COUNT(*) FROM (SELECT 1 FROM t) AS temp", generator.countQueryResult(sql));
    }

    @Test
    void countQueryResultKeepsIdentifiersContainingLimitOrOffset() {
        String sql = "SELECT credit_limit, offset_amount FROM t WHERE limit_type = 1";
        assertEquals("SELECT COUNT(*) FROM (" + sql + ") AS temp", generator.countQueryResult(sql));
    }
}
