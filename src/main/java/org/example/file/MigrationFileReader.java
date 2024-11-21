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

    public MigrationFileReader(String migrationPath) {
        this.migrationPath = migrationPath;
    }

    public List<File> getMigrationFiles() {
        try {
            List<File> migrationFiles = Files.list(Paths.get(migrationPath)) // Читаем все файлы в папке
                    .filter(Files::isRegularFile) // Оставляем только файлы
                    .filter(path -> path.toString().endsWith(".sql")) // Фильтруем только файлы .sql
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
}
