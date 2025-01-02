import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

@SuppressWarnings("unused")
public class BlockchainClient extends JFrame {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 8888;
    private static final int WINDOW_WIDTH = 800;
    private static final int WINDOW_HEIGHT = 600;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private JTextArea logArea;
    private JLabel statusLabel;

    public BlockchainClient() {
        super("Blockchain Client");
        setupGUI();
        connectToServer();
    }

    private void setupGUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        setLocationRelativeTo(null);

        // Main Panel avec BorderLayout
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Panel de boutons à gauche
        JPanel buttonPanel = createButtonPanel();
        mainPanel.add(buttonPanel, BorderLayout.WEST);

        // Zone de log au centre
        createLogArea();
        mainPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);

        // Barre de statut en bas
        statusLabel = new JLabel("Déconnecté");
        mainPanel.add(statusLabel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new GridLayout(6, 1, 0, 10));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));

        String[] buttonLabels = {
                "Afficher Blockchain",
                "Ajouter Transaction",
                "Miner un Bloc",
                "Vérifier Double Dépense",
                "Effacer Log",
                "Quitter"
        };

        for (String label : buttonLabels) {
            JButton button = createStyledButton(label);
            buttonPanel.add(button);
        }

        return buttonPanel;
    }

    private JButton createStyledButton(String text) {
        JButton button = new JButton(text);
        button.setPreferredSize(new Dimension(180, 40));
        button.setFont(new Font("Arial", Font.PLAIN, 12));
        button.setFocusPainted(false);

        button.addActionListener(e -> handleButtonClick(text));

        return button;
    }

    private void createLogArea() {
        logArea = new JTextArea();
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setEditable(false);
        logArea.setMargin(new Insets(10, 10, 10, 10));
    }

    private void handleButtonClick(String command) {
        if (!isConnected()) {
            appendToLog("Non connecté au serveur. Tentative de reconnexion...");
            connectToServer();
            return;
        }

        try {
            switch (command) {
                case "Afficher Blockchain":
                    sendCommand("1");
                    break;
                case "Ajouter Transaction":
                    showTransactionDialog();
                    break;
                case "Miner un Bloc":
                    sendCommand("3");
                    break;
                case "Vérifier Double Dépense":
                    sendCommand("4");
                    break;
                case "Effacer Log":
                    logArea.setText("");
                    break;
                case "Quitter":
                    closeConnection();
                    System.exit(0);
                    break;
            }
        } catch (Exception e) {
            appendToLog("Erreur: " + e.getMessage());
        }
    }

    private void showTransactionDialog() {
        JPanel panel = new JPanel(new GridLayout(3, 2, 5, 5));

        JTextField fromField = new JTextField();
        JTextField toField = new JTextField();
        JTextField amountField = new JTextField();

        panel.add(new JLabel("De:"));
        panel.add(fromField);
        panel.add(new JLabel("À:"));
        panel.add(toField);
        panel.add(new JLabel("Montant:"));
        panel.add(amountField);

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Nouvelle Transaction", JOptionPane.OK_CANCEL_OPTION);

        if (result == JOptionPane.OK_OPTION) {
            try {
                validateTransactionInput(fromField.getText(), toField.getText(), amountField.getText());
                String data = String.format("2:%s:%s:%s",
                        fromField.getText(),
                        toField.getText(),
                        amountField.getText());
                sendCommand(data);
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void validateTransactionInput(String from, String to, String amount) {
        if (from.isEmpty() || to.isEmpty() || amount.isEmpty()) {
            throw new IllegalArgumentException("Tous les champs sont obligatoires");
        }
        try {
            double value = Double.parseDouble(amount);
            if (value <= 0) {
                throw new IllegalArgumentException("Le montant doit être positif");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Montant invalide");
        }
    }

    private void connectToServer() {
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            statusLabel.setText("Connecté au serveur: " + SERVER_ADDRESS + ":" + SERVER_PORT);

            // Thread pour lire les réponses du serveur
            new Thread(this::readServerResponses).start();

            appendToLog("Connecté au serveur blockchain");
        } catch (IOException e) {
            statusLabel.setText("Déconnecté");
            appendToLog("Erreur de connexion: " + e.getMessage());
        }
    }

    private void readServerResponses() {
        try {
            String response;
            while ((response = in.readLine()) != null) {
                final String message = response;
                SwingUtilities.invokeLater(() -> appendToLog(message));
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("Déconnecté");
                appendToLog("Connexion perdue avec le serveur");
            });
        }
    }

    private void sendCommand(String command) {
        if (out != null) {
            out.println(command);
        }
    }

    private void appendToLog(String message) {
        logArea.append(message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private boolean isConnected() {
        return socket != null && !socket.isClosed() && socket.isConnected();
    }

    private void closeConnection() {
        try {
            if (out != null)
                out.println("5"); // Commande de déconnexion
            if (socket != null)
                socket.close();
        } catch (IOException e) {
            appendToLog("Erreur lors de la fermeture de la connexion: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            new BlockchainClient().setVisible(true);
        });
    }
}