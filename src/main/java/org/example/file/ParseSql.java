package org.example.file;

import lombok.extern.slf4j.Slf4j;

import java.io.*;

@Slf4j
public class ParseSql {

    public String readSqlFileToString(File migrationFile) {
        if (migrationFile == null || !migrationFile.exists()) {
            log.error("Provided file is null or doesn't exist: {}", migrationFile);
            throw new IllegalArgumentException("Invalid file: File is null or does not exist.");
        }

        log.info("Attempting to read file: {}", migrationFile.getName());

        try (BufferedReader fileReader = new BufferedReader(new FileReader(migrationFile))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = fileReader.readLine()) != null) {
                result.append(line).append(System.lineSeparator());
            }
            log.info("Successfully read file: {}", migrationFile.getName());
            return result.toString();
        } catch (IOException exception) {
            log.error("Error reading file {}: {}", migrationFile.getName(), exception.getMessage(), exception);
            throw new RuntimeException("Failed to read file: " + migrationFile.getName(), exception);
        }
    }

}
