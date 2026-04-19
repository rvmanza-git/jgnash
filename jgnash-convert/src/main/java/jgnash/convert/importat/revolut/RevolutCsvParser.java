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

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;

import jgnash.convert.importat.ImportTransaction;
import jgnash.util.FileMagic;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Parser for Revolut CSV exports.
 */
public final class RevolutCsvParser {

        private static final DateTimeFormatter[] DATE_TIME_PATTERNS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        };

        private static final DateTimeFormatter[] DATE_PATTERNS = {
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy")
    };

    private RevolutCsvParser() {
    }

    /**
     * Parse a Revolut CSV file without payee mappings.
     *
     * @param path path to the CSV file
     * @return parsed RevolutBank with transactions
     * @throws IOException if the file cannot be read
     */
    public static RevolutBank parse(final Path path) throws IOException {
        return parse(path, Map.of());
    }

    /**
     * Parse a Revolut CSV file with optional payee to account mappings.
     *
     * @param path path to the CSV file
     * @param payeeMappings map of payee names to destination account names
     * @return parsed RevolutBank with transactions
     * @throws IOException if the file cannot be read
     */
    public static RevolutBank parse(final Path path, final Map<String, String> payeeMappings) throws IOException {
        final RevolutBank bank = new RevolutBank();

        final Charset charset = FileMagic.detectCharset(path.toString());

        try (BufferedReader reader = Files.newBufferedReader(path, charset);
             CSVParser csvParser = CSVFormat.DEFAULT
                 .withFirstRecordAsHeader()
                 .withIgnoreSurroundingSpaces()
                 .parse(reader)) {

            for (final CSVRecord record : csvParser) {
                final String state = get(record, "State");

                // Skip pending/cancelled transactions when state exists.
                if (!state.isEmpty() && !"COMPLETED".equalsIgnoreCase(state)) {
                    continue;
                }

                final String amountField = get(record, "Amount");
                if (amountField.isEmpty()) {
                    continue;
                }

                final BigDecimal amount;

                try {
                    amount = parseAmount(amountField);
                } catch (final NumberFormatException e) {
                    continue;
                }

                final ImportTransaction importTransaction = new ImportTransaction();

                importTransaction.setAmount(amount);
                importTransaction.setDatePosted(parseDate(get(record, "Completed Date"), get(record, "Started Date")));

                final String description = resolvePayee(record);
                final String type = get(record, "Type");
                final String product = get(record, "Product");

                final String payee = !description.isEmpty() ? description : type;
                importTransaction.setPayee(payee);
                importTransaction.setMemo(buildMemo(type, product));


                // Apply payee mapping if available - store the account path in accountTo
                final String mappedAccount = findMappedAccount(payee, payeeMappings);
                if (mappedAccount != null && !mappedAccount.isEmpty()) {
                    importTransaction.setAccountTo(mappedAccount);
                }
                final String id = get(record, "ID");
                if (!id.isEmpty()) {
                    importTransaction.setFITID(id);
                }

                bank.addTransaction(importTransaction);
            }
        }

        return bank;
    }

    private static String get(final CSVRecord record, final String column) {
        if (record.isMapped(column)) {
            final String value = record.get(column);
            if (value != null) {
                return value.trim();
            }
        }

        return "";
    }

    private static String buildMemo(final String type, final String product) {
        if (type.isEmpty()) {
            return product;
        }

        if (product.isEmpty()) {
            return type;
        }

        return type + " - " + product;
    }

    private static String resolvePayee(final CSVRecord record) {
        final String[] candidates = {
                get(record, "Description"),
                get(record, "Counterparty"),
                get(record, "Merchant"),
                get(record, "Beneficiary"),
                get(record, "Partner"),
                get(record, "Reference")
        };

        for (final String candidate : candidates) {
            if (!candidate.isEmpty()) {
                return candidate;
            }
        }

        return "";
    }

    private static String findMappedAccount(final String payee, final Map<String, String> payeeMappings) {
        final String normalizedPayee = normalizePayee(payee);

        // 1) Exact match first.
        String mappedAccount = payeeMappings.get(normalizedPayee);
        if (mappedAccount != null && !mappedAccount.isEmpty()) {
            return mappedAccount;
        }

        // 2) Best fuzzy match for common variants (e.g. "Netflix" vs "Netflix.com").
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
            mappedAccount = payeeMappings.get(bestKey);
            if (mappedAccount != null && !mappedAccount.isEmpty()) {
                return mappedAccount;
            }
        }

        return null;
    }

    private static BigDecimal parseAmount(final String value) {
        String amount = value.trim().replace(",", "");

        if (amount.startsWith("(") && amount.endsWith(")")) {
            amount = "-" + amount.substring(1, amount.length() - 1);
        }

        amount = amount.replaceAll("[^0-9.\\-]", "");

        return new BigDecimal(amount);
    }

    private static String normalizePayee(final String payee) {
        return payee == null ? "" : payee.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static LocalDate parseDate(final String completedDate, final String startedDate) {
        final String value = !completedDate.isEmpty() ? completedDate : startedDate;

        if (value.isEmpty()) {
            return LocalDate.now();
        }

        try {
            return LocalDate.parse(value);
        } catch (final DateTimeParseException ignored) {
            // Keep trying additional formats.
        }

        try {
            return OffsetDateTime.parse(value).toLocalDate();
        } catch (final DateTimeParseException ignored) {
            // Keep trying additional formats.
        }

        for (final DateTimeFormatter formatter : DATE_TIME_PATTERNS) {
            try {
                return LocalDateTime.parse(value, formatter).toLocalDate();
            } catch (final DateTimeParseException ignored) {
                // Try the next formatter.
            }
        }

        for (final DateTimeFormatter formatter : DATE_PATTERNS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (final DateTimeParseException ignored) {
                // Try the next formatter.
            }
        }

        return LocalDate.now();
    }
}
