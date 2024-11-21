# Migration Tool

A migration tool for managing database schema migrations using SQL scripts. This tool allows you to apply pending migrations to a database, keep track of the ones that have been applied, and ensure that each migration is only run once.

## Features

- Automatically reads and executes SQL migration scripts.
- Tracks applied migrations in a database.
- Handles database connection and locking mechanisms to ensure safe execution.
- Logs migration processes and errors using SLF4J for easy debugging.

## Technologies

- Java 8+
- JDBC for database interactions
- SLF4J for logging
- SQL for database migrations

## Requirements

- Java 8 or higher
- A working database with appropriate access credentials
- SQL migration files to be applied

## Migrations files
Migrations files are supposed to be stored in /resources/migrations

## Installation

#### 1. Clone the repository:

```bash
  git clone https://github.com/BlackDaddy220232/MigrationTool.git
```

#### 2. Navigate into the cloned directory
```bash
cd Weather-App\src\main\resources
```

#### 3. Open ```application.properties```****

#### 4. Please provide your username and password, and url database.

#### 5. Also you supposed to create two tables in your database by using this SQL scripts:
```
CREATE TABLE migration_locks (
    id SERIAL PRIMARY KEY,       -- Уникальный идентификатор записи
    is_locked BOOLEAN NOT NULL,  -- Флаг блокировки: TRUE - занято, FALSE - свободно
    locked_at TIMESTAMP,         -- Время, когда была установлена блокировка
    locked_by VARCHAR(255)       -- Информация о том, кто установил блокировку (например, имя пользователя или ID приложения)
);
```
```
CREATE TABLE applied_migrations (
    id SERIAL PRIMARY KEY,
    migration_name VARCHAR(255) UNIQUE NOT NULL,
    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```
#### 6. Set your path to application.properties and path to your migration files.

#### 7. Execute the Maven command to clean the project and then build it
```bash
mvn clean install
```

#### 8. run the application using the following Java command:
```bash
java -jar \target\Weather-0.0.1-SNAPSHOT.jar
```


