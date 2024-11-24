package org.example.db;

import lombok.extern.slf4j.Slf4j;
import org.example.exception.LockException;

import java.sql.*;
import java.util.Properties;
import java.util.Scanner;

@Slf4j
public class ConnectionManager {

    private final Properties properties;
    private static final String LOCK_TABLE_QUERY = "SELECT * FROM migration_locks WHERE id = 1 FOR UPDATE";
    private static final String FETCH_LOCK_DETAILS_QUERY = "SELECT locked_by, locked_at FROM migration_locks WHERE id = 1 FOR UPDATE";
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
            log.error("Failed to establish database connection");
            return null;
        }
    }

    public boolean acquireLock(Connection connection) {
        log.info("Attempting to acquire lock...");
        try {
            connection.setAutoCommit(false);

            if (isLockExpired(connection)) {
                log.warn("Lock has expired. Releasing the old lock...");
                releaseLock(connection);
            }
            return attemptToAcquireLock(connection);

        } catch (SQLException e) {
            log.error("Database error during lock acquisition: {}", e.getMessage(), e);
            throw new LockException("Database error during lock acquisition: " + e.getMessage(), e);
        }
    }

    public void releaseLock(Connection connection) {
        log.info("Attempting to release lock...");
        try {
            connection.setAutoCommit(false);

            if (canReleaseLock(connection)) {
                executeLockRelease(connection);
                log.info("Lock successfully released.");
            }

        } catch (SQLException e) {
            log.error("Failed to release lock: {}", e.getMessage(), e);
            throw new LockException("Failed to release lock: " + e.getMessage(), e);
        }
    }

    // --- Private helper methods ---
    private boolean isLockExpired(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(LOCK_TABLE_QUERY);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                boolean isLocked = rs.getBoolean("is_locked");
                Timestamp lockedAt = rs.getTimestamp("locked_at");

                return isLocked && lockedAt != null && System.currentTimeMillis() - lockedAt.getTime() > LOCK_EXPIRATION_TIME_MS;
            }
        }
        return false;
    }

    private boolean attemptToAcquireLock(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(LOCK_TABLE_QUERY);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next() && !rs.getBoolean("is_locked")) {
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
        }
    }

    private boolean canReleaseLock(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(FETCH_LOCK_DETAILS_QUERY);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String lockedBy = rs.getString("locked_by");
                Timestamp lockedAt = rs.getTimestamp("locked_at");
                return (lockedAt != null && System.currentTimeMillis() - lockedAt.getTime() > LOCK_EXPIRATION_TIME_MS) ||
                        properties.getProperty("db.username").equals(lockedBy);
            }
        }
        return false;
    }

    private void executeLockRelease(Connection connection) throws SQLException {
        try (PreparedStatement releasePs = connection.prepareStatement(RELEASE_LOCK_QUERY)) {
            releasePs.executeUpdate();
            connection.commit();
        }
    }
}
