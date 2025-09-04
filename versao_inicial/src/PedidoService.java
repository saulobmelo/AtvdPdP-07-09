import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.*;

public class PedidoService {

    private static Map<Integer, List<String>> pedidos = new HashMap<>();

    static {
        pedidos.put(1, List.of("Livro Java - R$50"));
        pedidos.put(2, List.of("Notebook - R$3000"));
    }

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8001), 0);

        server.createContext("/pedido", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String path = exchange.getRequestURI().getPath();
                String[] parts = path.split("/");

                if (parts.length == 4 && "usuario".equals(parts[2])) {
                    int userId = Integer.parseInt(parts[3]);
                    List<String> lista = pedidos.getOrDefault(userId, List.of());

                    String resposta = lista.toString();

                    exchange.sendResponseHeaders(200, resposta.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(resposta.getBytes());
                    os.close();
                } else if (parts.length == 3) {
                    int pedidoId = Integer.parseInt(parts[2]);
                    // Má prática: chamada direta ao PagamentoService
                    URL url = new URL("http://localhost:8002/pagamento/" + pedidoId);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");

                    byte[] data = conn.getInputStream().readAllBytes();
                    String resposta = new String(data);

                    exchange.sendResponseHeaders(200, resposta.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(resposta.getBytes());
                    os.close();
                }
            }
        });

        System.out.println("PedidoService rodando na porta 8001");
        server.start();
    }
}