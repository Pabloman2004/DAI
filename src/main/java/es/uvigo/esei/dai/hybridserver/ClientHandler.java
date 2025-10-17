package es.uvigo.esei.dai.hybridserver;

import es.uvigo.esei.dai.hybridserver.http.HTTPRequest;
import es.uvigo.esei.dai.hybridserver.http.HTTPResponse;
import es.uvigo.esei.dai.hybridserver.http.HTTPResponseStatus;
import es.uvigo.esei.dai.hybridserver.http.HTTPParseException;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;


// Le paso el socket que se creó entre el cliente y el servidor y el listado de páginas almacenadas
class ClientHandler implements Runnable {
    private final Socket socket;
    private final HtmlRepository repo;

    public ClientHandler(Socket socket, HtmlRepository repo) {
        this.socket = socket;
        this.repo = repo;
    }

    @Override
    public void run() {
        try (
                Reader in = new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8);
                Writer out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)) {
            // Parseamos la petición
            HTTPRequest req = new HTTPRequest(in);

            // Según la petición mostraremos, eliminaremos o añadiremos
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

        // mapStorage es tu instancia de MapStorage (o MAPdaw)
        String httpResponse = repo.getPage(req); // ← respuesta HTTP COMPLETA
        out.write(httpResponse);
        out.flush();

    }

    private void handlePost(HTTPRequest req, Writer out) throws IOException {
        String httpResponse = repo.postPage(req);
        out.write(httpResponse);
        out.flush();
    }


    private void handleDelete(HTTPRequest req, Writer out) throws IOException {
        String httpResponse = repo.deletePage(req);
        out.write(httpResponse);
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
