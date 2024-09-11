package Client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Client {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 8080;
    static String title, task;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             BufferedReader consoleInput = new BufferedReader(new InputStreamReader(System.in));
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            System.out.println("Connected to Server");
            authenticateUser(in, out, consoleInput);

            while (true) {
                printMenu();
                String command = consoleInput.readLine().toLowerCase();
                switch (command) {
                    case "add task":
                        addTask(out, in, consoleInput);
                        break;
                    case "list task":
                        listTask(out, in);
                        break;
                    case "set complete":
                        setComplete(out, in, consoleInput);
                        break;
                    case "delete task":
                        deleteTask(out, in, consoleInput);
                        break;
                    default:
                        System.out.println("Invalid command");
                }
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
    private static void setComplete(PrintWriter out, BufferedReader in, BufferedReader consoleInput) throws IOException {
        System.out.print("Enter task ID or title to mark as complete: ");
        String identifier = consoleInput.readLine();
        out.println("completed " + identifier);
        System.out.println(in.readLine());
    }

    private static void listTask(PrintWriter out, BufferedReader in) throws IOException {
        out.println("list");
        String response;
        while (!(response = in.readLine()).isEmpty()) {
            System.out.println(response);
        }
    }

    private static void addTask(PrintWriter out, BufferedReader in, BufferedReader consoleInput) throws IOException {
        System.out.print("Title: ");
        String title = consoleInput.readLine();
        System.out.print("Task: ");
        String task = consoleInput.readLine();

        out.println("add " + title + "," + task);
        System.out.println(in.readLine());
    }

    private static void deleteTask(PrintWriter out, BufferedReader in, BufferedReader consoleInput) throws IOException {
        System.out.print("Enter task ID or title to delete: ");
        String identifier = consoleInput.readLine();
        out.println("delete " + identifier);
        System.out.println(in.readLine());
    }

    private static void printMenu() {
        System.out.print("1. Add task\n2. List tasks\n3. Set complete\n4. Delete task\nEnter command: ");
    }

    private static void authenticateUser(BufferedReader in, PrintWriter out, BufferedReader consoleInput) throws IOException {
        String username;
        String password;
        System.out.println(in.readLine());
        if (in.readLine().equals("Nickname: ")) {
            System.out.println(in.readLine());
            username = consoleInput.readLine();
            out.println(username);
        } else if (in.readLine().equals("Enter a password: ") || in.readLine().equals("Enter your password: ")) {
            System.out.println(in.readLine());
            password = consoleInput.readLine();
            out.println(password);
        }
    }
}
