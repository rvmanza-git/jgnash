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

import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RevolutCsvParserTest {

    @Test
    void parseSampleCsv() throws Exception {
        final RevolutBank bank = RevolutCsvParser.parse(getSamplePath());

        assertNotNull(bank);
        assertEquals(2, bank.getTransactions().size());

        assertEquals("Coffee Shop", bank.getTransactions().get(0).getPayee());
        assertEquals(new BigDecimal("-4.50"), bank.getTransactions().get(0).getAmount());
        assertEquals(LocalDate.of(2026, 1, 1), bank.getTransactions().get(0).getDatePosted());
        assertEquals("rev-1", bank.getTransactions().get(0).getFITID());

        assertEquals("Salary", bank.getTransactions().get(1).getPayee());
        assertEquals(new BigDecimal("2500.00"), bank.getTransactions().get(1).getAmount());
        assertEquals(LocalDate.of(2026, 1, 2), bank.getTransactions().get(1).getDatePosted());
    }

    private Path getSamplePath() throws URISyntaxException {
        return Path.of(getClass().getClassLoader().getResource("revolut-sample.csv").toURI());
    }
}
