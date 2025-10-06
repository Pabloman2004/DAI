package es.uvigo.esei.dai.hybridserver.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
/* En esta clase tengo que recoger los campos de una consulta http (Funciona completamente para GET)*/
import java.util.Map;

/*
 * No funciona bien lo de leer el contenido de un method POST
 */

/* En el constructor recogo esta informacion y creo un getter de este String para que pueda
 * ser accedido fuera de esta clase
 */
public class HTTPRequest {

  private HTTPRequestMethod method;
  private String httpVersion;
  private String resourceChain; // es todo el link ejemplo //
                                // /hello/world.html?country=Spain&province=Ourense&city=Ourense
  public Map<String, String> headerParameters;
  private String content;
  private Map<String, String> resourceParameters; // Si recibimos mas de un parametro tendre que cambiarlo
  private int contentLength;
  private final BufferedReader read;

  public HTTPRequest(Reader reader) throws IOException, HTTPParseException {
    this.read = (reader instanceof BufferedReader)
        ? (BufferedReader) reader
        : new BufferedReader(reader);
    // ----Primera linea method resourceChain httpVersion -------
    String r1 = read.readLine();
    // en esta linea lee algo del estilo GET
    // /htm?uuid=12345678-1234-1234-1234-123456789012 HTTP/1.1

    if (r1 == null || r1.isEmpty())
      throw new IOException("Empty requested line");

    String[] parts = r1.split(" ");
    if (parts.length < 3)
      throw new IOException("Malformed request line: " + r1);

    // comprobar si es un method de los que manejo
    try {
      this.method = HTTPRequestMethod.valueOf(parts[0].toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new HTTPParseException("Unsupported method: " + parts[0], e);
    }

    String recursoYquery = parts[1];
    String[] recursoSplit = recursoYquery.split("\\?", 2); // máximo 2 trozos
    this.resourceChain = recursoYquery; // "/htm"
    this.resourceParameters = new HashMap<>();

    String query = (recursoSplit.length > 1) ? recursoSplit[1] : null;
    if (query != null && !query.isEmpty()) {
      String[] pairs = query.split("&");
      for (String pair : pairs) {
        String[] kv = pair.split("=", 2);
        String key = kv[0];
        String value = (kv.length > 1) ? kv[1] : "";
        this.resourceParameters.put(key, value);
      }
    }

    this.httpVersion = parts[2];

    // ------ Cabecera -------
    this.headerParameters = new LinkedHashMap<>();

    String line;
    while ((line = read.readLine()) != null && !line.isEmpty()) {
      int idx = line.indexOf(':');
      if (idx > 0) {
        // Mantener la capitalización tal y como viene (sin toLowerCase)
        String name = line.substring(0, idx).trim();
        String value = line.substring(idx + 1).trim();

        // Si es Host, quitar el puerto
        if (name.equalsIgnoreCase("Host")) {
          int colon = value.indexOf(':');
          if (colon >= 0) {
            value = value.substring(0, colon).trim();
          }
        }

        this.headerParameters.put(name, value);
      }
    }

    // en caso de ser POST va a haber un parametro de la cabecera que son el numero
    // de bytes que debo leer
    String lenStr = headerParameters.get("Content-Length");
    if (lenStr != null) {
      try {
        this.contentLength = Integer.parseInt(lenStr);
      } catch (NumberFormatException e) {
        // el valor de Content-Length no es un número válido
        throw new HTTPParseException("Invalid Content-Length: " + lenStr, e);
      }
    }

    // ----- Content ---- no tocar mientras el profe no responde

    // Debe haberse leído ya la línea en blanco tras las cabeceras
    if (this.contentLength > 0) {
      char[] buf = new char[this.contentLength];
      int off = 0;
      while (off < this.contentLength) {
        int n = read.read(buf, off, contentLength - off);
        if (n == -1) {
          throw new HTTPParseException("Unexpected EOF while reading body");
        }
        off += n;
      }
      this.content = new String(buf, 0, contentLength);
      this.content = URLDecoder.decode(content, StandardCharsets.UTF_8.name());
    }else{
      this.content = null;
    }


  }

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

    // Quita "/" inicial si existe
    String trimmed = this.resourceChain.startsWith("/")
        ? this.resourceChain.substring(1)
        : this.resourceChain;

    // Quita la query string
    int qmark = trimmed.indexOf('?');
    if (qmark >= 0) {
      trimmed = trimmed.substring(0, qmark);
    }

    if (trimmed.isEmpty())
      return new String[0];

    // Divide en segmentos y elimina vacíos (por si hay dobles // o trailing /)
    String[] raw = trimmed.split("/");
    java.util.List<String> parts = new java.util.ArrayList<>(raw.length);
    for (String s : raw)
      if (!s.isEmpty())
        parts.add(s);

    return parts.toArray(new String[0]);
  }

  // sin hacer
  public String getResourceName() {
    if (this.resourceChain == null || this.resourceChain.isEmpty()) {
      return "";
    }

    // quitar "/" inicial si existe
    String trimmed = this.resourceChain.startsWith("/")
        ? this.resourceChain.substring(1)
        : this.resourceChain;

    // quitar query string si existe
    int qmark = trimmed.indexOf('?');
    if (qmark >= 0) {
      trimmed = trimmed.substring(0, qmark);
    }

    return trimmed;
  }

  public Map<String, String> getResourceParameters() {
    // en el caso de ser post los parametros estan en el cuerpo
    if (this.content != null && !this.content.isEmpty()) {
      // por si arrastra CR/LF finales del body
      String body = this.content.trim();
      String[] pairs = body.split("&");
      for (String pair : pairs) {
        if (pair.isEmpty())
          continue;
        String[] kv = pair.split("=", 2);
        String key = kv[0];
        String val = kv.length > 1 ? kv[1] : "";
        this.resourceParameters.put(key, val); // si hay claves repetidas, prevalece el body
      }
    }
    return this.resourceParameters;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();

    // Request line: METHOD resourceChain HTTP/version
    sb.append(this.getMethod().name())
        .append(' ')
        .append(this.getResourceChain())
        .append(' ')
        .append(this.getHttpVersion())
        .append("\r\n");

    // Headers
    for (Map.Entry<String, String> entry : this.getHeaderParameters().entrySet()) {
      sb.append(entry.getKey())
          .append(": ")
          .append(entry.getValue())
          .append("\r\n");
    }

    // Separador de cabeceras y cuerpo
    if (this.getContentLength() > 0) {
      sb.append("\r\n").append(this.getContent());
    }

    return sb.toString();
  }

}
