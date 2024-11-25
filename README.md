# Migration Tool

**Migration Tool** is a comprehensive Java-based utility designed for database schema management and migration tasks. It supports executing migrations, rolling back to specific versions, and retrieving the current status of migrations. The tool ensures data consistency and integrity during schema updates by employing robust locking mechanisms.

## Key Features

- **Run Migrations**: Execute pending SQL migration files in a specified order.
- **Rollback Support**: Roll back to a specific version to recover from unexpected issues.
- **Migration Status**: View the current migration status and identify pending updates.
- **Locking Mechanism**: Prevents simultaneous migration executions using a reliable locking system.
- **Configurable**: Highly customizable via `application.properties`.

---

## Project Structure

### Key Components

- **`MigrationTool`**  
  The main entry point for interacting with the tool. Provides a menu-driven interface to manage migrations.

- **`ConnectionManager`**  
  Manages database connections and handles locking to ensure safe execution of migrations.

- **`MigrationExecutor`**  
  Core logic for initializing, executing, rolling back, and managing migrations.

- **`MigrationFileReader`**  
  Validates and reads SQL migration files based on predefined naming conventions.

- **`ParseSql`**  
  Handles the parsing of SQL files into strings for execution.

- **`PropertiesUtils`**  
  Utility for loading application configuration from `application.properties`.

---

## Prerequisites

1. **Java**: Ensure you have JDK 17 or higher installed.
2. **Database**: Supported databases include PostgreSQL and MySQL (or any JDBC-compatible database).
3. **Migrations Directory**: Prepare a directory containing your SQL migration files in the format `Vx_x__Description.sql`.

---

## Configuration

Modify the `application.properties` file to configure database and migration settings:

```properties
# Database Configuration
db.url=jdbc:postgresql://localhost:5432/your_database
db.username=your_username
db.password=your_password

# Lock Configuration
lock.expiration.ms=expiration_time_default(10000)
path.migration=src/main/resources/migrations
path.undo=src/main/resources/migrations/undo
```

## SQL File Conventions

Migration files must adhere to the following naming format:

**`Vx_x__Description.sql`**  

Example : **`V1_1__Create_Users_Table.sql.sql`** 

Rollback files (if needed) must follow a similar convention:

**`UNDOx_x__Description.sql`**

Example: **`UNDO1_1__Rollback_Users_Table.sql`**

**The presence of numbers in the file description is prohibited!!!**

## How to Run
1. **Clone the Repository**
```
git clone https://github.com/BlackDaddy220232/MigrationTool.git
cd MigrationTool
```
2. **Build the Project**

Use Maven or your preferred build tool to compile the project:
```
mvn clean install
```
3. **Run the Tool**

Execute the main class:
```
java -jar target/MigrationTool-1.0-SNAPSHOT.jar
```
## Usage

When you run the tool, it presents a simple menu interface:

```
======MENU======
1. Run migrations
2. Rollback to any version
3. Get status of migrations
4. Exit
Choose your option: 
```

### Commands:
- Option 1: Executes all pending migrations in the correct order.
- Option 2: Prompts you to specify a version to roll back to (e.g., 1_1).
- Option 3: Displays the current status of migrations, including executed and pending versions.
- Option 4: Finish app.

## Java Documentation (JavaDoc)

This project uses JavaDoc for automatically generating source code documentation, which helps developers and users understand how to use the various classes, methods, and interfaces.
```
mvn javadoc:javadoc
```
You can find java doc by this path ```\target\reports\apidocs```
### Generating JavaDoc

To generate the JavaDoc for this project, run the following command:
## License
This project is licensed under the MIT License. See the LICENSE file for details.
