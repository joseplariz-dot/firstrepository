import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class Main {

    private static String getJdbcUrl() {
        String dbUrl = System.getenv("DATABASE_URL");
        if (dbUrl == null || dbUrl.isEmpty()) {
            return null;
        }
        // If the URL starts with postgresql:// or postgres://, prepend jdbc:
        if (dbUrl.startsWith("postgresql://") || dbUrl.startsWith("postgres://")) {
            return "jdbc:" + dbUrl;
        }
        return dbUrl;
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        String dbUrl = getJdbcUrl();

        if (dbUrl != null) {
            try (Connection conn = DriverManager.getConnection(dbUrl)) {
                Statement stmt = conn.createStatement();
                stmt.execute("CREATE TABLE IF NOT EXISTS users (id SERIAL PRIMARY KEY, name TEXT, age INT, address TEXT);");
            } catch (Exception e) {
                System.err.println("Database setup warning: " + e.getMessage());
            }
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new HomeHandler());
        server.createContext("/save", new SaveHandler(dbUrl));
        server.createContext("/users", new UsersHandler(dbUrl));

        server.setExecutor(null);
        System.out.println("Server started on port " + port);
        server.start();
    }

    static class HomeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = "<html>"
                    + "<head><title>User Data Form</title></head>"
                    + "<body style='font-family: Arial, sans-serif; margin: 40px;'>"
                    + "<h2>Enter User Information</h2>"
                    + "<form action='/save' method='POST'>"
                    + "  <label>Name:</label><br>"
                    + "  <input type='text' name='name' required><br><br>"
                    + "  <label>Age:</label><br>"
                    + "  <input type='number' name='age' required><br><br>"
                    + "  <label>Address:</label><br>"
                    + "  <input type='text' name='address' required><br><br>"
                    + "  <button type='submit'>Save Data</button>"
                    + "</form>"
                    + "<br><hr><br>"
                    + "<a href='/users'>View Saved Users</a>"
                    + "</body>"
                    + "</html>";

            byte[] responseBytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }

    static class SaveHandler implements HttpHandler {
        private final String dbUrl;

        public SaveHandler(String dbUrl) {
            this.dbUrl = dbUrl;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStream is = exchange.getRequestBody();
                String formData = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                String name = getParam(formData, "name");
                int age = 0;
                try {
                    age = Integer.parseInt(getParam(formData, "age"));
                } catch (NumberFormatException ignored) {}
                String address = getParam(formData, "address");

                boolean saved = false;
                if (dbUrl != null) {
                    try (Connection conn = DriverManager.getConnection(dbUrl)) {
                        String sql = "INSERT INTO users (name, age, address) VALUES (?, ?, ?)";
                        PreparedStatement pstmt = conn.prepareStatement(sql);
                        pstmt.setString(1, name);
                        pstmt.setInt(2, age);
                        pstmt.setString(3, address);
                        pstmt.executeUpdate();
                        saved = true;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                String responseHtml = "<html><body style='font-family: Arial, sans-serif; margin: 40px;'>"
                        + (saved ? "<h3>Data saved successfully!</h3>" : "<h3>Error: Could not save data to database.</h3>")
                        + "<a href='/'>Go Back</a> | <a href='/users'>View All Users</a>"
                        + "</body></html>";

                byte[] responseBytes = responseHtml.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            }
        }

        private String getParam(String body, String key) {
            for (String pair : body.split("&")) {
                String[] kv = pair.split("=");
                if (kv.length > 0 && kv[0].equals(key)) {
                    return kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
                }
            }
            return "";
        }
    }

    static class UsersHandler implements HttpHandler {
        private final String dbUrl;

        public UsersHandler(String dbUrl) {
            this.dbUrl = dbUrl;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder html = new StringBuilder();
            html.append("<html><body style='font-family: Arial, sans-serif; margin: 40px;'>");
            html.append("<h2>All Registered Users</h2>");

            if (dbUrl != null) {
                try (Connection conn = DriverManager.getConnection(dbUrl)) {
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT * FROM users ORDER BY id DESC");

                    html.append("<table border='1' cellpadding='8' cellspacing='0'>");
                    html.append("<tr><th>ID</th><th>Name</th><th>Age</th><th>Address</th></tr>");

                    boolean hasData = false;
                    while (rs.next()) {
                        hasData = true;
                        html.append("<tr>")
                            .append("<td>").append(rs.getInt("id")).append("</td>")
                            .append("<td>").append(rs.getString("name")).append("</td>")
                            .append("<td>").append(rs.getInt("age")).append("</td>")
                            .append("<td>").append(rs.getString("address")).append("</td>")
                            .append("</tr>");
                    }
                    html.append("</table>");

                    if (!hasData) {
                        html.append("<p>No users found in database yet.</p>");
                    }
                } catch (Exception e) {
                    html.append("<p style='color: red;'>Database error: ").append(e.getMessage()).append("</p>");
                }
            } else {
                html.append("<p style='color: red;'>DATABASE_URL environment variable is missing.</p>");
            }

            html.append("<br><a href='/'>Add Another Entry</a></body></html>");

            byte[] responseBytes = html.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }
}
