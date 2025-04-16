import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.Date; // Add explicit import for java.util.Date

public class ExpenseSplitterGUI extends JFrame {
    private Map<String, User> users;
    private List<Expense> expenses;
    private JPanel mainPanel;
    private JTabbedPane tabbedPane;
    private DefaultTableModel balancesTableModel;
    private DefaultTableModel expensesTableModel;
    private JTable balancesTable;
    private JTable expensesTable;
    private JList<String> userList;
    private DefaultListModel<String> userListModel;
    private JLabel statusLabel; // Added status label for database connection
    
    // Database connection properties
    private static final String DB_URL = "jdbc:mysql://localhost:3306/expense_splitter";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "anubhav@123";
    
    private Connection connection;

    public ExpenseSplitterGUI() {
        this.users = new HashMap<>();
        this.expenses = new ArrayList<>();

        // Set up the JFrame
        setTitle("Expense Splitter");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);
        
        // Add window listener for database cleanup
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeDatabase();
                System.exit(0);
            }
        });

        // Create main panel with tabs
        mainPanel = new JPanel(new BorderLayout());
        tabbedPane = new JTabbedPane();

        // Create tabs
        JPanel usersPanel = createUsersPanel();
        JPanel expensesPanel = createExpensesPanel();
        JPanel balancesPanel = createBalancesPanel();
        JPanel settlePanel = createSettlePanel();

        tabbedPane.addTab("Users", usersPanel);
        tabbedPane.addTab("Expenses", expensesPanel);
        tabbedPane.addTab("Balances", balancesPanel);
        tabbedPane.addTab("Settle Up", settlePanel);

        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        
        // Add status label at the bottom
        statusLabel = new JLabel("Database: Connecting...");
        statusLabel.setBorder(new EmptyBorder(3, 10, 3, 10));
        mainPanel.add(statusLabel, BorderLayout.SOUTH);
        
        add(mainPanel);
        
        // Initialize database connection
        initializeDatabase();
        
        // Refresh UI with loaded data
        SwingUtilities.invokeLater(() -> {
            refreshUserList();
            refreshExpensesTable();
            refreshBalancesTable();
        });
    }

    private void initializeDatabase() {
        try {
            // Load MySQL JDBC driver
            Class.forName("com.mysql.cj.jdbc.Driver");
            
            // Establish connection
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            statusLabel.setText("Database: Connected to " + DB_URL);
            statusLabel.setForeground(new Color(0, 128, 0)); // Green
            System.out.println("Database connection established successfully");
            
            // Create tables if they don't exist
            createDatabaseTables();
            
            // Load existing data
            loadUsersFromDatabase();
            loadExpensesFromDatabase();
        } catch (ClassNotFoundException e) {
            statusLabel.setText("Database: Error - JDBC Driver not found");
            statusLabel.setForeground(Color.RED);
            JOptionPane.showMessageDialog(this, 
                "MySQL JDBC Driver not found. Please add the MySQL connector JAR to your project.",
                "Database Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            addRetryButton();
        } catch (SQLException e) {
            statusLabel.setText("Database: Error - " + e.getMessage());
            statusLabel.setForeground(Color.RED);
            JOptionPane.showMessageDialog(this, 
                "Failed to connect to database. Error: " + e.getMessage(),
                "Database Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            addRetryButton();
        }
    }
    
    private void addRetryButton() {
        JButton retryButton = new JButton("Retry Database Connection");
        retryButton.addActionListener(e -> {
            initializeDatabase();
            mainPanel.remove(retryButton);
            mainPanel.revalidate();
            mainPanel.repaint();
        });
        
        mainPanel.add(retryButton, BorderLayout.NORTH);
        mainPanel.revalidate();
        mainPanel.repaint();
    }
    
    private void closeDatabase() {
        if (connection != null) {
            try {
                connection.close();
                statusLabel.setText("Database: Disconnected");
                statusLabel.setForeground(Color.BLACK);
                System.out.println("Database connection closed");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private JPanel createUsersPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // User list
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        JScrollPane userScrollPane = new JScrollPane(userList);

        // Add user panel
        JPanel addUserPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField userNameField = new JTextField(20);
        JButton addUserButton = new JButton("Add User");

        addUserButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String username = userNameField.getText().trim();
                if (!username.isEmpty()) {
                    addUser(username);
                    userNameField.setText("");
                    refreshUserList();
                }
            }
        });

        addUserPanel.add(new JLabel("Username:"));
        addUserPanel.add(userNameField);
        addUserPanel.add(addUserButton);

        // Delete user
        JButton deleteUserButton = new JButton("Delete Selected User");
        deleteUserButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selectedUser = userList.getSelectedValue();
                if (selectedUser != null) {
                    removeUser(selectedUser);
                    refreshUserList();
                }
            }
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(deleteUserButton);

        // Add components to panel
        panel.add(new JLabel("Users"), BorderLayout.NORTH);
        panel.add(userScrollPane, BorderLayout.CENTER);
        panel.add(addUserPanel, BorderLayout.SOUTH);
        panel.add(buttonPanel, BorderLayout.EAST);

        return panel;
    }

    private JPanel createExpensesPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Expenses table
        String[] expenseColumns = {"Date", "Paid By", "Description", "Amount", "Split Between"};
        expensesTableModel = new DefaultTableModel(expenseColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Make the table cells non-editable
            }
        };
        expensesTable = new JTable(expensesTableModel);
        JScrollPane expensesScrollPane = new JScrollPane(expensesTable);
        
        // Add expense deletion button
        JButton deleteExpenseButton = new JButton("Delete Selected Expense");
        deleteExpenseButton.addActionListener(e -> {
            int selectedRow = expensesTable.getSelectedRow();
            if (selectedRow >= 0) {
                try {
                    deleteExpense(selectedRow);
                    refreshExpensesTable();
                    refreshBalancesTable();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(panel, 
                        "Error deleting expense: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                }
            } else {
                JOptionPane.showMessageDialog(panel, 
                    "Please select an expense to delete.",
                    "No Selection", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(deleteExpenseButton);
        
        // Add expense panel
        JPanel addExpensePanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Row 1
        JLabel paidByLabel = new JLabel("Paid By:");
        JComboBox<String> paidByComboBox = new JComboBox<>();

        gbc.gridx = 0;
        gbc.gridy = 0;
        addExpensePanel.add(paidByLabel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        addExpensePanel.add(paidByComboBox, gbc);

        // Row 2
        JLabel amountLabel = new JLabel("Amount:");
        JTextField amountField = new JTextField(10);

        gbc.gridx = 0;
        gbc.gridy = 1;
        addExpensePanel.add(amountLabel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 1;
        addExpensePanel.add(amountField, gbc);

        // Row 3
        JLabel descriptionLabel = new JLabel("Description:");
        JTextField descriptionField = new JTextField(20);

        gbc.gridx = 0;
        gbc.gridy = 2;
        addExpensePanel.add(descriptionLabel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        addExpensePanel.add(descriptionField, gbc);
        gbc.gridwidth = 1;

        // Row 4
        JLabel splitBetweenLabel = new JLabel("Split Between:");
        JPanel checkBoxPanel = new JPanel();
        checkBoxPanel.setLayout(new BoxLayout(checkBoxPanel, BoxLayout.Y_AXIS));
        JScrollPane checkBoxScrollPane = new JScrollPane(checkBoxPanel);
        checkBoxScrollPane.setPreferredSize(new Dimension(300, 150));

        gbc.gridx = 0;
        gbc.gridy = 3;
        addExpensePanel.add(splitBetweenLabel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        addExpensePanel.add(checkBoxScrollPane, gbc);
        gbc.gridwidth = 1;

        // Split type selection
        JPanel splitTypePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JRadioButton equalSplitRadio = new JRadioButton("Equal Split", true);
        JRadioButton customSplitRadio = new JRadioButton("Custom Split");
        ButtonGroup splitTypeGroup = new ButtonGroup();
        splitTypeGroup.add(equalSplitRadio);
        splitTypeGroup.add(customSplitRadio);
        splitTypePanel.add(equalSplitRadio);
        splitTypePanel.add(customSplitRadio);

        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        addExpensePanel.add(splitTypePanel, gbc);
        gbc.gridwidth = 1;

        // Row 5
        JButton addExpenseButton = new JButton("Add Expense");

        gbc.gridx = 1;
        gbc.gridy = 5;
        addExpensePanel.add(addExpenseButton, gbc);

        // Action listener for the add expense button
        addExpenseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    String paidBy = (String) paidByComboBox.getSelectedItem();
                    double totalAmount = Double.parseDouble(amountField.getText());
                    String description = descriptionField.getText();
                    boolean isEqualSplit = equalSplitRadio.isSelected();

                    // Get selected users from checkboxes and their amounts
                    List<String> splitBetween = new ArrayList<>();
                    Map<String, Double> customAmounts = new HashMap<>();
                    Component[] components = checkBoxPanel.getComponents();
                    double sumOfCustomAmounts = 0;

                    for (Component component : components) {
                        if (component instanceof JPanel) {
                            JPanel userPanel = (JPanel) component;
                            Component[] userComponents = userPanel.getComponents();
                            JCheckBox checkBox = null;
                            JTextField userAmountField = null;
                            
                            for (Component userComponent : userComponents) {
                                if (userComponent instanceof JCheckBox) {
                                    checkBox = (JCheckBox) userComponent;
                                } else if (userComponent instanceof JTextField) {
                                    userAmountField = (JTextField) userComponent;
                                }
                            }

                            if (checkBox != null && checkBox.isSelected()) {
                                String userName = checkBox.getText();
                                splitBetween.add(userName);
                                
                                if (!isEqualSplit && userAmountField != null && !userAmountField.getText().isEmpty()) {
                                    double userAmount = Double.parseDouble(userAmountField.getText());
                                    customAmounts.put(userName, userAmount);
                                    sumOfCustomAmounts += userAmount;
                                }
                            }
                        }
                    }

                    if (paidBy != null && !description.isEmpty() && !splitBetween.isEmpty()) {
                        // Validation for custom split
                        if (!isEqualSplit) {
                            if (Math.abs(sumOfCustomAmounts - totalAmount) > 0.01) {
                                JOptionPane.showMessageDialog(panel, 
                                    "Sum of individual amounts ($" + String.format("%.2f", sumOfCustomAmounts) + 
                                    ") must equal the total expense amount ($" + String.format("%.2f", totalAmount) + ")",
                                    "Validation Error", JOptionPane.ERROR_MESSAGE);
                                return;
                            }
                        }

                        // Add expense with either equal or custom split
                        if (isEqualSplit) {
                            addExpense(paidBy, totalAmount, description, splitBetween);
                        } else {
                            addCustomExpense(paidBy, totalAmount, description, splitBetween, customAmounts);
                        }
                        
                        amountField.setText("");
                        descriptionField.setText("");
                        refreshExpensesTable();
                        refreshBalancesTable();

                        // Reset checkboxes
                        for (Component component : components) {
                            if (component instanceof JPanel) {
                                JPanel userPanel = (JPanel) component;
                                for (Component userComponent : userPanel.getComponents()) {
                                    if (userComponent instanceof JCheckBox) {
                                        ((JCheckBox) userComponent).setSelected(false);
                                    } else if (userComponent instanceof JTextField) {
                                        ((JTextField) userComponent).setText("");
                                    }
                                }
                            }
                        }
                    } else {
                        JOptionPane.showMessageDialog(panel, "Please fill in all fields and select at least one user to split with.",
                                "Input Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(panel, "Please enter valid amounts.",
                            "Input Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        // Toggle amount fields visibility based on split type selection
        ActionListener splitTypeListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                boolean isCustomSplit = customSplitRadio.isSelected();
                Component[] components = checkBoxPanel.getComponents();
                
                for (Component component : components) {
                    if (component instanceof JPanel) {
                        JPanel userPanel = (JPanel) component;
                        for (Component userComponent : userPanel.getComponents()) {
                            if (userComponent instanceof JTextField) {
                                userComponent.setVisible(isCustomSplit);
                            }
                        }
                    }
                }
                checkBoxPanel.revalidate();
                checkBoxPanel.repaint();
            }
        };
        
        equalSplitRadio.addActionListener(splitTypeListener);
        customSplitRadio.addActionListener(splitTypeListener);

        // Update user-related components when tab is selected
        tabbedPane.addChangeListener(e -> {
            if (tabbedPane.getSelectedComponent() == panel) {
                paidByComboBox.removeAllItems();
                checkBoxPanel.removeAll();
                boolean isCustomSplit = customSplitRadio.isSelected();

                for (String user : users.keySet()) {
                    paidByComboBox.addItem(user);
                    
                    JPanel userPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
                    JCheckBox checkBox = new JCheckBox(user);
                    JTextField userAmountField = new JTextField(8);
                    userAmountField.setVisible(isCustomSplit);
                    
                    userPanel.add(checkBox);
                    userPanel.add(userAmountField);
                    checkBoxPanel.add(userPanel);
                }

                checkBoxPanel.revalidate();
                checkBoxPanel.repaint();
            }
        });
        
        // Add components to panel
        panel.add(new JLabel("Expenses"), BorderLayout.NORTH);
        panel.add(expensesScrollPane, BorderLayout.CENTER);
        panel.add(addExpensePanel, BorderLayout.SOUTH);
        panel.add(buttonPanel, BorderLayout.EAST);

        return panel;
    }

    // Add this new method to handle expense deletion
    private void deleteExpense(int tableRow) throws SQLException {
        // We need to find which expense corresponds to the selected row
        // First get the date and description from the selected row to help identify it
        String dateStr = (String) expensesTableModel.getValueAt(tableRow, 0);
        String paidBy = (String) expensesTableModel.getValueAt(tableRow, 1);
        String description = (String) expensesTableModel.getValueAt(tableRow, 2);
        
        // Confirm deletion with user
        int confirm = JOptionPane.showConfirmDialog(
            this,
            "Are you sure you want to delete the expense:\n" +
            "Date: " + dateStr + "\n" +
            "Paid By: " + paidBy + "\n" +
            "Description: " + description,
            "Confirm Deletion",
            JOptionPane.YES_NO_OPTION
        );
        
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        
        try {
            connection.setAutoCommit(false);
            
            // Query the database to find the exact expense ID
            PreparedStatement findStmt = connection.prepareStatement(
                "SELECT e.id, e.amount FROM expenses e " +
                "JOIN users u ON e.paid_by_id = u.id " +
                "WHERE u.username = ? AND e.description = ? AND DATE_FORMAT(e.date, '%Y-%m-%d %H:%i:%s') = ?"
            );
            
            findStmt.setString(1, paidBy);
            findStmt.setString(2, description);
            findStmt.setString(3, dateStr);
            
            ResultSet rs = findStmt.executeQuery();
            
            if (!rs.next()) {
                throw new SQLException("Could not find the expense in the database");
            }
            
            int expenseId = rs.getInt("id");
            double amount = rs.getDouble("amount");
            findStmt.close();
            
            // Get the user ID of the person who paid
            User payerUser = null;
            for (User user : users.values()) {
                if (user.getName().equals(paidBy)) {
                    payerUser = user;
                    break;
                }
            }
            
            if (payerUser == null) {
                throw new SQLException("Could not find the payer user");
            }
            
            // Get participants and their share amounts before deleting
            PreparedStatement participantsStmt = connection.prepareStatement(
                "SELECT u.username, u.id AS user_id, ep.share_amount " +
                "FROM expense_participants ep " +
                "JOIN users u ON ep.user_id = u.id " +
                "WHERE ep.expense_id = ?"
            );
            participantsStmt.setInt(1, expenseId);
            ResultSet participantsRs = participantsStmt.executeQuery();
            
            // Store participant info to update balances
            Map<String, Double> participantShares = new HashMap<>();
            while (participantsRs.next()) {
                String username = participantsRs.getString("username");
                double shareAmount = participantsRs.getDouble("share_amount");
                participantShares.put(username, shareAmount);
            }
            participantsStmt.close();
            
            // Update balances - reverse the expense effect
            for (Map.Entry<String, Double> entry : participantShares.entrySet()) {
                String username = entry.getKey();
                double shareAmount = entry.getValue();
                
                if (!username.equals(paidBy)) {
                    User debtor = users.get(username);
                    
                    // We need to reverse the effect, so we check for a balance from payer to debtor
                    PreparedStatement checkBalanceStmt = connection.prepareStatement(
                        "SELECT amount FROM balances WHERE creditor_id = ? AND debtor_id = ?"
                    );
                    checkBalanceStmt.setInt(1, payerUser.getId());
                    checkBalanceStmt.setInt(2, debtor.getId());
                    ResultSet balanceRs = checkBalanceStmt.executeQuery();
                    
                    if (balanceRs.next()) {
                        // There's a balance from payer to debtor
                        double balanceAmount = balanceRs.getDouble("amount");
                        
                        if (balanceAmount <= shareAmount) {
                            // Delete the balance if it's completely covered
                            PreparedStatement deleteBalanceStmt = connection.prepareStatement(
                                "DELETE FROM balances WHERE creditor_id = ? AND debtor_id = ?"
                            );
                            deleteBalanceStmt.setInt(1, payerUser.getId());
                            deleteBalanceStmt.setInt(2, debtor.getId());
                            deleteBalanceStmt.executeUpdate();
                            deleteBalanceStmt.close();
                            
                            // Update in-memory model
                            payerUser.getBalances().remove(username);
                            
                            // If there's remaining amount, add a reverse balance
                            if (balanceAmount < shareAmount) {
                                double remainingAmount = shareAmount - balanceAmount;
                                
                                PreparedStatement insertReverseStmt = connection.prepareStatement(
                                    "INSERT INTO balances (creditor_id, debtor_id, amount) VALUES (?, ?, ?)"
                                );
                                insertReverseStmt.setInt(1, debtor.getId());
                                insertReverseStmt.setInt(2, payerUser.getId());
                                insertReverseStmt.setDouble(3, remainingAmount);
                                insertReverseStmt.executeUpdate();
                                insertReverseStmt.close();
                                
                                // Update in-memory model
                                debtor.getBalances().put(paidBy, remainingAmount);
                            }
                        } else {
                            // Reduce the balance
                            PreparedStatement updateBalanceStmt = connection.prepareStatement(
                                "UPDATE balances SET amount = ? WHERE creditor_id = ? AND debtor_id = ?"
                            );
                            updateBalanceStmt.setDouble(1, balanceAmount - shareAmount);
                            updateBalanceStmt.setInt(2, payerUser.getId());
                            updateBalanceStmt.setInt(3, debtor.getId());
                            updateBalanceStmt.executeUpdate();
                            updateBalanceStmt.close();
                            
                            // Update in-memory model
                            payerUser.getBalances().put(username, balanceAmount - shareAmount);
                        }
                    } else {
                        // Check if there's a reverse balance (debtor to payer)
                        checkBalanceStmt = connection.prepareStatement(
                            "SELECT amount FROM balances WHERE creditor_id = ? AND debtor_id = ?"
                        );
                        checkBalanceStmt.setInt(1, debtor.getId());
                        checkBalanceStmt.setInt(2, payerUser.getId());
                        balanceRs = checkBalanceStmt.executeQuery();
                        
                        if (balanceRs.next()) {
                            // There's already a reverse balance, increase it
                            double existingAmount = balanceRs.getDouble("amount");
                            
                            PreparedStatement updateReverseStmt = connection.prepareStatement(
                                "UPDATE balances SET amount = ? WHERE creditor_id = ? AND debtor_id = ?"
                            );
                            updateReverseStmt.setDouble(1, existingAmount + shareAmount);
                            updateReverseStmt.setInt(2, debtor.getId());
                            updateReverseStmt.setInt(3, payerUser.getId());
                            updateReverseStmt.executeUpdate();
                            updateReverseStmt.close();
                            
                            // Update in-memory model
                            debtor.getBalances().put(paidBy, existingAmount + shareAmount);
                        } else {
                            // Add a new balance from debtor to payer
                            PreparedStatement insertReverseStmt = connection.prepareStatement(
                                "INSERT INTO balances (creditor_id, debtor_id, amount) VALUES (?, ?, ?)"
                            );
                            insertReverseStmt.setInt(1, debtor.getId());
                            insertReverseStmt.setInt(2, payerUser.getId());
                            insertReverseStmt.setDouble(3, shareAmount);
                            insertReverseStmt.executeUpdate();
                            insertReverseStmt.close();
                            
                            // Update in-memory model
                            debtor.getBalances().put(paidBy, shareAmount);
                        }
                    }
                    
                    checkBalanceStmt.close();
                }
            }
            
            // Now delete the expense and its participants (participants will be deleted automatically via ON DELETE CASCADE)
            PreparedStatement deleteExpenseStmt = connection.prepareStatement(
                "DELETE FROM expenses WHERE id = ?"
            );
            deleteExpenseStmt.setInt(1, expenseId);
            deleteExpenseStmt.executeUpdate();
            deleteExpenseStmt.close();
            
            // Also remove from our in-memory expenses list
            Iterator<Expense> iterator = expenses.iterator();
            while (iterator.hasNext()) {
                Expense expense = iterator.next();
                if (expense.getId() == expenseId) {
                    iterator.remove();
                    break;
                }
            }
            
            connection.commit();
            JOptionPane.showMessageDialog(this, "Expense deleted successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                rollbackEx.printStackTrace();
            }
            throw e;
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException autoCommitEx) {
                autoCommitEx.printStackTrace();
            }
        }
    }

    private JPanel createBalancesPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Balances table
        String[] balanceColumns = {"User", "Owes To", "Amount"};
        balancesTableModel = new DefaultTableModel(balanceColumns, 0);
        balancesTable = new JTable(balancesTableModel);
        JScrollPane balancesScrollPane = new JScrollPane(balancesTable);

        JButton refreshButton = new JButton("Refresh Balances");
        refreshButton.addActionListener(e -> refreshBalancesTable());

        panel.add(new JLabel("Current Balances"), BorderLayout.NORTH);
        panel.add(balancesScrollPane, BorderLayout.CENTER);
        panel.add(refreshButton, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createSettlePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel settleInputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // From user
        JLabel fromLabel = new JLabel("From:");
        JComboBox<String> fromComboBox = new JComboBox<>();

        gbc.gridx = 0;
        gbc.gridy = 0;
        settleInputPanel.add(fromLabel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        settleInputPanel.add(fromComboBox, gbc);

        // To user
        JLabel toLabel = new JLabel("To:");
        JComboBox<String> toComboBox = new JComboBox<>();

        gbc.gridx = 0;
        gbc.gridy = 1;
        settleInputPanel.add(toLabel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 1;
        settleInputPanel.add(toComboBox, gbc);

        // Amount
        JLabel amountLabel = new JLabel("Amount:");
        JTextField amountField = new JTextField(10);

        gbc.gridx = 0;
        gbc.gridy = 2;
        settleInputPanel.add(amountLabel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 2;
        settleInputPanel.add(amountField, gbc);

        // Settle button
        JButton settleButton = new JButton("Settle Debt");

        gbc.gridx = 1;
        gbc.gridy = 3;
        settleInputPanel.add(settleButton, gbc);

        // Action listener for settle button
        settleButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    String from = (String) fromComboBox.getSelectedItem();
                    String to = (String) toComboBox.getSelectedItem();
                    double amount = Double.parseDouble(amountField.getText());

                    if (from != null && to != null && !from.equals(to)) {
                        settleDebt(from, to, amount);
                        amountField.setText("");
                        refreshBalancesTable();
                    } else {
                        JOptionPane.showMessageDialog(panel, "Please select different users for From and To.",
                                "Input Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(panel, "Please enter a valid amount.",
                            "Input Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        // Update user-related components when tab is selected
        tabbedPane.addChangeListener(e -> {
            if (tabbedPane.getSelectedComponent() == panel) {
                fromComboBox.removeAllItems();
                toComboBox.removeAllItems();

                for (String user : users.keySet()) {
                    fromComboBox.addItem(user);
                    toComboBox.addItem(user);
                }
            }
        });

        panel.add(new JLabel("Settle Debts"), BorderLayout.NORTH);
        panel.add(settleInputPanel, BorderLayout.CENTER);

        return panel;
    }

    // Core functionality methods with database integration
    public void addUser(String name) {
        if (!users.containsKey(name)) {
            try {
                // Insert user into database
                PreparedStatement stmt = connection.prepareStatement(
                    "INSERT INTO users (username) VALUES (?)", 
                    Statement.RETURN_GENERATED_KEYS);
                stmt.setString(1, name);
                int rowsAffected = stmt.executeUpdate();
                
                if (rowsAffected > 0) {
                    // Get the auto-generated ID
                    ResultSet generatedKeys = stmt.getGeneratedKeys();
                    if (generatedKeys.next()) {
                        int userId = generatedKeys.getInt(1);
                        User user = new User(name, userId);
                        users.put(name, user);
                        System.out.println("Added user: " + name + " with ID " + userId);
                    }
                }
                stmt.close();
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(this, 
                    "Error adding user: " + e.getMessage(), 
                    "Database Error", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        } else {
            JOptionPane.showMessageDialog(this, "User already exists!", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void removeUser(String name) {
        if (users.containsKey(name)) {
            User user = users.get(name);
            
            // Check if user is involved in any expenses
            try {
                PreparedStatement checkExpensesStmt = connection.prepareStatement(
                    "SELECT COUNT(*) AS count FROM expenses WHERE paid_by_id = ?");
                checkExpensesStmt.setInt(1, user.getId());
                ResultSet rs = checkExpensesStmt.executeQuery();
                rs.next();
                int count = rs.getInt("count");
                
                PreparedStatement checkParticipantsStmt = connection.prepareStatement(
                    "SELECT COUNT(*) AS count FROM expense_participants WHERE user_id = ?");
                checkParticipantsStmt.setInt(1, user.getId());
                rs = checkParticipantsStmt.executeQuery();
                rs.next();
                count += rs.getInt("count");
                
                if (count > 0) {
                    JOptionPane.showMessageDialog(this, 
                        "Cannot remove user involved in expenses.",
                        "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                // Check if user has any balances
                PreparedStatement checkBalancesStmt = connection.prepareStatement(
                    "SELECT COUNT(*) AS count FROM balances WHERE creditor_id = ? OR debtor_id = ?");
                checkBalancesStmt.setInt(1, user.getId());
                checkBalancesStmt.setInt(2, user.getId());
                rs = checkBalancesStmt.executeQuery();
                rs.next();
                count = rs.getInt("count");
                
                if (count > 0) {
                    JOptionPane.showMessageDialog(this, 
                        "Cannot remove user with outstanding balances.",
                        "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                // Remove user from database
                PreparedStatement deleteUserStmt = connection.prepareStatement(
                    "DELETE FROM users WHERE id = ?");
                deleteUserStmt.setInt(1, user.getId());
                deleteUserStmt.executeUpdate();
                
                // Remove from memory
                users.remove(name);
                System.out.println("Removed user: " + name);
                
                checkExpensesStmt.close();
                checkParticipantsStmt.close();
                checkBalancesStmt.close();
                deleteUserStmt.close();
                
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(this, 
                    "Error removing user: " + e.getMessage(), 
                    "Database Error", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        } else {
            JOptionPane.showMessageDialog(this, "User does not exist!", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void addExpense(String paidBy, double amount, String description, List<String> splitBetween) {
        if (!users.containsKey(paidBy)) {
            JOptionPane.showMessageDialog(this, "User " + paidBy + " does not exist!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        List<User> participants = new ArrayList<>();
        for (String username : splitBetween) {
            if (!users.containsKey(username)) {
                JOptionPane.showMessageDialog(this, "User " + username + " does not exist!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            participants.add(users.get(username));
        }

        try {
            connection.setAutoCommit(false);
            
            // Insert expense record
            User payer = users.get(paidBy);
            PreparedStatement expenseStmt = connection.prepareStatement(
                "INSERT INTO expenses (paid_by_id, amount, description) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
            expenseStmt.setInt(1, payer.getId());
            expenseStmt.setDouble(2, amount);
            expenseStmt.setString(3, description);
            expenseStmt.executeUpdate();
            
            // Get generated expense ID
            ResultSet generatedKeys = expenseStmt.getGeneratedKeys();
            int expenseId;
            if (generatedKeys.next()) {
                expenseId = generatedKeys.getInt(1);
            } else {
                throw new SQLException("Failed to get expense ID");
            }
            
            // Calculate split amount
            double splitAmount = amount / splitBetween.size();
            
            // Insert participant records and update balances
            PreparedStatement participantStmt = connection.prepareStatement(
                "INSERT INTO expense_participants (expense_id, user_id, share_amount) VALUES (?, ?, ?)");
            
            for (String username : splitBetween) {
                User participant = users.get(username);
                
                // Add participant to expense
                participantStmt.setInt(1, expenseId);
                participantStmt.setInt(2, participant.getId());
                participantStmt.setDouble(3, splitAmount);
                participantStmt.executeUpdate();
                
                // Skip balance update for the payer themselves
                if (!username.equals(paidBy)) {
                    updateBalanceInDatabase(payer, users.get(username), splitAmount);
                }
            }
            
            // Create expense object and add to list
            Expense expense = new Expense(payer, amount, description, participants);
            expense.setId(expenseId);
            expenses.add(expense);
            
            connection.commit();
            expenseStmt.close();
            participantStmt.close();
            
            System.out.println("Added expense: " + description + " - $" + amount + " paid by " + paidBy);
            
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                rollbackEx.printStackTrace();
            }
            JOptionPane.showMessageDialog(this, 
                "Error adding expense: " + e.getMessage(), 
                "Database Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException autoCommitEx) {
                autoCommitEx.printStackTrace();
            }
        }
    }

    public void addCustomExpense(String paidBy, double amount, String description, List<String> splitBetween, Map<String, Double> customAmounts) {
        if (!users.containsKey(paidBy)) {
            JOptionPane.showMessageDialog(this, "User " + paidBy + " does not exist!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        List<User> participants = new ArrayList<>();
        for (String username : splitBetween) {
            if (!users.containsKey(username)) {
                JOptionPane.showMessageDialog(this, "User " + username + " does not exist!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            participants.add(users.get(username));
        }

        try {
            connection.setAutoCommit(false);
            
            // Insert expense record
            User payer = users.get(paidBy);
            PreparedStatement expenseStmt = connection.prepareStatement(
                "INSERT INTO expenses (paid_by_id, amount, description) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
            expenseStmt.setInt(1, payer.getId());
            expenseStmt.setDouble(2, amount);
            expenseStmt.setString(3, description);
            expenseStmt.executeUpdate();
            
            // Get generated expense ID
            ResultSet generatedKeys = expenseStmt.getGeneratedKeys();
            int expenseId;
            if (generatedKeys.next()) {
                expenseId = generatedKeys.getInt(1);
            } else {
                throw new SQLException("Failed to get expense ID");
            }
            
            // Insert participant records and update balances
            PreparedStatement participantStmt = connection.prepareStatement(
                "INSERT INTO expense_participants (expense_id, user_id, share_amount) VALUES (?, ?, ?)");
            
            for (String username : splitBetween) {
                User participant = users.get(username);
                double userAmount = customAmounts.get(username);
                
                // Add participant to expense
                participantStmt.setInt(1, expenseId);
                participantStmt.setInt(2, participant.getId());
                participantStmt.setDouble(3, userAmount);
                participantStmt.executeUpdate();
                
                // Skip balance update for the payer themselves
                if (!username.equals(paidBy)) {
                    updateBalanceInDatabase(payer, users.get(username), userAmount);
                }
            }
            
            // Create expense object and add to list
            Expense expense = new Expense(payer, amount, description, participants);
            expense.setId(expenseId);
            expenses.add(expense);
            
            connection.commit();
            expenseStmt.close();
            participantStmt.close();
            
            System.out.println("Added custom expense: " + description + " - $" + amount + " paid by " + paidBy);
            
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                rollbackEx.printStackTrace();
            }
            JOptionPane.showMessageDialog(this, 
                "Error adding custom expense: " + e.getMessage(), 
                "Database Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException autoCommitEx) {
                autoCommitEx.printStackTrace();
            }
        }
    }

    private void updateBalanceInDatabase(User creditor, User debtor, double amount) throws SQLException {
        // First check if there's an existing balance in the reverse direction
        PreparedStatement checkReverseStmt = connection.prepareStatement(
            "SELECT amount FROM balances WHERE creditor_id = ? AND debtor_id = ?");
        checkReverseStmt.setInt(1, debtor.getId());
        checkReverseStmt.setInt(2, creditor.getId());
        ResultSet reverseResult = checkReverseStmt.executeQuery();
        
        if (reverseResult.next()) {
            // There's a reverse balance - debtor is owed money by creditor
            double reverseAmount = reverseResult.getDouble("amount");
            
            if (reverseAmount >= amount) {
                // Reduce the existing reverse debt
                PreparedStatement updateReverseStmt = connection.prepareStatement(
                    "UPDATE balances SET amount = ? WHERE creditor_id = ? AND debtor_id = ?");
                updateReverseStmt.setDouble(1, reverseAmount - amount);
                updateReverseStmt.setInt(2, debtor.getId());
                updateReverseStmt.setInt(3, creditor.getId());
                updateReverseStmt.executeUpdate();
                
                // Also update in-memory model
                debtor.getBalances().put(creditor.getName(), reverseAmount - amount);
                
                // If balance is now zero, remove the record
                if (reverseAmount - amount <= 0.001) {
                    PreparedStatement deleteStmt = connection.prepareStatement(
                        "DELETE FROM balances WHERE creditor_id = ? AND debtor_id = ?");
                    deleteStmt.setInt(1, debtor.getId());
                    deleteStmt.setInt(2, creditor.getId());
                    deleteStmt.executeUpdate();
                    
                    // Update in-memory model
                    debtor.getBalances().remove(creditor.getName());
                    deleteStmt.close();
                }
                
                updateReverseStmt.close();
            } else {
                // Reverse the debt direction
                double remainingAmount = amount - reverseAmount;
                
                // Delete the reverse balance
                PreparedStatement deleteReverseStmt = connection.prepareStatement(
                    "DELETE FROM balances WHERE creditor_id = ? AND debtor_id = ?");
                deleteReverseStmt.setInt(1, debtor.getId());
                deleteReverseStmt.setInt(2, creditor.getId());
                deleteReverseStmt.executeUpdate();
                
                // Add new balance in correct direction
                PreparedStatement insertStmt = connection.prepareStatement(
                    "INSERT INTO balances (creditor_id, debtor_id, amount) VALUES (?, ?, ?)");
                insertStmt.setInt(1, creditor.getId());
                insertStmt.setInt(2, debtor.getId());
                insertStmt.setDouble(3, remainingAmount);
                insertStmt.executeUpdate();
                
                // Update in-memory model
                debtor.getBalances().remove(creditor.getName());
                creditor.updateBalance(debtor.getName(), remainingAmount);
                
                deleteReverseStmt.close();
                insertStmt.close();
            }
        } else {
            // No reverse balance, check if there's an existing balance in this direction
            PreparedStatement checkStmt = connection.prepareStatement(
                "SELECT amount FROM balances WHERE creditor_id = ? AND debtor_id = ?");
            checkStmt.setInt(1, creditor.getId());
            checkStmt.setInt(2, debtor.getId());
            ResultSet result = checkStmt.executeQuery();
            
            if (result.next()) {
                // Update existing balance
                double existingAmount = result.getDouble("amount");
                PreparedStatement updateStmt = connection.prepareStatement(
                    "UPDATE balances SET amount = ? WHERE creditor_id = ? AND debtor_id = ?");
                updateStmt.setDouble(1, existingAmount + amount);
                updateStmt.setInt(2, creditor.getId());
                updateStmt.setInt(3, debtor.getId());
                updateStmt.executeUpdate();
                
                // Update in-memory model
                creditor.updateBalance(debtor.getName(), amount);
                
                updateStmt.close();
            } else {
                // Insert new balance
                PreparedStatement insertStmt = connection.prepareStatement(
                    "INSERT INTO balances (creditor_id, debtor_id, amount) VALUES (?, ?, ?)");
                insertStmt.setInt(1, creditor.getId());
                insertStmt.setInt(2, debtor.getId());
                insertStmt.setDouble(3, amount);
                insertStmt.executeUpdate();
                
                // Update in-memory model
                creditor.updateBalance(debtor.getName(), amount);
                
                insertStmt.close();
            }
            
            checkStmt.close();
        }
        
        checkReverseStmt.close();
    }

    public void settleDebt(String from, String to, double amount) {
        if (!users.containsKey(from) || !users.containsKey(to)) {
            JOptionPane.showMessageDialog(this, "One or both users do not exist!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        User fromUser = users.get(from);
        User toUser = users.get(to);

        try {
            // Check if there's a balance to settle
            PreparedStatement checkStmt = connection.prepareStatement(
                "SELECT amount FROM balances WHERE creditor_id = ? AND debtor_id = ?");
            checkStmt.setInt(1, toUser.getId());
            checkStmt.setInt(2, fromUser.getId());
            ResultSet result = checkStmt.executeQuery();
            
            if (result.next() && result.getDouble("amount") >= amount) {
                double currentAmount = result.getDouble("amount");
                double newAmount = currentAmount - amount;
                
                if (newAmount <= 0.001) {
                    // Delete the balance if it's effectively zero
                    PreparedStatement deleteStmt = connection.prepareStatement(
                        "DELETE FROM balances WHERE creditor_id = ? AND debtor_id = ?");
                    deleteStmt.setInt(1, toUser.getId());
                    deleteStmt.setInt(2, fromUser.getId());
                    deleteStmt.executeUpdate();
                    
                    // Update in-memory model
                    toUser.getBalances().remove(from);
                    
                    deleteStmt.close();
                } else {
                    // Update the balance
                    PreparedStatement updateStmt = connection.prepareStatement(
                        "UPDATE balances SET amount = ? WHERE creditor_id = ? AND debtor_id = ?");
                    updateStmt.setDouble(1, newAmount);
                    updateStmt.setInt(2, toUser.getId());
                    updateStmt.setInt(3, fromUser.getId());
                    updateStmt.executeUpdate();
                    
                    // Update in-memory model
                    toUser.getBalances().put(from, newAmount);
                    
                    updateStmt.close();
                }
                
                JOptionPane.showMessageDialog(this, from + " settled $" + amount + " with " + to, 
                    "Success", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Invalid settlement: check the amount and users", 
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
            
            checkStmt.close();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, 
                "Error settling debt: " + e.getMessage(), 
                "Database Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    // Refresh UI methods
    private void refreshUserList() {
        userListModel.clear();
        for (String user : users.keySet()) {
            userListModel.addElement(user);
        }
    }

    private void refreshExpensesTable() {
        expensesTableModel.setRowCount(0);
        
        try {
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(
                "SELECT e.id, e.amount, e.description, e.date, u.username AS paid_by " +
                "FROM expenses e " +
                "JOIN users u ON e.paid_by_id = u.id " +
                "ORDER BY e.date DESC");
            
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            
            while (resultSet.next()) {
                int expenseId = resultSet.getInt("id");
                String paidBy = resultSet.getString("paid_by");
                String description = resultSet.getString("description");
                double amount = resultSet.getDouble("amount");
                Date date = resultSet.getTimestamp("date");
                
                // Get participants for this expense
                PreparedStatement participantsStmt = connection.prepareStatement(
                    "SELECT u.username FROM expense_participants ep " +
                    "JOIN users u ON ep.user_id = u.id " +
                    "WHERE ep.expense_id = ?");
                participantsStmt.setInt(1, expenseId);
                ResultSet participantsResult = participantsStmt.executeQuery();
                
                StringBuilder splitBetween = new StringBuilder();
                while (participantsResult.next()) {
                    splitBetween.append(participantsResult.getString("username")).append(", ");
                }
                
                // Remove the last comma and space
                if (splitBetween.length() > 2) {
                    splitBetween.setLength(splitBetween.length() - 2);
                }
                
                expensesTableModel.addRow(new Object[]{
                    dateFormat.format(date),
                    paidBy,
                    description,
                    String.format("$%.2f", amount),
                    splitBetween.toString()
                });
                
                participantsStmt.close();
            }
            
            statement.close();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, 
                "Error refreshing expenses table: " + e.getMessage(),
                "Database Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private void refreshBalancesTable() {
        balancesTableModel.setRowCount(0);
        
        try {
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(
                "SELECT u1.username AS creditor, u2.username AS debtor, b.amount " +
                "FROM balances b " +
                "JOIN users u1 ON b.creditor_id = u1.id " +
                "JOIN users u2 ON b.debtor_id = u2.id " +
                "ORDER BY b.amount DESC");
            
            while (resultSet.next()) {
                String creditor = resultSet.getString("creditor");
                String debtor = resultSet.getString("debtor");
                double amount = resultSet.getDouble("amount");
                
                balancesTableModel.addRow(new Object[]{
                    debtor,
                    creditor,
                    String.format("$%.2f", amount)
                });
            }
            
            statement.close();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, 
                "Error refreshing balances table: " + e.getMessage(),
                "Database Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private void createDatabaseTables() throws SQLException {
        Statement statement = connection.createStatement();
        
        // Create users table
        String createUsersTable = "CREATE TABLE IF NOT EXISTS users (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "username VARCHAR(100) NOT NULL UNIQUE" +
                ")";
        statement.executeUpdate(createUsersTable);
        
        // Create expenses table
        String createExpensesTable = "CREATE TABLE IF NOT EXISTS expenses (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "paid_by_id INT NOT NULL," +
                "amount DECIMAL(10,2) NOT NULL," +
                "description VARCHAR(255) NOT NULL," +
                "date TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "FOREIGN KEY (paid_by_id) REFERENCES users(id)" +
                ")";
        statement.executeUpdate(createExpensesTable);
        
        // Create expense_participants table (for who's involved in an expense)
        String createParticipantsTable = "CREATE TABLE IF NOT EXISTS expense_participants (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "expense_id INT NOT NULL," +
                "user_id INT NOT NULL," +
                "share_amount DECIMAL(10,2) NOT NULL," +
                "FOREIGN KEY (expense_id) REFERENCES expenses(id) ON DELETE CASCADE," +
                "FOREIGN KEY (user_id) REFERENCES users(id)" +
                ")";
        statement.executeUpdate(createParticipantsTable);
        
        // Create balances table for tracking who owes whom
        String createBalancesTable = "CREATE TABLE IF NOT EXISTS balances (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "creditor_id INT NOT NULL," +
                "debtor_id INT NOT NULL," +
                "amount DECIMAL(10,2) NOT NULL," +
                "FOREIGN KEY (creditor_id) REFERENCES users(id)," +
                "FOREIGN KEY (debtor_id) REFERENCES users(id)," +
                "UNIQUE KEY unique_balance (creditor_id, debtor_id)" +
                ")";
        statement.executeUpdate(createBalancesTable);
        
        statement.close();
    }

    private void loadUsersFromDatabase() {
        try {
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT * FROM users");
            
            while (resultSet.next()) {
                int userId = resultSet.getInt("id");
                String username = resultSet.getString("username");
                User user = new User(username, userId);
                users.put(username, user);
            }
            
            // Load balances
            ResultSet balanceResults = statement.executeQuery(
                "SELECT u1.username AS creditor, u2.username AS debtor, b.amount " +
                "FROM balances b " +
                "JOIN users u1 ON b.creditor_id = u1.id " +
                "JOIN users u2 ON b.debtor_id = u2.id");
            
            while (balanceResults.next()) {
                String creditor = balanceResults.getString("creditor");
                String debtor = balanceResults.getString("debtor");
                double amount = balanceResults.getDouble("amount");
                
                if (users.containsKey(creditor)) {
                    users.get(creditor).getBalances().put(debtor, amount);
                }
            }
            
            statement.close();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, 
                "Error loading users: " + e.getMessage(),
                "Database Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private void loadExpensesFromDatabase() {
        try {
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(
                "SELECT e.id, e.amount, e.description, e.date, u.username AS paid_by " +
                "FROM expenses e " +
                "JOIN users u ON e.paid_by_id = u.id " +
                "ORDER BY e.date DESC");
            
            while (resultSet.next()) {
                int expenseId = resultSet.getInt("id");
                double amount = resultSet.getDouble("amount");
                String description = resultSet.getString("description");
                Date date = resultSet.getTimestamp("date");
                String paidBy = resultSet.getString("paid_by");
                
                if (users.containsKey(paidBy)) {
                    User payer = users.get(paidBy);
                    
                    // Get participants for this expense
                    PreparedStatement participantsStmt = connection.prepareStatement(
                        "SELECT u.username FROM expense_participants ep " +
                        "JOIN users u ON ep.user_id = u.id " +
                        "WHERE ep.expense_id = ?");
                    participantsStmt.setInt(1, expenseId);
                    ResultSet participantsResult = participantsStmt.executeQuery();
                    
                    List<User> participants = new ArrayList<>();
                    while (participantsResult.next()) {
                        String username = participantsResult.getString("username");
                        if (users.containsKey(username)) {
                            participants.add(users.get(username));
                        }
                    }
                    
                    Expense expense = new Expense(payer, amount, description, participants, date, expenseId);
                    expenses.add(expense);
                    participantsStmt.close();
                }
            }
            
            statement.close();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, 
                "Error loading expenses: " + e.getMessage(),
                "Database Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            // Set look and feel to system default
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    ExpenseSplitterGUI gui = new ExpenseSplitterGUI();
                    gui.setVisible(true);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(null, 
                        "Error starting application: " + e.getMessage(),
                        "Application Error", JOptionPane.ERROR_MESSAGE);
                    e.printStackTrace();
                }
            }
        });
    }

    // Inner classes
    static class User {
        private String name;
        private Map<String, Double> balances; // Who owes this user money
        private int id; // Database ID

        public User(String name) {
            this.name = name;
            this.balances = new HashMap<>();
            this.id = -1; // Not yet saved to database
        }
        
        public User(String name, int id) {
            this.name = name;
            this.balances = new HashMap<>();
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public Map<String, Double> getBalances() {
            return balances;
        }
        
        public int getId() {
            return id;
        }
        
        public void setId(int id) {
            this.id = id;
        }

        public void updateBalance(String debtor, double amount) {
            balances.put(debtor, balances.getOrDefault(debtor, 0.0) + amount);
        }
    }

    static class Expense {
        private User paidBy;
        private double amount;
        private String description;
        private List<User> splitBetween;
        private Date date; // This field already exists
        private int id; // Database ID

        public Expense(User paidBy, double amount, String description, List<User> splitBetween) {
            this.paidBy = paidBy;
            this.amount = amount;
            this.description = description;
            this.splitBetween = splitBetween;
            this.date = new Date(); // Using java.util.Date
            this.id = -1; // Not yet saved to database
        }
        
        public Expense(User paidBy, double amount, String description, List<User> splitBetween, 
                       Date date, int id) {
            this.paidBy = paidBy;
            this.amount = amount;
            this.description = description;
            this.splitBetween = splitBetween;
            this.date = date;
            this.id = id;
        }

        public User getPaidBy() {
            return paidBy;
        }

        public double getAmount() {
            return amount;
        }

        public String getDescription() {
            return description;
        }

        public List<User> getSplitBetween() {
            return splitBetween;
        }

        public Date getDate() {
            return date;
        }
        
        public int getId() {
            return id;
        }
        
        public void setId(int id) {
            this.id = id;
        }
    }
}