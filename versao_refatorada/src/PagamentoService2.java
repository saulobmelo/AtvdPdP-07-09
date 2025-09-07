import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class PagamentoService2 {

    private static final Map<Integer, String> PAGAMENTOS = new HashMap<>();

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8002), 0);

        // POST /pagamento/{pedidoId}
        server.createContext("/pagamento", exchange -> {
            try {
                String path = exchange.getRequestURI().getPath(); // /pagamento/101
                String[] parts = path.split("/");

                if (parts.length == 3 &&
                        ("POST".equalsIgnoreCase(exchange.getRequestMethod()) ||
                                "GET".equalsIgnoreCase(exchange.getRequestMethod()))) {

                    int pedidoId = Integer.parseInt(parts[2]);
                    PAGAMENTOS.put(pedidoId, "pago");
                    String json = "{\"pedidoId\":" + pedidoId + ",\"status\":\"pago\"}";

                    System.out.println("[PagamentoService] POST " + path);
                    System.out.println("[PagamentoService] Resposta: " + json);

                    respondJson(exchange, 200, json);
                } else {
                    respondJson(exchange, 404, "{\"erro\":\"Rota não encontrada\"}");
                }
            } catch (Exception e) {
                respondJson(exchange, 500, "{\"erro\":\"Falha no PagamentoService\"}");
            }
        });

        System.out.println("PagamentoService rodando na porta 8002");
        server.start();
    }

    private static void respondJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }
}