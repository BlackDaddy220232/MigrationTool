package org.example;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class ConnectionManager {
    private final Properties properties;
    private volatile Connection connection;
    private static volatile ConnectionManager instance;

    /**
     * Приватный конструктор для Singleton.
     */
    private ConnectionManager() {
        this.properties = PropertiesUtils.loadProperties("D:\\Internship\\MigrationTool\\src\\main\\resources\\application.properties");
    }

    /**
     * Возвращает единственный экземпляр ConnectionManager (Singleton).
     *
     * @return Экземпляр ConnectionManager.
     */
    public static ConnectionManager getInstance() {
        ConnectionManager localInstance = instance;
        if (localInstance == null) {
            synchronized (ConnectionManager.class) {
                if (instance == null) {
                    instance = new ConnectionManager();
                }
                localInstance = instance;
            }
        }
        return localInstance;
    }

    /**
     * Устанавливает подключение к базе данных.
     */
    public synchronized void startConnection() {
        if (connection == null || isConnectionClosed()) {
            try {
                connection = DriverManager.getConnection(
                        properties.getProperty("spring.datasource.url"),
                        properties.getProperty("spring.datasource.username"),
                        properties.getProperty("spring.datasource.password")
                );
            } catch (SQLException e) {
                throw new RuntimeException("Failed to establish database connection: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Проверяет, закрыто ли подключение.
     *
     * @return true, если подключение закрыто или null.
     */
    private boolean isConnectionClosed() {
        try {
            return connection == null || connection.isClosed();
        } catch (SQLException e) {
            return true; // Если ошибка при проверке, считаем, что подключение закрыто.
        }
    }

    /**
     * Закрывает подключение к базе данных.
     */
    public synchronized void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                System.err.println("Failed to close database connection: " + e.getMessage());
            } finally {
                connection = null; // Обнуляем объект, чтобы можно было повторно создать соединение.
            }
        }
    }

    /**
     * Загрузка свойств из файла.
     *
     * @param filePath Путь к файлу свойств.
     * @return Объект Properties.
     */
    private Properties loadProperties(String filePath) {
        Properties properties = new Properties();
        try (var inputStream = getClass().getClassLoader().getResourceAsStream(filePath)) {
            if (inputStream != null) {
                properties.load(inputStream);
            } else {
                throw new RuntimeException("Properties file not found at: " + filePath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load properties file: " + e.getMessage(), e);
        }
        return properties;
    }
}
