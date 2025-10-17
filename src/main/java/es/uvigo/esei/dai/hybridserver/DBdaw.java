package es.uvigo.esei.dai.hybridserver;

import es.uvigo.esei.dai.hybridserver.http.HTTPRequest;
import es.uvigo.esei.dai.hybridserver.http.HTTPResponseStatus;

import java.io.IOException;
import java.sql.*;
import java.util.Map;
import java.util.UUID;

public class DBdaw implements HtmlRepository {

    private final String url;
    private final String user;
    private final String pass;

    public DBdaw(String url, String user, String pass) {
        this.url = url;
        this.user = user;
        this.pass = pass;
    }

    /** 🔹 Método auxiliar para abrir una nueva conexión por petición */
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, pass);
    }

    @Override
    public String getPage(HTTPRequest req) throws IOException {
        // 1) raíz → página de bienvenida (200)
        final String chain = req.getResourceChain();
        if (chain == null || chain.isEmpty() || "/".equals(chain)) {
            return ok("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>Hybrid Server</title></head>"
                    + "<body><h1>Hybrid Server</h1><p>Data Base Mode</p></body></html>");
        }

        // 2) solo aceptamos /html, si no → 400 (según los tests)
        final String resource = req.getResourceName(); // "html" sin query
        if (resource == null || !resource.equals("html")) {
            return err(HTTPResponseStatus.S400, "Bad Request");
        }

        final Map<String, String> params = req.getResourceParameters();
        final String uuid = params != null ? params.get("uuid") : null;

        // GET /html → listado
        if (uuid == null || uuid.isBlank()) {
            StringBuilder html = new StringBuilder("<html><body><h1>Paginas</h1><ul>");
            try (Connection c = getConnection();
                    PreparedStatement ps = c.prepareStatement("SELECT uuid FROM hstestdb.HTML ORDER BY uuid");
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("uuid");
                    html.append("<li><a href=\"html?uuid=").append(id).append("\">").append(id).append("</a></li>");
                }
            } catch (SQLException e) {
                return err(HTTPResponseStatus.S500, "Database error");
            }
            html.append("</ul></body></html>");
            return ok(html.toString());
        }

        // GET /html?uuid=xxx → recuperar página
        try (Connection c = getConnection();
                PreparedStatement ps = c.prepareStatement("SELECT content FROM HTML WHERE uuid = ?")) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String content = rs.getString("content");
                    return ok(content);
                } else {
                    return err(HTTPResponseStatus.S404, "Not found");
                }
            }
        } catch (SQLException e) {
            return err(HTTPResponseStatus.S500, "Database error");
        }
    }

    @Override
    public String postPage(HTTPRequest req) throws IOException {
        final String resource = req.getResourceName();
        if (resource == null || !resource.equals("html"))
            return err(HTTPResponseStatus.S400, "Bad Request");

        final Map<String, String> params = req.getResourceParameters();
        final String htmlContent = (params != null) ? params.get("html") : null;

        if (htmlContent == null || htmlContent.isBlank())
            return err(HTTPResponseStatus.S400, "Missing html parameter");

        final String uuid = UUID.randomUUID().toString();

        try (Connection c = getConnection();
                PreparedStatement ps = c.prepareStatement("INSERT INTO HTML (uuid, content) VALUES (?, ?)")) {
            ps.setString(1, uuid);
            ps.setString(2, htmlContent);
            ps.executeUpdate();
        } catch (SQLException e) {
            return err(HTTPResponseStatus.S500, "Database error");
        }

        final String link = "<a href=\"html?uuid=" + uuid + "\">" + uuid + "</a>";
        final String body = "<html><body><h1>Created</h1><p>" + link + "</p></body></html>";
        return ok(body);
    }

    @Override
    public String deletePage(HTTPRequest req) throws IOException {
        final String resource = req.getResourceName();
        if (resource == null || !resource.equals("html"))
            return err(HTTPResponseStatus.S400, "Bad Request");

        final Map<String, String> params = req.getResourceParameters();
        final String uuid = (params != null) ? params.get("uuid") : null;

        if (uuid == null || uuid.isBlank()) {
            return err(HTTPResponseStatus.S400, "Missing uuid parameter");
        }

        try (Connection c = getConnection();
                PreparedStatement ps = c.prepareStatement("DELETE FROM HTML WHERE uuid = ?")) {
            ps.setString(1, uuid);
            int deleted = ps.executeUpdate();
            if (deleted == 0)
                return err(HTTPResponseStatus.S404, "Page not found");
        } catch (SQLException e) {
            return err(HTTPResponseStatus.S500, "Database error");
        }

        return ok("<html><body><h1>Deleted</h1></body></html>");
    }

    @Override
    public boolean exists(String path) {
        return false;
    }

    private String ok(String body) {
        return "HTTP/1.1 200 OK\r\n"
                + "Content-Type: text/html\r\n"
                + "Connection: close\r\n"
                + "\r\n"
                + body;
    }

    private String err(HTTPResponseStatus status, String msg) {
        return "HTTP/1.1 " + status.getCode() + " " + status.getStatus() + "\r\n"
                + "Content-Type: text/html\r\n"
                + "Connection: close\r\n\r\n"
                + "<html><body><h1>" + status.getCode() + " " + status.getStatus() + "</h1><p>"
                + msg + "</p></body></html>";
    }
}
