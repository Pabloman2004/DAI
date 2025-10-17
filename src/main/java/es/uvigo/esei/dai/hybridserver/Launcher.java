package es.uvigo.esei.dai.hybridserver;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class Launcher {
    public static void main(String[] args) {
        Properties config = new Properties();

        // === Cargar fichero de configuración (si se proporciona) ===
        if (args.length == 1) {
            String configFile = args[0];
            try (FileInputStream fis = new FileInputStream(configFile)) {
                config.load(fis);
                System.out.println("Configuración cargada desde " + configFile);
            } catch (IOException e) {
                System.err.println("❌ Error al leer el fichero de configuración: " + configFile);
                e.printStackTrace();
                System.exit(1);
            }
        }
        else{
            System.out.println("No hay fichero");
        }

        // === Crear un mapa con páginas (por si queremos usar modo Map) ===
        Map<String, String> samplePages = new HashMap<>();
        samplePages.put("demo-uuid", "<html><body><h1>Página de ejemplo</h1><p>Contenido generado en memoria</p></body></html>");
        samplePages.put("abc123", "<html><body><h1>Hola desde MAP</h1><p>Servidor en modo memoria</p></body></html>");

        // === Inicializar servidor ===
        // Puedes cambiar fácilmente entre:
        //   new HybridServer(config) → modo BD
        //   new HybridServer(samplePages) → modo mapa
        try (HybridServer server = new HybridServer(samplePages)) {
            // try (HybridServer server = new HybridServer(samplePages)) { // ← modo memoria
            server.start();
            System.out.println("Servidor escuchando en http://localhost:" + server.getPort());
            System.out.println("Pulsa ENTER para parar...");
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
