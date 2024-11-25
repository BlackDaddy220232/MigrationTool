package org.example.db;

import lombok.extern.slf4j.Slf4j;
import org.example.exception.DatabaseException;
import org.example.exception.MigrationException;
import org.example.file.MigrationFileReader;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The MigrationManager class handles database migration operations, including:
 * <ul>
 *     <li>Fetching and registering migrations</li>
 *     <li>Handling migration rollbacks</li>
 *     <li>Managing migration tables in the database</li>
 * </ul>
 * This class ensures that migrations are applied in the correct order and manages the state of migrations in the database.
 */
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

    private static final String IS_TABLE_APPLIED_MIGRATIONS_EXIST = "SELECT to_regclass('applied_migrations');";
    private static final String IS_TABLE_MIGRATION_LOCKS_EXIST="SELECT to_regclass('migration_locks');";
    private static final String CREATE_TABLE_APPLIED_MIGRATIONS="CREATE TABLE applied_migrations (" +
            "id SERIAL PRIMARY KEY, " +
            "migration_name VARCHAR(255) UNIQUE NOT NULL, " +
            "applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "created_by VARCHAR(255)" +
            ");";
    private static final String CREATE_TABLE_MIGRATION_LOCKS="CREATE TABLE migration_locks ("
            + "id SERIAL PRIMARY KEY, "
            + "is_locked BOOLEAN NOT NULL, "
            + "locked_at TIMESTAMP, "
            + "locked_by VARCHAR(255)); "
            + "INSERT INTO migration_locks (id, is_locked, locked_at, locked_by) "
            + "VALUES (1, false, NULL, NULL);";
    private MigrationFileReader fileReader;

    private Properties properties;

    public MigrationManager(Properties properties){
        this.properties=properties;
        fileReader = new MigrationFileReader(properties.getProperty("path.migration"), properties.getProperty("path.undo"));
    }

    /**
     * Fetches the list of applied migrations from the database.
     *
     * @param connection The database connection
     * @return List of applied migration names
     */
    public List<String> getAppliedMigrations(Connection connection) {
        log.debug("Fetching applied migrations from the database...");
        try (PreparedStatement ps = connection.prepareStatement(FETCH_APPLIED_MIGRATIONS_QUERY);
             ResultSet rs = ps.executeQuery()) {
            return extractAppliedMigrations(rs);
        } catch (SQLException e) {
            logAndThrowMigrationException("Failed to fetch applied migrations: {}",e);
        }
        return null;
    }
    /**
     * Deletes the specified number of most recent migrations from the database.
     *
     * @param numbersToDelete Number of migrations to delete
     * @param connection The database connection
     */
    public void deleteMigrations(Integer numbersToDelete,Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement(DELETE_MIGRATION_QUERY)) {
            statement.setInt(1, numbersToDelete); // Устанавливаем количество миграций для удаления
            int rowsAffected = statement.executeUpdate(); // Выполняем удаление
            log.debug("Deleted " + rowsAffected + " migrations from applied_migrations.");
        } catch (SQLException e) {
            logAndThrowMigrationException("Failed to register migrations: {}",e);
        }
    }

    /**
     * Fetches pending migrations by comparing applied migrations with available migration files.
     *
     * @param connection The database connection
     * @return List of pending migration files
     */
    public List<File> getPendingMigrations(Connection connection) {
        log.debug("Fetching pending migrations...");
        List<File> allMigrations = fileReader.getMigrationFiles();
        List<String> appliedMigrations = getAppliedMigrations(connection);
        List<File> pendingMigrations = filterPendingMigrations(allMigrations, appliedMigrations);
        log.info("Found {} pending migrations.", pendingMigrations.size());
        return pendingMigrations;
    }
    /**
     * Registers the provided migration files in the database.
     *
     * @param migrationFiles List of migration files to be registered
     * @param connection The database connection
     */
    public void registerMigrations(List<File> migrationFiles, Connection connection) {
        log.info("Registering migrations in the database...");
        try {
            executeMigrationRegistration(migrationFiles, connection);
        } catch (SQLException e) {
            logAndThrowMigrationException("Failed to register migrations: {}",e);
        }
    }
    /**
     * Fetches the rollback migrations for the specified version.
     *
     * @param version Migration version to rollback
     * @param connection The database connection
     * @return List of rollback migration files
     */
    public List<File> getRollbackMigrations(String version, Connection connection) {
        List<String> appliedMigrations = getAppliedMigrations(connection);
        List<File> rollbackFiles = fileReader.getRollbackFiles();
        if(!isVersionExists(appliedMigrations,version)){
            throw new MigrationException("This version doesn't exist");
        }
        Collections.reverse(appliedMigrations);
        return findRollbackFilesToApply(version, appliedMigrations, rollbackFiles);
    }
    /**
     * Creates the necessary migration-related tables in the database if they do not exist.
     *
     * @param connection The database connection
     * @return true if tables were created, false otherwise
     */
    public boolean createTables(Connection connection) {
        boolean tablesCreated = createAppliedMigrationsTable(connection);
        if (createMigrationLocksTable(connection)) {
            tablesCreated = true;
        }
        return tablesCreated;
    }

    //private helper methods
    private List<File> findRollbackFilesToApply(String version, List<String> appliedMigrations, List<File> rollbackFiles) {
        List<File> rollbackFilesToReturn = new ArrayList<>();
        for (String appliedMigration : appliedMigrations) {
            String appliedMigrationVersion = extractVersionFromFileName(appliedMigration);
            if (appliedMigrationVersion.equals(version)) {
                break;
            }
            File rollbackFile = findRollbackFileForMigration(appliedMigrationVersion, rollbackFiles);
            validateRollbackFile(appliedMigrationVersion, rollbackFile);
            log.debug("Rollback file found for migration version {}: {}", appliedMigrationVersion, rollbackFile.getName());
            rollbackFilesToReturn.add(rollbackFile);
        }
        return rollbackFilesToReturn;
    }
    private boolean isVersionExists(List<String> migrations, String version){
        for (String migrationVersion : migrations){
            if(compareMigrationVersions(migrationVersion,version)){
                return true;
            }
        }
        return false;
    }
    private boolean compareMigrationVersions(String firstMigration, String secondMigration){
        String firstMigrationVersion = extractVersionFromFileName(firstMigration);
        String secondMigrationVersion = extractVersionFromFileName(secondMigration);
        return firstMigrationVersion.equals(secondMigrationVersion);
    }

    private void validateRollbackFile(String appliedMigrationVersion, File rollbackFile) {
        if (rollbackFile == null) {
            throw new MigrationException("Missing rollback file for migration version: " + appliedMigrationVersion);
        }
        String rollbackFileName = rollbackFile.getName();
        String rollbackVersion = extractVersionFromFileName(rollbackFileName);
        if (!rollbackVersion.equals(appliedMigrationVersion)) {
            throw new MigrationException("Version mismatch: migration " + appliedMigrationVersion +
                    " does not have corresponding rollback file with version " + rollbackVersion);
        }
    }

    private File findRollbackFileForMigration(String migrationVersion, List<File> rollbackFiles) {
        return rollbackFiles.stream()
                .filter(file -> migrationVersion.equals(extractVersionFromFileName(file.getName())))
                .findFirst()
                .orElse(null);
    }


    private String extractVersionFromFileName(String fileName) {
        // Используем регулярное выражение, чтобы оставить только числа и подчеркивания до последней цифры
        String versionPart = fileName.replaceAll("[^0-9_]", ""); // Оставляем только цифры и подчеркивания
        // Убираем все, что идет после последней цифры
        return versionPart.replaceAll("(_\\D.*|[^\\d]+$)", "");
    }


    private List<String> extractAppliedMigrations(ResultSet rs) throws SQLException {
        List<String> appliedMigrations = new ArrayList<>();
        while (rs.next()) {
            appliedMigrations.add(rs.getString("migration_name"));
        }
        log.info("Successfully fetched {} applied migrations.", appliedMigrations.size());
        return appliedMigrations;
    }

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
            log.debug("Successfully registered {} migrations.", migrationFiles.size());
        } catch (SQLException e) {
            connection.rollback();
            logAndThrowDatabaseException("Transaction rolled back due to an error.",e);
        }
    }
    private void addMigrationToBatch(PreparedStatement ps, File migrationFile) throws SQLException {
        String migrationName = migrationFile.getName();
        ps.setString(1, migrationName);
        ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
        ps.setString(3, properties.getProperty("db.username"));
        ps.addBatch();
        log.debug("Added migration {} to the batch.", migrationName);
    }
    private boolean isTableExists(String sqlCommand,Connection connection){
        try(PreparedStatement ps = connection.prepareStatement(sqlCommand)){
            ps.execute();
            ResultSet resultSet = ps.getResultSet();
            resultSet.next();
            if((resultSet.getArray(1)==null)){
                return false;
            }
            return true;
        }catch (SQLException e){
            logAndThrowDatabaseException("Can't get access to database",e);
        }
        return false;
    }

    private boolean createAppliedMigrationsTable(Connection connection) {
        if (!isTableExists(IS_TABLE_APPLIED_MIGRATIONS_EXIST, connection)) {
            try (PreparedStatement ps = connection.prepareStatement(CREATE_TABLE_APPLIED_MIGRATIONS)) {
                ps.execute();
                return true; // Indicate that the table was created
            } catch (SQLException e) {
                logAndThrowDatabaseException("Failed to create applied migrations table",e);
            }
        }
        return false; // Table already exists
    }

    private boolean createMigrationLocksTable(Connection connection) {
        if (!isTableExists(IS_TABLE_MIGRATION_LOCKS_EXIST, connection)) {
            try (PreparedStatement ps = connection.prepareStatement(CREATE_TABLE_MIGRATION_LOCKS)) {
                ps.execute();
                return true; // Indicate that the table was created
            } catch (SQLException e) {
                logAndThrowDatabaseException("Failed to create migration locks table",e);
            }
        }
        return false; // Table already exists
    }
    private void logAndThrowDatabaseException(String message, SQLException e) {
        log.error(message, e);
        throw new DatabaseException(message + ": " + e.getMessage());
    }
    private void logAndThrowMigrationException(String message, SQLException e) {
        log.error(message, e);
        throw new MigrationException(message + ": " + e.getMessage());
    }


}
