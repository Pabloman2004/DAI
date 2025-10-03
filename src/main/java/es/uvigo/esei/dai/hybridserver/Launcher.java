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
package es.uvigo.esei.dai.hybridserver;

import java.io.IOException;
import java.util.Map;

public class Launcher {
  public static void main(String[] args) {
        Map<String, String> seed = Map.of(
      "ls", "<html><body><h1>Página 1234</h1></body></html>",
      "abcd", "<html><body><h1>Página abcd</h1></body></html>"
    );
      try (HybridServer server = new HybridServer(seed)) {
      server.start();
      System.out.println("Servidor escuchando en http://localhost:" + server.getPort());
      System.out.println("Pulsa ENTER para parar...");
      System.in.read(); // bloquea hasta ENTER
    }
    catch(IOException e){
      e.printStackTrace();
      
    }
    
  }
}
