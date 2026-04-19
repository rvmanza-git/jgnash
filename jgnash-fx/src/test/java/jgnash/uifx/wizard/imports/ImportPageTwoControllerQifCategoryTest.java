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
package jgnash.uifx.wizard.imports;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import jgnash.convert.importat.ImportTransaction;
import jgnash.convert.importat.qif.QifImport;
import jgnash.convert.importat.qif.QifTransaction;
import jgnash.engine.Account;
import jgnash.engine.AccountType;
import jgnash.engine.CurrencyNode;
import jgnash.engine.DataStoreType;
import jgnash.engine.Engine;
import jgnash.engine.EngineFactory;
import jgnash.engine.jpa.JpaH2DataStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportPageTwoControllerQifCategoryTest {

    @Test
    void qifCategoryOverridesBayesPrediction() throws Exception {
        final String testFile = Files.createTempFile("qif-category-", JpaH2DataStore.H2_FILE_EXT).toString();

        Files.deleteIfExists(new File(testFile).toPath());
        EngineFactory.deleteDatabase(testFile);

        EngineFactory.closeEngine(EngineFactory.DEFAULT);

        final Engine engine = EngineFactory.bootLocalEngine(testFile, EngineFactory.DEFAULT, EngineFactory.EMPTY_PASSWORD,
                DataStoreType.H2_DATABASE);

        try {
            final CurrencyNode currency = engine.getDefaultCurrency();

            final Account baseBank = new Account(AccountType.BANK, currency);
            baseBank.setName("Checking");
            assertTrue(engine.addAccount(engine.getRootAccount(), baseBank));

            final Account transferBank = new Account(AccountType.BANK, currency);
            transferBank.setName("Savings");
            assertTrue(engine.addAccount(engine.getRootAccount(), transferBank));

            final Account expenseRoot = new Account(AccountType.EXPENSE, currency);
            expenseRoot.setName("Expenses");
            assertTrue(engine.addAccount(engine.getRootAccount(), expenseRoot));

            final Account foodExpense = new Account(AccountType.EXPENSE, currency);
            foodExpense.setName("Food");
            assertTrue(engine.addAccount(expenseRoot, foodExpense));

            final File qifFile = Files.createTempFile("qif-import-", ".qif").toFile();

            final String qif = "!Type:Bank\n"
                    + "D01/01/26\n"
                    + "T-10.00\n"
                    + "PGrocery\n"
                    + "LExpenses:Food\n"
                    + "^\n"
                    + "D01/02/26\n"
                    + "T-15.00\n"
                    + "PTransfer\n"
                    + "L[Savings]\n"
                    + "^\n";

            Files.writeString(qifFile.toPath(), qif, StandardCharsets.UTF_8);

            final QifImport qifImport = new QifImport();
            assertTrue(qifImport.doPartialParse(qifFile));

            final List<QifTransaction> qifTransactions = qifImport.getParser().getBank().getTransactions();
            assertEquals(2, qifTransactions.size());

            for (final QifTransaction qifTransaction : qifTransactions) {
                qifTransaction.setAccount(baseBank);
            }

            final List<ImportTransaction> importTransactions = new ArrayList<>(qifTransactions);

            final Method overrideMethod = ImportPageTwoController.class
                    .getDeclaredMethod("applyQifCategoryOverrides", List.class);
            overrideMethod.setAccessible(true);
            overrideMethod.invoke(new ImportPageTwoController(), importTransactions);

                assertEquals(foodExpense.getUuid(), importTransactions.get(0).getAccount().getUuid(),
                    () -> "Category='" + qifTransactions.get(0).category + "' mapped to account='"
                        + importTransactions.get(0).getAccount().getPathName() + "'");
                assertEquals(transferBank.getUuid(), importTransactions.get(1).getAccount().getUuid(),
                    () -> "Category='" + qifTransactions.get(1).category + "' mapped to account='"
                        + importTransactions.get(1).getAccount().getPathName() + "'");
        } finally {
            EngineFactory.closeEngine(EngineFactory.DEFAULT);
            EngineFactory.deleteDatabase(testFile);
        }
    }
}
