# Expense Tracker Application

A desktop application built with Java Swing that helps users track shared expenses and settle debts among friends, roommates, or groups.

## Features

- **User Management**: Add and remove users who share expenses
- **Expense Tracking**: Record expenses with custom or equal splits
- **Balance Management**: View who owes whom and how much
- **Debt Settlement**: Settle up debts between users
- **Data Persistence**: All data is stored in a MySQL database

## Requirements

- Java Development Kit (JDK) 8 or higher
- MySQL Server 8.0 or higher
- MySQL Connector/J 9.2.0 (JDBC driver)

## Database Setup

1. Create a MySQL database named `expense_splitter`
2. The application uses the following database configuration:
   ```java
   private static final String DB_URL = "jdbc:mysql://localhost:3306/expense_splitter";
   private static final String DB_USER = "root";
   private static final String DB_PASSWORD = "anubhav@123"; // Change this to your MySQL password

## Compile
```
javac -cp .;../lib/mysql-connector-j-9.2.0.jar ExpenseSplitterGUI.java
```
For Linux/Mac:
```
javac -cp .:../lib/mysql-connector-j-9.2.0.jar ExpenseSplitterGUI.java
```

## Run
```
java -cp .;../lib/mysql-connector-j-9.2.0.jar ExpenseSplitterGUI
```

For Linux/Mac:
```
java -cp .:../lib/mysql-connector-j-9.2.0.jar ExpenseSplitterGUI
```
