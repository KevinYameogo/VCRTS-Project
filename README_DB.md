# Database Setup Instructions

This project now uses a MySQL database for storage.

## Prerequisites
1.  **MySQL Server**: Ensure you have MySQL installed and running.
2.  **Database**: Create a database named `vcrts` (or whatever you prefer).
    ```sql
    CREATE DATABASE vcrts;
    ```

## Configuration
Create a file named `.env` in the project root directory (same level as `pom.xml`) with your database credentials:

```properties
DB_HOST=localhost
DB_PORT=3306
DB_NAME=vcrts
DB_USER=root
DB_PASSWORD=your_password
```

## Running
The application will automatically connect to the database on startup.
If the connection fails, check the console for error messages.
The tables (`users`, `jobs`, `vehicles`) will be created automatically if they don't exist.
