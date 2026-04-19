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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.prefs.Preferences;

import javafx.concurrent.Task;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;

import jgnash.convert.importat.GenericImport;
import jgnash.convert.importat.ImportTransaction;
import jgnash.convert.importat.revolut.RevolutBank;
import jgnash.convert.importat.revolut.RevolutCsvParser;
import jgnash.convert.importat.revolut.RevolutPayeeMappingLoader;
import jgnash.engine.Account;
import jgnash.engine.Engine;
import jgnash.engine.EngineFactory;
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

            // Ask user if they want to use a payee mapping file
            final Map<String, String> mappings = askForMappingFile(pref);

            new Thread(new ImportTask(file, mappings)).start();
        }
    }

    private static Map<String, String> askForMappingFile(final Preferences pref) {
        final ButtonType response = StaticUIMethods.showConfirmationDialog(
            "Use Payee Mapping File?",
            "Would you like to use a payee mapping file to pre-select destination accounts?");

        if (!response.getButtonData().isDefaultButton()) {
            return Map.of();
        }

        final FileChooser mappingChooser = new FileChooser();
        final File initialDirectory = new File(pref.get(LAST_DIR, System.getProperty("user.home")));

        if (initialDirectory.isDirectory()) {
            mappingChooser.setInitialDirectory(initialDirectory);
        }

        mappingChooser.setTitle("Select Payee Mapping File");
        mappingChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv", "*.CSV")
        );

        final File mappingFile = mappingChooser.showOpenDialog(MainView.getPrimaryStage());

        if (mappingFile != null) {
            try {
                final Map<String, String> mappings = RevolutPayeeMappingLoader.loadMappings(mappingFile.toPath());

                if (mappings.isEmpty()) {
                    StaticUIMethods.displayWarning(
                            "No mappings were loaded from the selected file. "
                                    + "Please verify the CSV has Payee and Destination account columns.");
                }

                return mappings;
            } catch (final Exception e) {
                StaticUIMethods.displayError("Failed to load mapping file: " + e.getMessage());
            }
        }

        return Map.of();
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
        private final Map<String, String> payeeMappings;

        ImportTask(final File file, final Map<String, String> payeeMappings) {
            this.file = file;
            this.payeeMappings = payeeMappings;
            setOnSucceeded(event -> onSuccess());
        }

        @Override
        protected RevolutBank call() throws Exception {
            final RevolutBank revolutBank = RevolutCsvParser.parse(file.toPath(), payeeMappings);

            if (!payeeMappings.isEmpty()) {
                final long matchedMappings = revolutBank.getTransactions().stream()
                        .filter(t -> t.getAccountTo() != null && !t.getAccountTo().isEmpty())
                        .count();

                if (matchedMappings == 0) {
                    JavaFXUtils.runLater(() -> StaticUIMethods.displayWarning(
                            "Loaded " + payeeMappings.size() + " mapping entries, but none matched imported payees. "
                                    + "Please verify payee text in the mapping file matches Revolut payee values."));
                }
            }

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
                wizardDialogController.setSetting(ImportWizard.Settings.PAYEE_MAPPINGS, payeeMappings);

            importWizard.showAndWait();

            if (wizardDialogController.validProperty().get()) {
                final Account account = (Account) wizardDialogController.getSetting(ImportWizard.Settings.ACCOUNT);

                @SuppressWarnings("unchecked")
                final List<ImportTransaction> transactions = (List<ImportTransaction>) wizardDialogController
                        .getSetting(ImportWizard.Settings.TRANSACTIONS);

                applyMappingsToTransactions(transactions, payeeMappings);

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

    private static void applyMappingsToTransactions(final List<ImportTransaction> transactions,
                                                    final Map<String, String> payeeMappings) {
        if (transactions == null || transactions.isEmpty() || payeeMappings == null || payeeMappings.isEmpty()) {
            return;
        }

        final Engine engine = EngineFactory.getEngine(EngineFactory.DEFAULT);
        if (engine == null) {
            return;
        }

        final List<Account> allAccounts = collectAllAccounts(engine);

        int resolved = 0;

        for (final ImportTransaction transaction : transactions) {
            final String mappedPath = findMappedPath(transaction.getPayee(), payeeMappings);
            if (mappedPath == null || mappedPath.isEmpty()) {
                continue;
            }

            final Account account = findAccountByPathName(allAccounts, mappedPath);
            if (account != null) {
                transaction.setAccount(account);
                resolved++;
            }
        }

        if (resolved == 0) {
            StaticUIMethods.displayWarning("Mapping file was loaded but no destination accounts were resolved "
                    + "at final import stage.");
        }
    }

    private static String findMappedPath(final String payee, final Map<String, String> payeeMappings) {
        final String normalizedPayee = normalizePayee(payee);

        String mappedPath = payeeMappings.get(normalizedPayee);
        if (mappedPath != null && !mappedPath.isEmpty()) {
            return mappedPath;
        }

        String bestKey = null;
        for (final String mappingKey : payeeMappings.keySet()) {
            if (mappingKey.isEmpty()) {
                continue;
            }

            if (normalizedPayee.contains(mappingKey) || mappingKey.contains(normalizedPayee)) {
                if (bestKey == null || mappingKey.length() > bestKey.length()) {
                    bestKey = mappingKey;
                }
            }
        }

        if (bestKey != null) {
            mappedPath = payeeMappings.get(bestKey);
            if (mappedPath != null && !mappedPath.isEmpty()) {
                return mappedPath;
            }
        }

        return null;
    }

    private static List<Account> collectAllAccounts(final Engine engine) {
        final Set<Account> accounts = new LinkedHashSet<>();

        addAccountsRecursively(engine.getAccountList(), accounts);
        addAccountsRecursively(engine.getIncomeAccountList(), accounts);
        addAccountsRecursively(engine.getExpenseAccountList(), accounts);

        return new ArrayList<>(accounts);
    }

    private static void addAccountsRecursively(final List<Account> source, final Set<Account> destination) {
        if (source == null) {
            return;
        }

        for (final Account account : source) {
            if (destination.add(account)) {
                addAccountsRecursively(account.getChildren(), destination);
            }
        }
    }

    private static Account findAccountByPathName(final List<Account> allAccounts, final String pathName) {
        final String normalizedRequested = normalizeAccountPath(pathName);

        if (normalizedRequested.isEmpty()) {
            return null;
        }

        for (final Account account : allAccounts) {
            if (normalizeAccountPath(account.getPathName()).equalsIgnoreCase(normalizedRequested)) {
                return account;
            }
        }

        for (final Account account : allAccounts) {
            final String accountPath = normalizeAccountPath(account.getPathName());
            final int separator = accountPath.indexOf(':');

            if (separator != -1 && separator + 1 < accountPath.length()) {
                final String withoutRoot = accountPath.substring(separator + 1);
                if (withoutRoot.equalsIgnoreCase(normalizedRequested)) {
                    return account;
                }
            }
        }

        for (final Account account : allAccounts) {
            final String accountPath = normalizeAccountPath(account.getPathName());
            if (accountPath.endsWith(":" + normalizedRequested)) {
                return account;
            }
        }

        final int lastSeparator = normalizedRequested.lastIndexOf(':');
        final String leafName = lastSeparator == -1 ? normalizedRequested : normalizedRequested.substring(lastSeparator + 1);

        for (final Account account : allAccounts) {
            if (account.getName().equalsIgnoreCase(leafName)) {
                return account;
            }
        }

        return null;
    }

    private static String normalizePayee(final String payee) {
        return payee == null ? "" : payee.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String normalizeAccountPath(final String path) {
        if (path == null) {
            return "";
        }

        String normalized = path.trim().replaceAll("\\s*:\\s*", ":");
        normalized = normalized.replaceAll("\\s+", " ");

        while (normalized.contains("::")) {
            normalized = normalized.replace("::", ":");
        }

        if (normalized.startsWith(":")) {
            normalized = normalized.substring(1);
        }

        if (normalized.endsWith(":")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }
}
