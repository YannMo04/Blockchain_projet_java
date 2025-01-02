import java.io.*;
import java.net.*;
import java.util.*;
import java.security.MessageDigest;
import java.util.concurrent.*;
import java.time.Instant;

public class BlockchainServer {
    private static final int SERVER_PORT = 8888;
    private static final List<Block> blockchain = Collections.synchronizedList(new ArrayList<>());
    private static final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    private static final ExecutorService pool = Executors.newFixedThreadPool(10);

    public static void main(String[] args) {
        initializeGenesisBlock();
        startServer();
    }

    private static void initializeGenesisBlock() {
        Block genesisBlock = new Block(0, "Genesis", "Genesis", "0");
        blockchain.add(genesisBlock);
        System.out.println("Genesis block créé: " + genesisBlock);
    }

    private static void startServer() {
        try (ServerSocket serverSocket = new ServerSocket(SERVER_PORT)) {
            System.out.println("SERVEUR BLOCKCHAIN EN ECOUTE SUR LE PORT " + SERVER_PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("CLIENT CONNECTE : " + clientSocket.getInetAddress().getHostAddress());
                ClientHandler handler = new ClientHandler(clientSocket);
                clients.add(handler);
                pool.submit(handler);
            }
        } catch (IOException e) {
            System.err.println("Erreur du serveur : " + e.getMessage());
            pool.shutdown();
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private boolean isRunning = true;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                setupStreams();
                handleClientCommunication();
            } catch (IOException e) {
                System.err.println("Error handling client: " + e.getMessage());
            } finally {
                cleanup();
            }
        }

        private void setupStreams() throws IOException {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            sendBlockchain();
        }

        private void handleClientCommunication() throws IOException {
            String request;
            while (isRunning && (request = in.readLine()) != null) {
                try {
                    processRequest(request);
                } catch (Exception e) {
                    out.println("Error processing request: " + e.getMessage());
                }
            }
        }

        private void processRequest(String request) {
            String[] parts = request.split(":");
            String command = parts[0];

            switch (command) {
                case "1": // Show blockchain
                    sendBlockchain();
                    break;
                case "2": // Add block
                    if (parts.length == 4) {
                        addBlock(parts[1], parts[2], parts[3]);
                    }
                    break;
                case "3": // Mine block
                    mineBlock();
                    break;
                case "4": // Test double spending
                    testDoubleSpending();
                    break;
                case "5": // Disconnect
                    disconnect();
                    break;
                default:
                    out.println("Commande invalide");
            }
        }

        private void sendBlockchain() {
            synchronized (blockchain) {
                out.println("Etat actuel de la blockchain:");
                for (Block block : blockchain) {
                    out.println(block);
                }
            }
        }

        private void addBlock(String from, String to, String amount) {
            try {
                double amountValue = Double.parseDouble(amount);
                if (amountValue <= 0) {
                    throw new IllegalArgumentException("Le montant doit être positif");
                }

                Block lastBlock = blockchain.get(blockchain.size() - 1);
                Block newBlock = new Block(
                        lastBlock.getIndex() + 1,
                        from,
                        to,
                        amount);

                blockchain.add(newBlock);
                broadcast("Nouveau Block ajouté : " + newBlock);
            } catch (NumberFormatException e) {
                out.println("Invalid amount format");
            } catch (IllegalArgumentException e) {
                out.println(e.getMessage());
            }
        }

        private void mineBlock() {
            Block lastBlock = blockchain.get(blockchain.size() - 1);
            Block minedBlock = new Block(
                    lastBlock.getIndex() + 1,
                    "MINER",
                    "MINER_REWARD",
                    "50");

            blockchain.add(minedBlock);
            broadcast("Block Miné: " + minedBlock);
        }

        private void testDoubleSpending() {
            out.println("Test sur la double dépense ...");
            Map<String, Double> balances = calculateBalances();
            boolean doubleSpendingDetected = false;

            for (Map.Entry<String, Double> entry : balances.entrySet()) {
                if (entry.getValue() < 0) {
                    doubleSpendingDetected = true;
                    out.println("Double dépense detecté sur le compte: " + entry.getKey());
                }
            }

            if (!doubleSpendingDetected) {
                out.println("Pas de double dépense détecté");
            }
        }

        private Map<String, Double> calculateBalances() {
            Map<String, Double> balances = new HashMap<>();

            synchronized (blockchain) {
                for (Block block : blockchain) {
                    String from = block.getFrom();
                    String to = block.getTo();
                    double amount = Double.parseDouble(block.getAmount());

                    if (!from.equals("Genesis") && !from.equals("MINER")) {
                        balances.merge(from, -amount, Double::sum);
                    }
                    balances.merge(to, amount, Double::sum);
                }
            }

            return balances;
        }

        private void broadcast(String message) {
            for (ClientHandler client : clients) {
                client.send(message);
            }
        }

        private void send(String message) {
            if (out != null) {
                out.println(message);
            }
        }

        private void disconnect() {
            isRunning = false;
        }

        private void cleanup() {
            clients.remove(this);
            System.out.println("CLIENT DECONNECTE : " + socket.getInetAddress().getHostAddress());
            try {
                if (out != null)
                    out.close();
                if (in != null)
                    in.close();
                if (socket != null)
                    socket.close();
            } catch (IOException e) {
                System.err.println("Erreur lors du nettoyage : " + e.getMessage());
            }
        }

    }

    private static class Block {
        private final int index;
        private final String from;
        private final String to;
        private final String amount;
        private final long timestamp;
        private final String hash;
        private final String previousHash;

        public Block(int index, String from, String to, String amount) {
            this.index = index;
            this.from = from;
            this.to = to;
            this.amount = amount;
            this.timestamp = Instant.now().getEpochSecond();
            this.previousHash = index > 0 ? blockchain.get(index - 1).getHash() : "0";
            this.hash = calculateHash();
        }

        public int getIndex() {
            return index;
        }

        public String getFrom() {
            return from;
        }

        public String getTo() {
            return to;
        }

        public String getAmount() {
            return amount;
        }

        public String getHash() {
            return hash;
        }

        private String calculateHash() {
            try {
                String data = index + from + to + amount + timestamp + previousHash;
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(data.getBytes());
                StringBuilder hexString = new StringBuilder();
                for (byte b : hash) {
                    String hex = Integer.toHexString(0xff & b);
                    if (hex.length() == 1)
                        hexString.append('0');
                    hexString.append(hex);
                }
                return hexString.toString();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String toString() {
            return String.format("Block #%d [From: %s, To: %s, Amount: %s, Hash: %s...]",
                    index, from, to, amount, hash.substring(0, 10));
        }
    }
}