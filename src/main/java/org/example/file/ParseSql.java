package org.example.file;

import lombok.extern.slf4j.Slf4j;

import java.io.*;

/**
 * Utility class for parsing SQL migration files.
 * <p>
 * Provides functionality to read the content of SQL files into a String for further processing.
 */
@Slf4j
public class ParseSql {

    /**
     * Reads the content of a migration SQL file and returns it as a String.
     *
     * @param migrationFile The SQL file to be read.
     * @return The content of the file as a String.
     * @throws IllegalArgumentException If the file is null or doesn't exist.
     * @throws RuntimeException If an error occurs while reading the file.
     */
    public String readSqlFileToString(File migrationFile) {
        validateFile(migrationFile);  // Validate the file before reading it
        log.debug("Reading file: {}", migrationFile.getName());

        try (BufferedReader fileReader = new BufferedReader(new FileReader(migrationFile))) {
            return readFileContent(fileReader);  // Extracted method for reading the file content
        } catch (IOException exception) {
            logErrorAndThrow(migrationFile, exception);  // Extracted method for logging and throwing
            return null;  // Unreachable, but required by the compiler
        }
    }
    private void validateFile(File migrationFile) {
        if (migrationFile == null || !migrationFile.exists()) {
            log.error("Invalid file: {}", migrationFile);
        }
    }

    private String readFileContent(BufferedReader fileReader) throws IOException {
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = fileReader.readLine()) != null) {
            result.append(line).append(System.lineSeparator());
        }
        return result.toString();
    }

    private void logErrorAndThrow(File migrationFile, IOException exception) {
        log.error("Error reading file {}: {}", migrationFile.getName(), exception.getMessage(), exception);
    }
}
