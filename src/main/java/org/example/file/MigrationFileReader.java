package org.example.file;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class MigrationFileReader {
    private final String migrationPath;
    private final String rollbackPath;
    private static final String REGEX_MIGRATION_FILE="^V\\d+_\\d+_.*\\.sql$";

    private static final String REGEX_ROLLBACK_FILE="^UNDO\\d+_\\d+_.*\\.sql$";

    public MigrationFileReader(String migrationPath, String rollbackPath) {
        this.migrationPath = migrationPath;
        this.rollbackPath = rollbackPath;
    }

    // Метод для получения файлов миграции
    public List<File> getMigrationFiles() {
        try {
            List<File> migrationFiles = Files.list(Paths.get(migrationPath)) // Читаем все файлы в папке
                    .filter(Files::isRegularFile) // Оставляем только файлы
                    .filter(path -> path.toString().endsWith(".sql") && validateFile(path,REGEX_MIGRATION_FILE)) // Фильтруем только файлы .sql с валидацией
                    .sorted(Comparator.naturalOrder()) // Сортируем по имени
                    .map(Path::toFile) // Преобразуем Path в File
                    .collect(Collectors.toList()); // Собираем в List

            log.info("Successfully retrieved migration files from {}", migrationPath);
            return migrationFiles;

        } catch (IOException e) {
            log.error("Error while reading migration files from {}: {}", migrationPath, e.getMessage(), e);
            return new LinkedList<>(); // Возвращаем пустой список в случае ошибки
        }
    }

    // Метод для получения файлов отката
    public List<File> getRollbackFiles() {
        try {
            List<File> rollbackFiles = Files.list(Paths.get(rollbackPath)) // Читаем все файлы в папке
                    .filter(Files::isRegularFile) // Оставляем только файлы
                    .filter(path -> path.toString().endsWith(".sql") && validateFile(path,REGEX_ROLLBACK_FILE)) // Фильтруем только файлы .sql с валидацией
                    .sorted(Comparator.naturalOrder()) // Сортируем по имени
                    .map(Path::toFile) // Преобразуем Path в File
                    .collect(Collectors.toList()); // Собираем в List

            log.info("Successfully retrieved rollback files from {}", migrationPath);
            return rollbackFiles;

        } catch (IOException e) {
            log.error("Error while reading rollback files from {}: {}", migrationPath, e.getMessage(), e);
            return new LinkedList<>(); // Возвращаем пустой список в случае ошибки
        }
    }

    // Валидация для файлов миграции
    private boolean validateFile(Path path, String regex) {
        String fileName = path.getFileName().toString();
        if (fileName.matches(regex)) {
            log.info("Valid {} file", fileName);
            return true;
        } else {
            log.warn("Invalid {} file:", fileName);
            return false;
        }
    }
}
