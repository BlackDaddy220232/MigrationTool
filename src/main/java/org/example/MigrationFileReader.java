package org.example;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class MigrationFileReader {
    private final String migrationPath;

    // Конструктор для задания пути миграций
    public MigrationFileReader(String migrationPath) {
        this.migrationPath = migrationPath;
    }

    // Метод для получения списка файлов миграций
    public List<File> getMigrationFiles() {
        try {
            return Files.list(Paths.get(migrationPath)) // Читаем все файлы в папке
                    .filter(Files::isRegularFile) // Оставляем только файлы
                    .filter(path -> path.toString().endsWith(".sql")) // Фильтруем только файлы .sql
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile) // Преобразуем Path в File
                    .collect(Collectors.toCollection(LinkedList::new)); // Собираем в LinkedList
        } catch (IOException e) {
            e.printStackTrace();
            return new LinkedList<>(); // Возвращаем пустой список в случае ошибки
        }
    }

}
