package Server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/todo_list";
    private static final String DB_PASSWORD = "";
    private static final String DB_USER = "root";
    private static final int PORT = 8080;
    private static ExecutorService threadPool = Executors.newCachedThreadPool();

    public static void main(String[] args) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(PORT)){
            while (true) {
                Socket socket = serverSocket.accept();
                threadPool.submit(new ClientHandler(socket));
            }
        } finally {
            threadPool.shutdown();
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private String nickname;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");

                try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                     PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                     Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {

                    if (!authenticateUser(connection, in, out)) {
                        return;
                    }

                    String request;
                    while ((request = in.readLine()) != null) {
                        System.out.println("Received: " + request);
                        String response = handleRequest(request, connection);
                        out.println(response);
                        out.flush();
                    }
                }
            } catch (SQLException | IOException | ClassNotFoundException e) {
                e.printStackTrace();
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private String handleRequest(String request, Connection connection) {
            String[] parts = request.split(" ", 2);
            String command = parts[0].toLowerCase();
            String argument = parts.length > 1 ? parts[1] : "";

            try {
                switch (command) {
                    case "add":
                        String[] parameters = argument.split(", ", 3);
                        return addTask(connection, Integer.parseInt(parameters[0]), parameters[1], Integer.parseInt(parameters[2]));
                    case "list":
                        return listTask(connection);
                    case "completed":
                        return setCompleted(connection, Integer.parseInt(argument));
                    case "delete":
                        return deleteTask(connection, Integer.parseInt(argument));
                    default:
                        return "Invalid command or parameters";
                }
            } catch (Exception e) {
                e.printStackTrace();
                return "Error";
            }
        }

        private String setCompleted(Connection connection, int ID) throws SQLException {
            String query = "UPDATE tasks SET completed = 1 WHERE id = ? AND completed = 0";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setInt(1, ID);
                preparedStatement.executeUpdate();
                return "Task Completed";
            }
        }

        private String deleteTask(Connection connection, int ID) throws SQLException {
            String query = "DELETE FROM tasks WHERE id = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setInt(1, ID);
                preparedStatement.executeUpdate();
                return "Deleted";
            }
        }

        private String listTask(Connection connection) throws SQLException {
            StringBuilder response = new StringBuilder();
            String query = "SELECT * FROM tasks";
            try (Statement statement = connection.createStatement()) {
                ResultSet resultSet = statement.executeQuery(query);
                while (resultSet.next()) {
                    response.append("ID: ").append(resultSet.getInt("id"))
                            .append(", Task: ").append(resultSet.getString("task"))
                            .append(", Created at: ").append(resultSet.getTimestamp("created_at"))
                            .append(", Status: ").append(resultSet.getBoolean("completed") ? "Done" : "Not completed")
                            .append("\n");
                }
            }
            return response.toString();
        }

        private String addTask(Connection connection, int userId, String task, int status) throws SQLException {
            String query = "INSERT INTO tasks (user_id, task, completed) VALUES (?, ?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setInt(1, userId);
                preparedStatement.setString(2, task);
                preparedStatement.setInt(3, status);
                preparedStatement.executeUpdate();
                return "Task added";
            }
        }

        private boolean authenticateUser(Connection connection, BufferedReader in, PrintWriter out) throws SQLException, IOException {
            out.println("Nickname: ");
            out.flush();
            nickname = in.readLine();

            String nickQuery = "SELECT * FROM users WHERE username = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(nickQuery)) {
                preparedStatement.setString(1, nickname);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (!resultSet.next()) {
                        out.println("User doesn't exist. Please register.");
                        out.flush();
                        registerUser(connection, in, out);
                        out.println("You can now log in.");
                        out.flush();
                        return false;
                    } else {
                        out.println("Enter your password: ");
                        out.flush();
                        String password = in.readLine();
                        if (!password.equals(resultSet.getString("password"))) {
                            out.println("Wrong password");
                            out.flush();
                            return false;
                        } else {
                            return true;
                        }
                    }
                }
            }
        }

        private void registerUser(Connection connection, BufferedReader in, PrintWriter out) throws SQLException, IOException {
            out.println("Enter a password: ");
            out.flush();
            String password = in.readLine();

            String query = "INSERT INTO users (username, password) VALUES (?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setString(1, nickname);
                preparedStatement.setString(2, password);
                preparedStatement.executeUpdate();
            }
        }
    }
}
