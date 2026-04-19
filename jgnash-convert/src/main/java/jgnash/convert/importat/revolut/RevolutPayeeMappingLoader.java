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
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

import jgnash.util.FileMagic;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Loader for Revolut payee to account mappings.
 * 
 * Expected format: CSV with two columns - "Payee" and "Account"
 * (or "Destination account" instead of "Account").
 */
public final class RevolutPayeeMappingLoader {

    private RevolutPayeeMappingLoader() {
    }

    /**
     * Load payee to account mappings from a CSV file.
     * 
     * @param path path to the mapping file
    * @return map of payee names to destination account paths
     * @throws IOException if the file cannot be read
     */
    public static Map<String, String> loadMappings(final Path path) throws IOException {
        final Map<String, String> mappings = new HashMap<>();

        final Charset charset = FileMagic.detectCharset(path.toString());

        try (BufferedReader reader = Files.newBufferedReader(path, charset);
             CSVParser csvParser = CSVFormat.DEFAULT
                 .withFirstRecordAsHeader()
                 .withIgnoreSurroundingSpaces()
                 .parse(reader)) {

            final String payeeHeader = findHeader(csvParser.getHeaderMap(), "payee");
            String accountHeader = findHeader(csvParser.getHeaderMap(), "destination account", "account");

            if (accountHeader == null) {
                accountHeader = findHeader(csvParser.getHeaderMap(), "destinationaccount", "destination_account");
            }

            for (final CSVRecord record : csvParser) {
                final String payee = payeeHeader != null ? getField(record, payeeHeader) : getField(record, 0);
                final String account = accountHeader != null ? getField(record, accountHeader) : getField(record, 1);

                if (!payee.isEmpty() && !account.isEmpty()) {
                    mappings.put(normalizePayee(payee), account.trim());
                }
            }
        }

        return mappings;
    }

    private static String getField(final CSVRecord record, final String column) {
        if (record.isMapped(column)) {
            final String value = record.get(column);
            if (value != null) {
                return value.trim();
            }
        }

        return "";
    }

    private static String getField(final CSVRecord record, final int index) {
        if (index >= 0 && index < record.size()) {
            final String value = record.get(index);
            if (value != null) {
                return value.trim();
            }
        }

        return "";
    }

    private static String findHeader(final Map<String, Integer> headerMap, final String... aliases) {
        final List<String> normalizedAliases = new ArrayList<>();
        for (final String alias : aliases) {
            normalizedAliases.add(normalizeHeader(alias));
        }

        for (final String header : headerMap.keySet()) {
            final String normalizedHeader = normalizeHeader(header);
            if (normalizedAliases.contains(normalizedHeader)) {
                return header;
            }
        }

        return null;
    }

    private static String normalizeHeader(final String header) {
        if (header == null) {
            return "";
        }

        // Remove UTF-8 BOM and normalize spacing/casing.
        return header.replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT)
                .replace("_", " ")
                .replaceAll("\\s+", " ");
    }

    private static String normalizePayee(final String payee) {
        return payee == null ? "" : payee.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
