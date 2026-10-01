import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class Main {

    public static void main(String[] args) throws Exception {
        // Get PORT and Database credentials from environment variables (provided by Render)
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        String dbUrl = System.getenv("DATABASE_URL");

        // Initialize Database Table if needed
        if (dbUrl != null) {
            try (Connection conn = DriverManager.getConnection(dbUrl)) {
                Statement stmt = conn.createStatement();
                stmt.execute("CREATE TABLE IF NOT EXISTS users (id SERIAL PRIMARY KEY, name TEXT, age INT, address TEXT);");
            } catch (Exception e) {
                System.out.println("DB Setup Warning: " + e.getMessage());
            }
        }

        // Start Java's built-in HTTP server
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // Route 1: Main Web Form Page
        server.createContext("/", new RootHandler());
        
        // Route 2: API to Save Data
        server.createContext("/save", new SaveHandler(dbUrl));
        
        // Route 3: API to View Data
        server.createContext("/users", new UsersHandler(dbUrl));

        server.setExecutor(null);
        System.out.println("Server started on port " + port);
        server.start();
    }

    // HTML Web Form Handler
    static class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = "<html><body>"
                    + "<h2>Enter User Details</h2>"
                    + "<form action='/save' method='POST'>"
                    + "  Name: <input type='text' name='name'><br><br>"
                    + "  Age: <input type='number' name='age'><br><br>"
                    + "  Address: <input type='text' name='address'><br><br>"
                    + "  <input type='submit' value='Submit'>"
                    + "</form>"
                    + "<br><a href='/users'>View All Submitted Users</a>"
                    + "</body></html>";

            exchange.sendResponseHeaders(200, html.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(html.getBytes());
            os.close();
        }
    }

    // Save Data to PostgreSQL Handler
    static class SaveHandler implements HttpHandler {
        private String dbUrl;
        public SaveHandler(String dbUrl) { this.dbUrl = dbUrl; }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStream is = exchange.getRequestBody();
                String formData = new String(is.readAllBytes());
                
                // Parse simple form data: name=John&age=25&address=123+Main+St
                String name = getParam(formData, "name");
                int age = Integer.parseInt(getParam(formData, "age").replaceAll("[^0-9]", "0"));
                String address = getParam(formData, "address");

                if (dbUrl != null) {
                    try (Connection conn = DriverManager.getConnection(dbUrl)) {
                        String sql = "INSERT INTO users (name, age, address) VALUES (?, ?, ?)";
                        PreparedStatement pstmt = conn.prepareStatement(sql);
                        pstmt.setString(1, name);
                        pstmt.setInt(2, age);
                        pstmt.setString(3, address);
                        pstmt.executeUpdate();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                String response = "<html><body><h3>Data Saved Successfully!</h3><a href='/'>Go Back</a> | <a href='/users'>View All Users</a></body></html>";
                exchange.sendResponseHeaders(200, response.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        }

        private String getParam(String body, String key) {
            for (String pair : body.split("&")) {
                String[] kv = pair.split("=");
                if (kv.length > 0 && kv[0].equals(key)) {
                    return kv.length > 1 ? java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8) : "";
                }
            }
            return "";
        }
    }

    // Retrieve Data from PostgreSQL Handler
    static class UsersHandler implements HttpHandler {
        private String dbUrl;
        public UsersHandler(String dbUrl) { this.dbUrl = dbUrl; }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder html = new StringBuilder("<html><body><h2>Saved Users</h2><ul>");
            
            if (dbUrl != null) {
                try (Connection conn = DriverManager.getConnection(dbUrl)) {
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT * FROM users");
                    while (rs.next()) {
                        html.append("<li>")
                            .append("<b>Name:</b> ").append(rs.getString("name"))
                            .append(" | <b>Age:</b> ").append(rs.getInt("age"))
                            .append(" | <b>Address:</b> ").append(rs.getString("address"))
                            .append("</li>");
                    }
                } catch (Exception e) {
                    html.append("<p>Error fetching users: ").append(e.getMessage()).append("</p>");
                }
            } else {
                html.append("<p>Database connection not configured.</p>");
            }

            html.append("</ul><br><a href='/'>Add Another Entry</a></body></html>");

            exchange.sendResponseHeaders(200, html.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(html.toString().getBytes());
            os.close();
        }
    }
}
