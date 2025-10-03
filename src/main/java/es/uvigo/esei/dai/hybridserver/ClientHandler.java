package es.uvigo.esei.dai.hybridserver;

import es.uvigo.esei.dai.hybridserver.http.HTTPRequest;
import es.uvigo.esei.dai.hybridserver.http.HTTPRequestMethod;
import es.uvigo.esei.dai.hybridserver.http.HTTPResponse;
import es.uvigo.esei.dai.hybridserver.http.HTTPResponseStatus;
import es.uvigo.esei.dai.hybridserver.http.HTTPParseException;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;


//le paso el socket que se creo entre el cliente y el servidor, y el listado de paginas almecenadas
class ClientHandler implements Runnable {
    private final Socket socket;
    private final PageRepository repository;

    ClientHandler(Socket socket, PageRepository repository) {
        this.socket = socket;
        this.repository = repository;
    }

    @Override
    public void run() {

        //con un try con recursos creo un input y output para leer lo que memanda el cliente y escribir la respuesta
        try (
                Reader in = new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8);
                Writer out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)) {
            // 1) Parsear la petición HTTP
            HTTPRequest req = new HTTPRequest(in);

            // 2) Solo GET permitido
            if (req.getMethod() != HTTPRequestMethod.GET) {
                writeError(out, HTTPResponseStatus.S405, "Method Not Allowed");
                return;
            }

            final String chain = req.getResourceChain(); // p.ej. "/", "/html?uuid=..."
            final String resourceName = req.getResourceName(); // p.ej. "html"

            // 3) Si ha mandado http://localhost:8888 devolvemos la pagina de bienvenida
            if (chain == null || chain.equals("/") || chain.isEmpty()) {
                HTTPResponse res = new HTTPResponse();
                res.setStatus(HTTPResponseStatus.S200);
                res.putParameter("Content-Type", "text/html; charset=UTF-8");
                res.putParameter("Connection", "close");
                res.setContent("<html><body><h1>Hybrid Server</h1><p>Bienvenido 👋</p></body></html>");
                res.print(out);
                out.flush();
                return;
            }

            // 4) Rutas que no sean "/html" → 404
            if (!"html".equals(resourceName)) {
                writeError(out, HTTPResponseStatus.S404, "Not Found");
                return;
            }

            // 5) Caso /html → con o sin uuid
            final Map<String, String> params = req.getResourceParameters();
            final String uuid = (params != null) ? params.get("uuid") : null; // si el uuid es distinto de null lo guardo

            // 5a) http://localhost:8888/html sin uuid → listar todas las páginas
            if (uuid == null || uuid.isEmpty()) {
                Map<String, String> all = repository.all();

                StringBuilder html = new StringBuilder()
                        .append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
                        .append("<title>Listado de páginas</title></head><body>")
                        .append("<h1>Páginas disponibles</h1>");

                if (all.isEmpty()) {
                    html.append("<p>No hay páginas almacenadas.</p>");
                } else {
                    html.append("<ul>");
                    for (Map.Entry<String, String> e : all.entrySet()) {
                        String id = e.getKey();
                        html.append("<li><a href=\"/html?uuid=")
                                .append(id)
                                .append("\">")
                                .append(id)
                                .append("</a></li>");
                    }
                    html.append("</ul>");
                }

                html.append("</body></html>");

                HTTPResponse res = new HTTPResponse();
                res.setStatus(HTTPResponseStatus.S200);
                res.putParameter("Content-Type", "text/html; charset=UTF-8");
                res.putParameter("Connection", "close");
                res.setContent(html.toString());
                res.print(out);
                out.flush();
                return;
            }

            // 5b) /html con uuid → servir la página si existe
            final String html = repository.get(uuid);
            if (html == null) {
                writeError(out, HTTPResponseStatus.S404, "Not Found");
                return;
            }

            HTTPResponse res = new HTTPResponse();
            res.setStatus(HTTPResponseStatus.S200);
            res.putParameter("Content-Type", "text/html; charset=UTF-8");
            res.putParameter("Connection", "close");
            res.setContent(html);
            res.print(out);
            out.flush();

        } catch (IOException | HTTPParseException e) {
            try (Writer out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)) {
                writeError(out, HTTPResponseStatus.S400, "Bad Request");
            } catch (IOException ignore) {
            }
        } finally {
            try {
                socket.close();
            } catch (IOException ignore) {
            }
        }
    }

    private static void writeError(Writer out, HTTPResponseStatus status, String message) throws IOException {
        HTTPResponse res = new HTTPResponse();
        res.setStatus(status);
        res.putParameter("Content-Type", "text/html; charset=UTF-8");
        res.putParameter("Connection", "close");

        // Puedes enviar un HTML sencillo explicando el error (opcional)
        String body = "<html><body><h1>" + status.getCode() + " " + status.getStatus() + "</h1><p>" +
                message + "</p></body></html>";
        res.setContent(body);

        res.print(out);
        out.flush();
    }
}
