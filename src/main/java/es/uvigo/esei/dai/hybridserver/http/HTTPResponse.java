/**
 *  HybridServer
 *  Copyright (C) 2025 Miguel Reboiro-Jato
 *  
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package es.uvigo.esei.dai.hybridserver.http;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class HTTPResponse {

  private String version;
  private HTTPResponseStatus status;
  private Map<String,String> cabecera;
  private String contenido;

  public HTTPResponse() {
    this.version = "HTTP/1.1";
    this.status = HTTPResponseStatus.S200;
    this.cabecera = new HashMap<>();
    this.contenido = "";
  }

  public HTTPResponseStatus getStatus() {
    return this.status;
  }

  public void setStatus(HTTPResponseStatus status) {
    this.status = status;
  }

  public String getVersion() {
    return this.version;
  }

  public void setVersion(String version) {
     this.version = version;
  }

  public String getContent() {
    return this.contenido;
  }

// lo hago así para no tener que hacer "Content-Length" en el metodo printer
public void setContent(String content) {
    this.contenido = content != null ? content : "";
    this.cabecera.put("Content-Length", String.valueOf(this.contenido.length()));
}


  public Map<String, String> getParameters() {
    return this.cabecera;
  }

  public String putParameter(String name, String value) {
    return this.cabecera.put(name, value); //si ya existia un valor con esa cabecera lo devuelve si era null lo añade y ya
  }

  public boolean containsParameter(String name) {
    return this.cabecera.containsKey(name);
  }

  public String removeParameter(String name) {
    return this.cabecera.remove(name);
  }

  public void clearParameters() {
    this.cabecera.clear();
  }

public List<String> listParameters() {
    return new ArrayList<>(this.cabecera.keySet());
}


public void print(Writer writer) throws IOException {
    StringBuilder sb = new StringBuilder();

    // 1. Línea de estado
    sb.append(this.version)
      .append(" ")
      .append(this.status.getCode())
      .append(" ")
      .append(this.status.getStatus())
      .append("\r\n");

    // 2. Cabeceras
    for (Map.Entry<String, String> entry : this.cabecera.entrySet()) {
        sb.append(entry.getKey())
          .append(": ")
          .append(entry.getValue())
          .append("\r\n");
    }

    // 3. Línea en blanco para separar cabeceras y contenido
    sb.append("\r\n");

    // 4. Cuerpo (si lo hay)
    if (this.contenido != null && !this.contenido.isEmpty()) {
        sb.append(this.contenido);
    }

    // 5. Escribir en el Writer
    writer.write(sb.toString());
    writer.flush();
}


  @Override
  public String toString() {
    try (final StringWriter writer = new StringWriter()) {
      this.print(writer);

      return writer.toString();
    } catch (IOException e) {
      throw new RuntimeException("Unexpected I/O exception", e);
    }
  }
}
