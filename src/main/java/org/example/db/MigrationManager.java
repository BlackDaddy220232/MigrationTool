package org.example.db;

import lombok.extern.slf4j.Slf4j;
import org.example.exception.MigrationException;
import org.example.file.MigrationFileReader;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class MigrationManager {

    private static final String FETCH_APPLIED_MIGRATIONS_QUERY = "SELECT migration_name FROM applied_migrations";
    private static final String INSERT_NEW_MIGRATION_QUERY = "INSERT INTO applied_migrations (migration_name, applied_at, created_by) VALUES (?, ?, ?)";

    private static final String DELETE_MIGRATION_QUERY = "DELETE FROM applied_migrations " +
            "WHERE id IN (" +
            "  SELECT id FROM applied_migrations " +
            "  ORDER BY id DESC " +
            "  LIMIT ?" + // используем параметр LIMIT для выбора количества удаляемых записей
            ")";
    private MigrationFileReader fileReader;

    private Properties properties;

    public MigrationManager(Properties properties){
        this.properties=properties;
        fileReader = new MigrationFileReader(properties.getProperty("path.migration"), properties.getProperty("path.undo"));
    }
    /**
     * Fetches applied migrations from the database.
     *
     * @param connection Database connection
     * @return List of applied migration names
     */
    public List<String> getAppliedMigrations(Connection connection) {
        log.info("Fetching applied migrations from the database...");
        try (PreparedStatement ps = connection.prepareStatement(FETCH_APPLIED_MIGRATIONS_QUERY);
             ResultSet rs = ps.executeQuery()) {
            return extractAppliedMigrations(rs);
        } catch (SQLException e) {
            log.error("Failed to fetch applied migrations: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch applied migrations: " + e.getMessage(), e);
        }
    }

    /**
     * Fetches pending migrations by comparing applied migrations and available files.
     *
     * @param connection Database connection
     * @return List of pending migration files
     */
    public List<File> getPendingMigrations(Connection connection) {
        log.info("Fetching pending migrations...");
        List<File> allMigrations = fileReader.getMigrationFiles();
        List<String> appliedMigrations = getAppliedMigrations(connection);

        List<File> pendingMigrations = filterPendingMigrations(allMigrations, appliedMigrations);
        log.info("Found {} pending migrations.", pendingMigrations.size());
        return pendingMigrations;
    }

    public void registerMigrations(List<File> migrationFiles, Connection connection) {
        log.info("Registering migrations in the database...");
        try {
            executeMigrationRegistration(migrationFiles, connection);
        } catch (SQLException e) {
            log.error("Failed to register migrations: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to register migrations: " + e.getMessage(), e);
        }
    }
    public List<File> getRollbackMigrations(String version,Connection connection) {
        log.info("Starting rollback to {} version", version);

        // Получаем все примененные миграции
        List<String> appliedMigrations = getAppliedMigrations(connection);
        // Получаем все файлы откатов
        List<File> rollbackFiles = fileReader.getRollbackFiles();

        // Сортируем примененные миграции в обратном порядке (самая последняя миграция будет первой)
        Collections.reverse(appliedMigrations);

        // Список файлов откатов, которые нужно вернуть
        List<File> rollbackFilesToReturn = new ArrayList<>();

        // Перебираем примененные миграции с самой последней
        for (int i = 0; i < appliedMigrations.size(); i++) {
            String appliedMigration = appliedMigrations.get(i);
            String appliedMigrationVersion = extractVersionFromFileName(appliedMigration);

            // Если достигли указанной версии для отката, прекращаем обработку
            System.out.println(appliedMigrationVersion);
            if (appliedMigrationVersion.equals(version)) {
                break; // Остановим обработку, если достигнута версия отката
            }

            // Найдем файл отката для текущей миграции
            File rollbackFile = findRollbackFileForMigration(appliedMigrationVersion, rollbackFiles);

            // Если файл отката не найден, выбрасываем ошибку
            if (rollbackFile == null) {
                throw new MigrationException("Missing rollback file for migration version: " + appliedMigrationVersion);
            }

            // Проверим, что версия в файле отката совпадает с версией миграции
            String rollbackFileName = rollbackFile.getName();
            String rollbackVersion = extractVersionFromFileName(rollbackFileName);

            if (!rollbackVersion.equals(appliedMigrationVersion)) {
                throw new MigrationException("Version mismatch: migration " + appliedMigrationVersion + " does not have corresponding rollback file with version " + rollbackVersion);
            }

            log.info("Rollback file found for migration version {}: {}", appliedMigrationVersion, rollbackFile.getName());

            // Добавляем файл отката в список для возврата
            rollbackFilesToReturn.add(rollbackFile);
        }

        // Возвращаем список файлов для отката
        return rollbackFilesToReturn;
    }

    // Метод для извлечения версии из имени файла
    private String extractVersionFromFileName(String fileName) {
        // Используем регулярное выражение, чтобы оставить только числа и подчеркивания до последней цифры
        String versionPart = fileName.replaceAll("[^0-9_]", ""); // Оставляем только цифры и подчеркивания
        // Убираем все, что идет после последней цифры
        return versionPart.replaceAll("(_\\D.*|[^\\d]+$)", "");
    }

    public void deleteMigrations(Integer numbersToDelete,Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement(DELETE_MIGRATION_QUERY)) {
            statement.setInt(1, numbersToDelete); // Устанавливаем количество миграций для удаления
            int rowsAffected = statement.executeUpdate(); // Выполняем удаление
            log.info("Deleted " + rowsAffected + " migrations from applied_migrations.");
        } catch (SQLException e) {
            log.error("Failed to register migrations: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to register migrations: " + e.getMessage(), e);
        }
    }

    // Метод для нахождения файла отката для конкретной миграции
    private File findRollbackFileForMigration(String migrationVersion, List<File> rollbackFiles) {
        for (File rollbackFile : rollbackFiles) {
            String rollbackFileName = rollbackFile.getName();
            String rollbackVersion = extractVersionFromFileName(rollbackFileName);

            // Проверяем, если версия отката совпадает с версией миграции
            if (rollbackVersion.equals(migrationVersion)) {
                return rollbackFile;
            }
        }
        return null; // Если файл отката не найден
    }



    /**
     * Extracts applied migrations from the result set.
     */
    private List<String> extractAppliedMigrations(ResultSet rs) throws SQLException {
        List<String> appliedMigrations = new ArrayList<>();
        while (rs.next()) {
            appliedMigrations.add(rs.getString("migration_name"));
        }
        log.info("Successfully fetched {} applied migrations.", appliedMigrations.size());
        return appliedMigrations;
    }

    /**
     * Filters out applied migrations to determine pending migrations.
     */
    private List<File> filterPendingMigrations(List<File> allMigrations, List<String> appliedMigrations) {
        return allMigrations.stream()
                .filter(file -> !appliedMigrations.contains(file.getName()))
                .collect(Collectors.toList());
    }
    private void executeMigrationRegistration(List<File> migrationFiles, Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT_NEW_MIGRATION_QUERY)) {
            for (File migrationFile : migrationFiles) {
                addMigrationToBatch(ps, migrationFile);
            }
            ps.executeBatch();
            log.info("Successfully registered {} migrations.", migrationFiles.size());
        } catch (SQLException e) {
            connection.rollback();
            log.warn("Transaction rolled back due to an error.");
            throw new MigrationException("Transaction rolled back due to an error.");
        }
    }
    private void addMigrationToBatch(PreparedStatement ps, File migrationFile) throws SQLException {
        String migrationName = migrationFile.getName();
        ps.setString(1, migrationName);
        ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
        ps.setString(3, properties.getProperty("db.username"));
        ps.addBatch();
        log.info("Added migration {} to the batch.", migrationName);
    }


}
