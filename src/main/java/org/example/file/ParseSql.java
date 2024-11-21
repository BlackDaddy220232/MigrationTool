package org.example.file;

import lombok.extern.slf4j.Slf4j;

import java.io.*;

@Slf4j
public class ParseSql {

    public String sqlConverter(File migrationFile) {
        StringBuilder result = new StringBuilder();

        // Чтение файла
        try (BufferedReader fileReader = new BufferedReader(new FileReader(migrationFile))) {
            String line;
            while ((line = fileReader.readLine()) != null) {
                result.append(line).append(System.lineSeparator());
            }
            log.info("Successfully read file: {}", migrationFile.getName());
            return result.toString();
        } catch (FileNotFoundException exception) {
            log.error("File {} doesn't exist: {}", migrationFile.getName(), exception.getMessage());
            throw new RuntimeException("File doesn't exist: " + migrationFile.getName(), exception); // Бросаем RuntimeException
        } catch (IOException exception) {
            log.error("An error occurred while reading file {}: {}", migrationFile.getName(), exception.getMessage(), exception);
            throw new RuntimeException("An error occurred while reading file: " + migrationFile.getName(), exception); // Бросаем RuntimeException
        }
    }
}
