package org.example.utils;

import lombok.NoArgsConstructor;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
/**
 * The {@code PropertiesUtils} class provides utility methods for loading configuration properties
 * from a properties file. This class is designed to simplify loading key-value pairs into a
 * {@link Properties} object for use in application configuration.
 */
@NoArgsConstructor
public class PropertiesUtils {
    /**
     * Loads a properties file from the classpath into a {@link Properties} object.
     *
     * @param fileName the name of the properties file to load, located in the classpath.
     * @return a {@link Properties} object containing the key-value pairs from the file.
     * @throws RuntimeException if the file is not found or an I/O error occurs while reading it.
     */
    public static Properties loadProperties(String fileName) {
        Properties properties = new Properties();
        try(InputStream inputStream = PropertiesUtils.class.getClassLoader().getResourceAsStream(fileName)) {
            if (inputStream == null) {
                throw new FileNotFoundException("Properties file not found: " + fileName);
            }
            properties.load(inputStream);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to load properties: " + exception.getMessage(), exception);
        }
        return properties;
    }
}