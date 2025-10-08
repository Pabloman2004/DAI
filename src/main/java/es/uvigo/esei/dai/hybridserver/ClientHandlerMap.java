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
class ClientHandlerMap implements Runnable {
    private final Socket socket;
    private final PageRepository repository;

    ClientHandlerMap(Socket socket, PageRepository repository) {
        this.socket = socket;
        this.repository = repository;
    }

    @Override
    public void run() {
        try (
                Reader in = new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8);
                Writer out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)) {
            // parseamos la peticion
            HTTPRequest req = new HTTPRequest(in);

            // según la petición mostraremos, eliminaremos o añadiremos
            switch (req.getMethod()) {
                case GET:
                    handleGet(req, out);
                    break;
                case POST:
                    handlePost(req, out);
                    break;
                case DELETE:
                    handleDelete(req, out);
                    break;
                default:
                    writeError(out, HTTPResponseStatus.S405, "Method Not Allowed");
                    break;
            }

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

    private void handleGet(HTTPRequest req, Writer out) throws IOException {
        final String chain = req.getResourceChain();
        final String resourceName = req.getResourceName();

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

        if (!"html".equals(resourceName)) {
            writeError(out, HTTPResponseStatus.S404, "Not Found");
            return;
        }

        final Map<String, String> params = req.getResourceParameters();
        final String uuid = (params != null) ? params.get("uuid") : null;

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

        // /html con uuid
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
    }

    public void handlePost(HTTPRequest req, Writer out) throws IOException {
        Map<String, String> params = req.getResourceParameters();
        String uuid = (params != null) ? params.get("uuid") : null;
        String content = (params != null) ? params.get("content") : null;

        if (uuid == null || uuid.isEmpty()) {
            writeError(out, HTTPResponseStatus.S400, "Falta el parámetro uuid");
            return;
        }
        boolean exists = repository.get(uuid) != null ;
        repository.put(uuid, content);
        

        HTTPResponse res = new HTTPResponse();
        res.setStatus(exists ? HTTPResponseStatus.S200: HTTPResponseStatus.S201);
        res.putParameter("Content-Type", "text/html; charset=UTF-8");
        res.putParameter("Connection", "close");

        StringBuilder html = new StringBuilder()
                .append("<html><body>")
                .append("<h1>POST recibido correctamente</h1>")
                .append("<p>Página ")
                .append(exists ? "actualizada" : "creada")
                .append(" con UUID: ").append(uuid).append("</p>")
                .append("</body></html>");

        res.setContent(html.toString());
        res.print(out);
        out.flush();

    }

    public void handleDelete(HTTPRequest req, Writer out) throws IOException {

        Map<String, String> params = req.getResourceParameters();
        String uuid = (params != null) ? params.get("uuid") : null;

        if (uuid == null || uuid.isEmpty()) {
            writeError(out, HTTPResponseStatus.S400, "Falta el parámetro uuid");
            return;
        }

        String removed = repository.remove(uuid);
        if (removed == null) {
            writeError(out, HTTPResponseStatus.S404, "Página no encontrada");
            return;
        }

        HTTPResponse res = new HTTPResponse();
        res.setStatus(HTTPResponseStatus.S200);
        res.putParameter("Content-Type", "text/html; charset=UTF-8");
        res.putParameter("Connection", "close");
        res.setContent("<html><body><h1>DELETE recibido correctamente</h1></body></html>");
        res.print(out);
        out.flush();
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
