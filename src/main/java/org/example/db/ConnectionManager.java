package org.example.db;

import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.Properties;

@Slf4j
public class ConnectionManager {
    private final Properties properties;
    private static final String LOCK_TABLE_QUERY = "SELECT * FROM migration_locks WHERE id = 1 FOR UPDATE";
    private static final String UPDATE_LOCK_QUERY = "UPDATE migration_locks SET is_locked = TRUE, locked_at = ?, locked_by = ? WHERE id = 1";
    private static final String RELEASE_LOCK_QUERY = "UPDATE migration_locks SET is_locked = FALSE, locked_at = NULL, locked_by = NULL WHERE id = 1";
    private static final long LOCK_EXPIRATION_TIME_MS = 10000; // 10 секунд

    public ConnectionManager(Properties properties) {
        this.properties = properties;
    }

    public Connection getConnection() {
        log.info("Attempting to establish database connection...");
        try {
            Connection connection = DriverManager.getConnection(
                    properties.getProperty("db.url"),
                    properties.getProperty("db.username"),
                    properties.getProperty("db.password")
            );
            log.info("Database connection established successfully.");
            return connection;
        } catch (SQLException e) {
            log.error("Failed to establish database connection", e);
            throw new RuntimeException("Failed to establish database connection: " + e.getMessage());
        }
    }

    public boolean acquireLock(Connection connection) {
        log.info("Attempting to acquire lock...");
        try {
            connection.setAutoCommit(false);

            try (PreparedStatement ps = connection.prepareStatement(LOCK_TABLE_QUERY)) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    boolean isLocked = rs.getBoolean("is_locked");
                    Timestamp lockedAt = rs.getTimestamp("locked_at");

                    // Если блокировка уже установлена и прошло более 10 секунд, снимаем её
                    if (isLocked && lockedAt != null && System.currentTimeMillis() - lockedAt.getTime() > LOCK_EXPIRATION_TIME_MS) {
                        log.warn("Lock has expired. Releasing the old lock and acquiring a new one...");
                        releaseLock(connection); // Снимаем старую блокировку
                    }

                    // Если блокировка не установлена, устанавливаем её
                    if (!isLocked) {
                        try (PreparedStatement updatePs = connection.prepareStatement(UPDATE_LOCK_QUERY)) {
                            updatePs.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
                            updatePs.setString(2, properties.getProperty("db.username"));
                            updatePs.executeUpdate();
                            connection.commit();
                            log.info("Lock successfully acquired.");
                            return true;
                        }
                    } else {
                        connection.rollback();
                        log.warn("The table is currently locked by another process.");
                        return false;
                    }
                } else {
                    connection.rollback();
                    log.error("No lock entry found in the lock table. Please check the table.");
                    throw new RuntimeException("Something wrong with the lock table. Check the lock table");
                }
            } catch (SQLException e) {
                connection.rollback();
                log.error("Failed to acquire lock: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to acquire lock: " + e.getMessage(), e);
            }

        } catch (SQLException e) {
            log.error("Database error during lock acquisition: {}", e.getMessage(), e);
            throw new RuntimeException("Database error during lock acquisition: " + e.getMessage(), e);
        }
    }

    public boolean releaseLock(Connection connection) {
        log.info("Attempting to release lock...");
        try {
            connection.setAutoCommit(false);

            // Сначала проверяем, кто держит блокировку
            try (PreparedStatement ps = connection.prepareStatement("SELECT locked_by, locked_at FROM migration_locks WHERE id = 1 FOR UPDATE")) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String lockedBy = rs.getString("locked_by");
                    Timestamp lockedAt = rs.getTimestamp("locked_at");

                    // Проверяем, если прошло больше 10 секунд с момента установки блокировки
                    if (lockedAt != null && System.currentTimeMillis() - lockedAt.getTime() > LOCK_EXPIRATION_TIME_MS) {
                        log.info("Lock has expired or been held too long, releasing...");
                        try (PreparedStatement releasePs = connection.prepareStatement(RELEASE_LOCK_QUERY)) {
                            releasePs.executeUpdate();
                            connection.commit();
                            log.info("Lock successfully released due to expiration.");
                        }
                    } else if (lockedBy != null && lockedBy.equals(properties.getProperty("db.username"))) {
                        // Если это тот же пользователь, который установил блокировку, снимаем её
                        try (PreparedStatement releasePs = connection.prepareStatement(RELEASE_LOCK_QUERY)) {
                            releasePs.executeUpdate();
                            connection.commit(); // Сохраняем изменения
                            log.info("Lock successfully released by the current user.");
                            return true;
                        }
                    } else {
                        log.error("You are not authorized to release this lock as it was locked by: {}", lockedBy);
                        return false;
                    }
                } else {
                    log.error("No lock entry found in the lock table. Please check the table.");
                    throw new RuntimeException("Something wrong with the lock table. Check the table");
                }
            }
        } catch (SQLException e) {
            log.error("Failed to release lock: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to release lock: " + e.getMessage(), e);
        }
        return false;
    }
}
