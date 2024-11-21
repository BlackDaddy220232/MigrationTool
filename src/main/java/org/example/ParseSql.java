package org.example;

import java.io.*;

public class ParseSql {
    public String sqlConverter(File migrationFile) {
        try (BufferedReader fileReader = new BufferedReader(new FileReader(migrationFile))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = fileReader.readLine()) != null) {
                result.append(line).append(System.lineSeparator());
            }
            System.out.println(result.toString());
            return result.toString();
        } catch (FileNotFoundException exception) {
            System.out.println(String.format("File %s doesn't exist", migrationFile.getName()));
            return null;
        } catch (IOException exception) {
            System.out.println(String.format("An error occurred while reading file %s: %s", migrationFile.getName(), exception.getMessage()));
            return null;
        }
    }

}
