/*
 * jGnash, a personal finance application
 * Copyright (C) 2001-2020 Craig Cavanaugh
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package jgnash.convert.importat.revolut;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import jgnash.convert.importat.ImportTransaction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for Revolut CSV parsing with payee mappings.
 */
public class RevolutPayeeMappingTest {

    @Test
    public void testLoadMappings() throws Exception {
        final Path mappingFile = getMappingSamplePath();

        final Map<String, String> mappings = RevolutPayeeMappingLoader.loadMappings(mappingFile);

        assertNotNull(mappings);
        assertEquals(2, mappings.size());
        assertEquals("Expenses:Dining", mappings.get("coffee shop"));
        assertEquals("Expenses:Salary", mappings.get("salary"));
    }

    @Test
    public void testParseWithMappings() throws Exception {
        final Path csvFile = getSamplePath();
        final Path mappingFile = getMappingSamplePath();

        final Map<String, String> mappings = RevolutPayeeMappingLoader.loadMappings(mappingFile);
        final RevolutBank bank = RevolutCsvParser.parse(csvFile, mappings);

        assertNotNull(bank);
        final List<ImportTransaction> transactions = bank.getTransactions();

        assertEquals(2, transactions.size());

        // First transaction: Coffee Shop with mapping to Expenses:Dining
        final ImportTransaction first = transactions.get(0);
        assertEquals("Coffee Shop", first.getPayee());
        assertEquals("Expenses:Dining", first.getAccountTo());

        // Second transaction: Salary with mapping to Expenses:Salary
        final ImportTransaction second = transactions.get(1);
        assertEquals("Salary", second.getPayee());
        assertEquals("Expenses:Salary", second.getAccountTo());
    }

    @Test
    public void testParseWithoutMappings() throws Exception {
        final Path csvFile = getSamplePath();

        final RevolutBank bank = RevolutCsvParser.parse(csvFile);

        assertNotNull(bank);
        final List<ImportTransaction> transactions = bank.getTransactions();

        assertEquals(2, transactions.size());

        // Verify accountTo is null when no mapping is provided
        for (final ImportTransaction transaction : transactions) {
            assertNull(transaction.getAccountTo());
        }
    }

    private Path getSamplePath() throws URISyntaxException {
        return Path.of(getClass().getClassLoader().getResource("revolut-sample.csv").toURI());
    }

    private Path getMappingSamplePath() throws URISyntaxException {
        return Path.of(getClass().getClassLoader().getResource("revolut-mapping-sample.csv").toURI());
    }
}
