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
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                threadPool.submit(new ClientHandler(socket));
            }
        } finally {
            threadPool.shutdown();
        }
    }

    private static class ClientHandler implements Runnable {
        Socket socket;
        private String nickname;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
                try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                     Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {

                    if (!authenticateUser(in, out, connection)) {
                        return;
                    }

                    String request;
                    while ((request = in.readLine()) != null) {
                        System.out.println("Received" + request);
                        String response = requestHandler(request, connection);
                        System.out.println(response);
                        out.println(response);
                    }
                }
            } catch (SQLException | ClassNotFoundException | IOException e) {
                System.out.println(e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    System.out.println(e.getMessage());
                }
            }
        }

        private String requestHandler(String request, Connection connection) throws SQLException {
            String[] parts = request.split(" ", 2);
            String command = parts[0].toLowerCase();
            String argument = parts.length > 1 ? parts[1] : "";

            switch (command) {
                case "add":
                    String[] parameters = argument.split(",", 2);
                    return addTask(connection, nickname, parameters[0], parameters[1]);
                case "list":
                    return listTask(connection);
                case "completed":
                    return setComplete(connection, argument);
                case "delete":
                    return deleteTask(connection, argument);
                default:
                    return "Invalid";
            }
        }

        private String deleteTask(Connection connection, String identifier) throws SQLException{
            String query;
            PreparedStatement preparedStatement;

            if (isNumeric(identifier) /* Return true if identifier is String */) {
                query = "DELETE FROM tasks WHERE id = ? AND owner = ?";
                preparedStatement = connection.prepareStatement(query);
                preparedStatement.setInt(1, Integer.parseInt(identifier));
            } else {
                query = "DELETE FROM tasks WHERE title = ? AND owner = ?";
                preparedStatement = connection.prepareStatement(query);
                preparedStatement.setString(1, identifier);
            }
            preparedStatement.setString(2, nickname);
            int rowsAffected = preparedStatement.executeUpdate();

            return rowsAffected > 0 ? "Deleted" : "Not found";
        }

        private String setComplete(Connection connection, String identifier) throws SQLException{
            String query;
            PreparedStatement preparedStatement;

            if (isNumeric(identifier) /* Return true if identifier is String */) {
                query = "UPDATE tasks SET completed = 1 WHERE id = ? AND owner = ? AND completed = 0";
                preparedStatement = connection.prepareStatement(query);
                preparedStatement.setInt(1, Integer.parseInt(identifier));
            } else {
                query = "UPDATE tasks SET completed = 1 WHERE title = ? AND owner = ? AND completed = 0";
                preparedStatement = connection.prepareStatement(query);
                preparedStatement.setString(1, identifier);
            }
            preparedStatement.setString(2, nickname);
            int rowsAffected = preparedStatement.executeUpdate();

            return rowsAffected > 0 ? "Completed" : "Not found or Already Completed";
        }

        private String listTask(Connection connection) throws SQLException {
            StringBuilder response = new StringBuilder();
            String query = "SELECT * FROM tasks";
            try (Statement statement = connection.createStatement()) {
                ResultSet resultSet = statement.executeQuery(query);
                while (resultSet.next()) {
                    response.append("ID: ").append(resultSet.getInt("id"))
                            .append(", Title: ").append(resultSet.getString("title"))
                            .append(", Task: ").append(resultSet.getString("task"))
                            .append(", Created at: ").append(resultSet.getTimestamp("created_at"))
                            .append(", Status: ").append(resultSet.getBoolean("completed") ? "Done" : "Not completed")
                            .append("\n");
                }
            }
            return response.toString();
        }

        private String addTask(Connection connection, String owner, String title, String task) throws SQLException {
            String query = "INSERT INTO tasks (owner, title, task) VALUES (?, ?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setString(1, owner);
                preparedStatement.setString(2, title);
                preparedStatement.setString(3, task);
                preparedStatement.executeUpdate();
                return "Task added";
            }
        }

        private boolean authenticateUser(BufferedReader in, PrintWriter out, Connection connection) throws IOException, SQLException {
            out.println("Nickname: "); out.flush();
            nickname = in.readLine();

            String query = "SELECT * FROM users WHERE username = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setString(1, nickname);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (!resultSet.next()) {
                        out.println("User doesn't exist"); out.flush();
                        registerUser(in, out, connection);
                        out.println("You can now log in"); out.flush();
                        return false;
                    } else {
                        out.println("Enter your password: ");
                        String password = in.readLine();
                        if (!password.equals(resultSet.getString("password"))) {
                            out.println("Wrong password"); out.flush();
                            return false;
                        }
                        return true;
                    }
                }
            }
        }

        private void registerUser(BufferedReader in, PrintWriter out, Connection connection) throws SQLException, IOException{
            out.println("Enter a password: "); out.flush();
            String password = in.readLine();

            String query = "INSERT INTO users (username, password) VALUES (?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setString(1, nickname);
                preparedStatement.setString(2, password);
                preparedStatement.executeUpdate();
            }
        }

        private boolean isNumeric(String txt) {
            try {
                Integer.parseInt(txt);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }
}