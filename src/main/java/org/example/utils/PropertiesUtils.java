package org.example.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class PropertiesUtils {
    public static Properties loadProperties(String fileName) {
        Properties properties = new Properties();
        try(InputStream inputStream = PropertiesUtils.class.getClassLoader().getResourceAsStream(fileName)) {
            if (inputStream == null) {
                throw new RuntimeException("Properties file not found: " + fileName);
            }
            properties.load(inputStream);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to load properties: " + exception.getMessage(), exception);
        }
        return properties;
    }
}