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
  private int SERVICE_PORT = 8888;
  private int numClients;
  private Thread serverThread;
  private volatile boolean stop;
  private ExecutorService executor;

  private final HtmlRepository repository;

  public HybridServer() {
    this.repository = new DBdaw("jdbc:mysql://localhost:3306/hstestdb","hsdb","hsdbpass"); 
    this.numClients = 50;
  }

  public HybridServer(Map<String,String> pages) {
    this.repository = new MAPdaw(pages); 
  }

public HybridServer(Properties properties) {
    
    int port = 8888; // valor por defecto
    String portStr = properties.getProperty("port");
    if (portStr != null) {
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException ignore) {
            System.err.println("[WARN] Valor inválido para 'port', usando 8888 por defecto");
        }
    }
    this.SERVICE_PORT = port;

    // 🔹 Número de clientes simultáneos (tamaño del pool de threads)
    int threads = 10; // valor por defecto razonable
    String numClientsStr = properties.getProperty("numClients");
    if (numClientsStr != null) {
        try {
            threads = Integer.parseInt(numClientsStr);
        } catch (NumberFormatException ignore) {
            System.err.println("[WARN] Valor inválido para 'numClients', usando 10 por defecto");
        }
    }

    // Seguridad: nunca permitir 0 o negativo, o el Executor lanzará IllegalArgumentException
    if (threads <= 0) {
        System.err.println("[WARN] 'numClients' menor o igual que 0, ajustando a 2");
        threads = 2;
    }

    this.numClients = threads;

    // 🔹 Credenciales de base de datos
    String url  = properties.getProperty("db.url");
    String user = properties.getProperty("db.user");
    String pass = properties.getProperty("db.password");

    // 🔹 Crea el repositorio basado en base de datos
    this.repository = new DBdaw(url, user, pass);

    System.out.printf("[INFO] Servidor configurado en puerto %d con pool de %d hilos%n", SERVICE_PORT, numClients);
}




  public int getPort() { return SERVICE_PORT; }

  public void start() {
    
    this.executor = Executors.newFixedThreadPool(numClients);

    this.stop = false;
    this.serverThread = new Thread(() -> {
      try (final ServerSocket serverSocket = new ServerSocket(SERVICE_PORT)) {
        while (true) {
          final Socket socket = serverSocket.accept();
          if (stop) { try { socket.close(); } catch (IOException ignore) {} break; }
          executor.submit(new ClientHandler(socket, repository)); 
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
  try (Socket ignored = new Socket("localhost", SERVICE_PORT)) { } catch (IOException ignore) {}

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

