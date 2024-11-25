package org.example.db;

import lombok.extern.slf4j.Slf4j;
import org.example.exception.DatabaseException;
import org.example.exception.LockException;

import java.sql.*;
import java.util.Properties;
/**
 * Manages database connections and provides mechanisms for acquiring and releasing locks
 * on specific database tables.
 * <p>
 * The class establishes database connections, handles lock expiration, and retries
 * lock acquisition when needed, ensuring exclusive access during migrations.
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Establishes database connections</li>
 *   <li>Implements locking for exclusive table access</li>
 *   <li>Handles lock expiration and retries</li>
 * </ul>
 */

@Slf4j
public class ConnectionManager {

    private final Properties properties;
    private static final String LOCK_TABLE_QUERY = "SELECT * FROM migration_locks WHERE id = 1 FOR UPDATE";
    private static final String FETCH_LOCK_DETAILS_QUERY = "SELECT locked_by, locked_at FROM migration_locks WHERE id = 1 FOR UPDATE";
    private static final String UPDATE_LOCK_QUERY = "UPDATE migration_locks SET is_locked = TRUE, locked_at = ?, locked_by = ? WHERE id = 1";
    private static final String RELEASE_LOCK_QUERY = "UPDATE migration_locks SET is_locked = FALSE, locked_at = NULL, locked_by = NULL WHERE id = 1";
    private int lockExpirationTime = 10000;
    private static final int MAX_RETRY_ATTEMPTS = 10;

    public ConnectionManager(Properties properties) {
        this.properties = properties;
        lockExpirationTime =Integer.parseInt(properties.getProperty("lock.expiration.ms"));
    }
    /**
     * Establishes a new database connection using the configured properties.
     *
     * @return a {@link Connection} object for interacting with the database.
     * @throws DatabaseException if the connection cannot be established.
     */
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
            throw new DatabaseException("Failed to establish database connection" + e.getMessage());
        }
    }


    /**
     * Acquires a lock on the specified table to ensure exclusive access.
     * Retries lock acquisition multiple times if necessary.
     *
     * @param connection the {@link Connection} to use for lock operations.
     * @throws LockException if the lock cannot be acquired after retries.
     */
    public void acquireLock(Connection connection) {
        log.info("Attempting to acquire lock...");
        try {
            connection.setAutoCommit(false);
            attemptLockAcquisitionWithRetry(connection);
        } catch (SQLException e) {
            logAndThrowLockException("Critical database error during lock acquisition: {}",e);
        }
    }
    /**
     * Releases the lock on the specified table, allowing other processes to acquire it.
     *
     * @param connection the {@link Connection} to use for lock operations.
     * @throws LockException if the lock cannot be released due to a database error.
     */
    public void releaseLock(Connection connection) {
        log.debug("Attempting to release lock...");
        try {
            connection.setAutoCommit(false);
            if (canReleaseLock(connection)) {
                executeLockRelease(connection);
                log.info("Lock successfully released.");
            }

        } catch (SQLException e) {
            logAndThrowLockException("Failed to release lock: {}",e);
        }
    }

    private void attemptLockAcquisitionWithRetry(Connection connection) {
        int attempts=0;
        while (true) {
            try {
                if (handleExpiredLockIfNecessary(connection)) {
                    log.info("Lock successfully acquired.");
                    return;
                } else {
                    retryLockAcquisition();
                }
                attempts++;
                if(attempts==MAX_RETRY_ATTEMPTS){
                    throw new LockException("Cant lock table, more than 10 attempts");
                }
            } catch (SQLException | InterruptedException e) {
                handleLockAcquisitionError(e);
            }
        }
    }
    private boolean handleExpiredLockIfNecessary(Connection connection) throws SQLException {
        if (isLockExpired(connection)) {
            log.warn("Lock has expired. Releasing the old lock...");
            releaseLock(connection);
        }
        return attemptToAcquireLock(connection);
    }
    private void retryLockAcquisition() throws InterruptedException {
        log.debug("Lock acquisition failed. Retrying in 10 seconds...");
        Thread.sleep(10_000); // Ждем 10 секунд перед новой попыткой
    }
    private void handleLockAcquisitionError(Exception e) {
        if (e instanceof SQLException) {
            logAndThrowLockException("Database error during lock acquisition: {}", (SQLException) e);
        } else if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt(); // Восстанавливаем статус прерывания
            throw new RuntimeException("Lock acquisition interrupted.");
        }
    }
    private boolean isLockExpired(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(LOCK_TABLE_QUERY);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                boolean isLocked = rs.getBoolean("is_locked");
                Timestamp lockedAt = rs.getTimestamp("locked_at");
                return isLocked && lockedAt != null && System.currentTimeMillis() - lockedAt.getTime() > lockExpirationTime;
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
                    log.debug("Lock successfully acquired.");
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
                return (lockedAt != null && System.currentTimeMillis() - lockedAt.getTime() > lockExpirationTime) ||
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
    private void logAndThrowLockException(String message, SQLException e) {
        log.error(message, e);
        throw new LockException(message + ": " + e.getMessage());
    }
}
