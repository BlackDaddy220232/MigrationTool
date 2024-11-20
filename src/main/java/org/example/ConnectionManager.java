package org.example;

import java.sql.*;
import java.util.Properties;

public class ConnectionManager {

    private final Properties properties;
    private static final String LOCK_TABLE_QUERY = "SELECT * FROM migration_locks WHERE id = 1 FOR UPDATE";
    private static final String UPDATE_LOCK_QUERY = "UPDATE migration_locks SET is_locked = TRUE, locked_at = ?, locked_by = ? WHERE id = 1";
    private static final String RELEASE_LOCK_QUERY = "UPDATE migration_locks SET is_locked = FALSE, locked_at = NULL, locked_by = NULL WHERE id = 1";

    public ConnectionManager(String propertiesFilePath) {
        this.properties = PropertiesUtils.loadProperties(propertiesFilePath);
    }

    public Connection getConnection() {
        try {
            return DriverManager.getConnection(
                    properties.getProperty("spring.datasource.url"),
                    properties.getProperty("spring.datasource.username"),
                    properties.getProperty("spring.datasource.password")
            );
        } catch (SQLException e) {
            throw new RuntimeException("Failed to establish database connection: " + e.getMessage(), e);
        }
    }
    public boolean acquireLock(Connection connection) {
        try {
            // Начинаем транзакцию
            connection.setAutoCommit(false);

            // Попытка заблокировать строку
            try (PreparedStatement ps = connection.prepareStatement(LOCK_TABLE_QUERY)) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    boolean isLocked = rs.getBoolean("is_locked");
                    if (!isLocked) {
                        // Если строка не заблокирована, выполняем обновление для захвата блокировки
                        try (PreparedStatement updatePs = connection.prepareStatement(UPDATE_LOCK_QUERY)) {
                            updatePs.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
                            updatePs.setString(2, properties.getProperty("spring.datasource.username"));
                            updatePs.executeUpdate();
                            connection.commit();
                            return true; // Блокировка успешна
                        }
                    } else {
                        // Если строка уже заблокирована, возвращаем false
                        connection.rollback();
                        return false;
                    }
                } else {
                    // Если запись не найдена, возвращаем false
                    connection.rollback();
                    return false;
                }
            } catch (SQLException e) {
                connection.rollback(); // откат при ошибке
                throw new RuntimeException("Failed to acquire lock: " + e.getMessage(), e);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Database error during lock acquisition: " + e.getMessage(), e);
        }
    }
    public void releaseLock(Connection connection) {
        try {
            connection.setAutoCommit(false);
            // Сначала проверяем, кто держит блокировку
            try (PreparedStatement ps = connection.prepareStatement("SELECT locked_by FROM migration_locks WHERE id = 1 FOR UPDATE")) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String lockedBy = rs.getString("locked_by");

                    // Проверяем, что текущий пользователь является тем, кто установил блокировку
                    if (lockedBy != null && lockedBy.equals(properties.getProperty("spring.datasource.username"))) {
                        // Если это тот же пользователь, который установил блокировку, снимаем её
                        try (PreparedStatement releasePs = connection.prepareStatement(RELEASE_LOCK_QUERY)) {
                            releasePs.executeUpdate();
                            connection.commit(); // Сохраняем изменения
                        }
                    } else {
                        throw new RuntimeException("You are not allowed to release this lock. It was locked by: " + lockedBy);
                    }
                } else {
                    throw new RuntimeException("No lock found to release.");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to release lock: " + e.getMessage(), e);
        }
    }

}

