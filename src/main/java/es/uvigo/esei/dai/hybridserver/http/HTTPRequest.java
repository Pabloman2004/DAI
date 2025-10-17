package es.uvigo.esei.dai.hybridserver.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Clase que representa una petición HTTP.
 * Se encarga de parsear la request line, cabeceras y contenido (funciona para GET y POST).
 */
public class HTTPRequest {

    private HTTPRequestMethod method;
    private String httpVersion;
    private String resourceChain; // ej. "/htm?uuid=..."
    private Map<String, String> headerParameters;
    private String content;
    private Map<String, String> resourceParameters;
    private int contentLength;
    private final BufferedReader read;

    public HTTPRequest(Reader reader) throws IOException, HTTPParseException {
        this.read = (reader instanceof BufferedReader)
            ? (BufferedReader) reader
            : new BufferedReader(reader);

        // ---- Primera línea: METHOD resource HTTP/version ----
        String r1 = read.readLine();
        if (r1 == null || r1.isEmpty())
            throw new HTTPParseException("Missing request line");

        String[] parts = r1.split("\\s+");
        if (parts.length != 3)
            throw new HTTPParseException("Malformed request line: " + r1);

        // Método
        try {
            this.method = HTTPRequestMethod.valueOf(parts[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new HTTPParseException("Unsupported method: " + parts[0], e);
        }

        // Versión HTTP
        this.httpVersion = parts[2];
        if (!this.httpVersion.startsWith("HTTP/")) {
            throw new HTTPParseException("Invalid HTTP version: " + this.httpVersion);
        }

        // Recurso y query
        String recursoYquery = parts[1];
        this.resourceChain = recursoYquery;
        this.resourceParameters = new HashMap<>();

        String[] recursoSplit = recursoYquery.split("\\?", 2);
        String query = (recursoSplit.length > 1) ? recursoSplit[1] : null;
        if (query != null && !query.isEmpty()) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] kv = pair.split("=", 2);
                String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                String value = (kv.length > 1)
                    ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8)
                    : "";
                this.resourceParameters.put(key, value);
            }
        }

        // ---- Cabeceras ----
        this.headerParameters = new LinkedHashMap<>();
        String line;
        while ((line = read.readLine()) != null && !line.isEmpty()) {
            int idx = line.indexOf(':');
            if (idx <= 0) {
                // línea inválida sin ':'
                throw new HTTPParseException("Invalid header line: " + line);
            }

            String name = line.substring(0, idx).trim();
            String value = line.substring(idx + 1).trim();

            // Host: eliminar puerto si lo tiene
            if (name.equalsIgnoreCase("Host")) {
                int colon = value.indexOf(':');
                if (colon >= 0) value = value.substring(0, colon).trim();
            }

            this.headerParameters.put(name, value);
        }

        // ---- Content-Length ----
        String lenStr = headerParameters.get("Content-Length");
        if (lenStr != null) {
            try {
                this.contentLength = Integer.parseInt(lenStr);
            } catch (NumberFormatException e) {
                throw new HTTPParseException("Invalid Content-Length: " + lenStr, e);
            }
        }

        // ---- Body (solo si hay Content-Length) ----
        if (this.contentLength > 0) {
            char[] buf = new char[this.contentLength];
            int off = 0;
            while (off < this.contentLength) {
                int n = read.read(buf, off, contentLength - off);
                if (n == -1)
                    throw new IOException("Unexpected EOF while reading body");
                off += n;
            }
            this.content = new String(buf, 0, contentLength);
            this.content = URLDecoder.decode(content, StandardCharsets.UTF_8);
        } else {
            this.content = null;
        }
    }

    // ========================= Getters =========================

    public HTTPRequestMethod getMethod() {
        return this.method;
    }

    public String getResourceChain() {
        return this.resourceChain;
    }

    public String getHttpVersion() {
        return this.httpVersion;
    }

    public Map<String, String> getHeaderParameters() {
        return this.headerParameters;
    }

    public String getContent() {
        return this.content;
    }

    public int getContentLength() {
        return this.contentLength;
    }

    public String[] getResourcePath() {
        if (this.resourceChain == null || this.resourceChain.isEmpty()) {
            return new String[0];
        }

        String trimmed = this.resourceChain.startsWith("/")
            ? this.resourceChain.substring(1)
            : this.resourceChain;

        int qmark = trimmed.indexOf('?');
        if (qmark >= 0) {
            trimmed = trimmed.substring(0, qmark);
        }

        if (trimmed.isEmpty())
            return new String[0];

        String[] raw = trimmed.split("/");
        List<String> parts = new ArrayList<>(raw.length);
        for (String s : raw)
            if (!s.isEmpty())
                parts.add(s);

        return parts.toArray(new String[0]);
    }

    public String getResourceName() {
        if (this.resourceChain == null || this.resourceChain.isEmpty()) {
            return "";
        }

        String trimmed = this.resourceChain.startsWith("/")
            ? this.resourceChain.substring(1)
            : this.resourceChain;

        int qmark = trimmed.indexOf('?');
        if (qmark >= 0) {
            trimmed = trimmed.substring(0, qmark);
        }

        return trimmed;
    }

    public Map<String, String> getResourceParameters() {
        if (this.content != null && !this.content.isEmpty()) {
            String body = this.content.trim();
            String[] pairs = body.split("&");
            for (String pair : pairs) {
                if (pair.isEmpty()) continue;
                String[] kv = pair.split("=", 2);
                String key = kv[0];
                String val = kv.length > 1 ? kv[1] : "";
                this.resourceParameters.put(key, val);
            }
        }
        return this.resourceParameters;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(this.getMethod().name())
            .append(' ')
            .append(this.getResourceChain())
            .append(' ')
            .append(this.getHttpVersion())
            .append("\r\n");

        for (Map.Entry<String, String> entry : this.getHeaderParameters().entrySet()) {
            sb.append(entry.getKey())
                .append(": ")
                .append(entry.getValue())
                .append("\r\n");
        }

        if (this.getContentLength() > 0) {
            sb.append("\r\n").append(this.getContent());
        }

        return sb.toString();
    }
}
