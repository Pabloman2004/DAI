package es.uvigo.esei.dai.hybridserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HybridServer implements AutoCloseable {
  private static final int SERVICE_PORT = 8888;

  private Thread serverThread;
  private volatile boolean stop;
  private ExecutorService executor;

  // NUEVO: repositorio de páginas
  private final PageRepository repository;

  public HybridServer() {
    // por defecto, repo vacío
    this.repository = new PageRepository();
  }

  public HybridServer(Map<String, String> pages) {
    // Inicializar con la "BD" en memoria conteniendo pages
    this.repository = new PageRepository(pages);
  }

  public HybridServer(Properties properties) {
    // Puedes leer threads y/o precargar páginas si quieres
    // De momento, mantenlo simple:
    this.repository = new PageRepository();
  }

  public int getPort() {
    return SERVICE_PORT;
  }

  public void start() {
    int nThreads = Math.max(2, Runtime.getRuntime().availableProcessors());
    this.executor = Executors.newFixedThreadPool(nThreads);

    this.stop = false; //quitar linea
    this.serverThread = new Thread(() -> {
      try (final ServerSocket serverSocket = new ServerSocket(SERVICE_PORT)) {
        while (true) {
          final Socket socket = serverSocket.accept();
          if (stop) {
            try { socket.close(); } catch (IOException ignore) {}
            break;
          }
          // Pasamos también el repositorio al handler
          executor.submit(new ClientHandlerMap(socket, repository));//hay que gestionar el cierre del socket dentro del 
        
        }
      } catch (IOException e) {
        if (!stop) e.printStackTrace();
      }
    }, "HybridServer-Acceptor");

    this.serverThread.start();
  }

  @Override
  public void close() {
    this.stop = true;

    try (Socket socket = new Socket("localhost", SERVICE_PORT)) {
      // “Despierta” el accept
    } catch (IOException e) {
      // ok si no estaba arrancado
    }

    try {
      if (this.serverThread != null) this.serverThread.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      this.serverThread = null;
    }

    if (this.executor != null) {
      this.executor.shutdown();
      try {
        if (!this.executor.awaitTermination(10, TimeUnit.SECONDS)) {
          this.executor.shutdownNow();
        }
      } catch (InterruptedException e) {
        this.executor.shutdownNow();
        Thread.currentThread().interrupt();
      } finally {
        this.executor = null;
      }
    }
  }
}
