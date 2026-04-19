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
package jgnash.uifx.actions;

import java.io.File;
import java.util.List;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

import javafx.concurrent.Task;
import javafx.stage.FileChooser;

import jgnash.convert.importat.GenericImport;
import jgnash.convert.importat.ImportTransaction;
import jgnash.convert.importat.revolut.RevolutBank;
import jgnash.convert.importat.revolut.RevolutCsvParser;
import jgnash.engine.Account;
import jgnash.resource.util.ResourceUtils;
import jgnash.uifx.StaticUIMethods;
import jgnash.uifx.control.wizard.WizardDialogController;
import jgnash.uifx.util.JavaFXUtils;
import jgnash.uifx.views.main.MainView;
import jgnash.uifx.wizard.imports.ImportWizard;

/**
 * Utility class to import Revolut CSV files.
 */
public class ImportRevolutCsvAction {

    private static final String LAST_DIR = "importDir";

    private ImportRevolutCsvAction() {
        // Utility class
    }

    public static void showAndWait() {
        final ResourceBundle resources = ResourceUtils.getBundle();

        final FileChooser fileChooser = configureFileChooser();
        fileChooser.setTitle(resources.getString("Title.SelFile"));

        final File file = fileChooser.showOpenDialog(MainView.getPrimaryStage());

        if (file != null) {
            final Preferences pref = Preferences.userNodeForPackage(ImportRevolutCsvAction.class);
            pref.put(LAST_DIR, file.getParentFile().getAbsolutePath());

            new Thread(new ImportTask(file)).start();
        }
    }

    private static FileChooser configureFileChooser() {
        final Preferences pref = Preferences.userNodeForPackage(ImportRevolutCsvAction.class);
        final FileChooser fileChooser = new FileChooser();

        final File initialDirectory = new File(pref.get(LAST_DIR, System.getProperty("user.home")));

        if (initialDirectory.isDirectory()) {
            fileChooser.setInitialDirectory(initialDirectory);
        }

        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv", "*.CSV")
        );

        return fileChooser;
    }

    private static class ImportTask extends Task<RevolutBank> {

        private final File file;

        ImportTask(final File file) {
            this.file = file;
            setOnSucceeded(event -> onSuccess());
        }

        @Override
        protected RevolutBank call() throws Exception {
            final RevolutBank revolutBank = RevolutCsvParser.parse(file.toPath());

            if (revolutBank.getTransactions().isEmpty()) {
                JavaFXUtils.runLater(() -> StaticUIMethods.displayError(
                        ResourceUtils.getString("Message.Error.ParseTransactions")));
                cancel();
                return null;
            }

            return revolutBank;
        }

        private void onSuccess() {
            final RevolutBank revolutBank = getValue();

            final ImportWizard importWizard = new ImportWizard();

            final WizardDialogController<ImportWizard.Settings> wizardDialogController
                    = importWizard.wizardControllerProperty().get();

            wizardDialogController.setSetting(ImportWizard.Settings.BANK, revolutBank);

            importWizard.showAndWait();

            if (wizardDialogController.validProperty().get()) {
                final Account account = (Account) wizardDialogController.getSetting(ImportWizard.Settings.ACCOUNT);

                @SuppressWarnings("unchecked")
                final List<ImportTransaction> transactions = (List<ImportTransaction>) wizardDialogController
                        .getSetting(ImportWizard.Settings.TRANSACTIONS);

                final ImportTransactionsTask importTransactionsTask = new ImportTransactionsTask(account, transactions);

                new Thread(importTransactionsTask).start();

                StaticUIMethods.displayTaskProgress(importTransactionsTask);
            }
        }
    }

    private static class ImportTransactionsTask extends Task<Void> {

        private final Account account;
        private final List<ImportTransaction> transactions;

        ImportTransactionsTask(final Account account, final List<ImportTransaction> transactions) {
            this.account = account;
            this.transactions = transactions;
        }

        @Override
        public Void call() {
            updateMessage(ResourceUtils.getString("Message.PleaseWait"));
            updateProgress(-1, Long.MAX_VALUE);

            GenericImport.importTransactions(transactions, account);

            return null;
        }
    }
}
