import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class PedidoService2 {

    private static final Map<Integer, List<Map<String, Object>>> PEDIDOS = new HashMap<>();
    static {
        PEDIDOS.put(1, List.of(
                Map.of("id", 101, "descricao", "Livro Java", "valor", 50)
        ));
        PEDIDOS.put(2, List.of(
                Map.of("id", 102, "descricao", "Notebook", "valor", 3000)
        ));
    }

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8001), 0);

        // GET /pedido/usuario/{userId}
        // POST /pedido/{pedidoId}/pagar  -> (apenas retorna um payload informativo; quem chama o Pagamento agora é o Gateway)
        server.createContext("/pedido", exchange -> {
            try {
                String path = exchange.getRequestURI().getPath();
                String[] parts = path.split("/");

                if (parts.length == 4 && "usuario".equals(parts[2]) && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    int userId = Integer.parseInt(parts[3]);
                    List<Map<String, Object>> lista = PEDIDOS.getOrDefault(userId, List.of());
                    String json = toJsonArray(lista);
                    respondJson(exchange, 200, json);

                } else if (parts.length == 3 && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    int pedidoId = Integer.parseInt(parts[2]);
                    String json = "{\"pedidoId\":" + pedidoId + ",\"acao\":\"pagar\",\"status\":\"solicitado\"}";
                    respondJson(exchange, 200, json);

                } else {
                    respondJson(exchange, 404, "{\"erro\":\"Rota não encontrada\"}");
                }
            } catch (Exception e) {
                respondJson(exchange, 500, "{\"erro\":\"Falha no PedidoService\"}");
            }
        });

        System.out.println("PedidoService rodando na porta 8001");
        server.start();
    }

    private static void respondJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(data); }
    }

    private static String toJsonArray(List<Map<String, Object>> lista) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < lista.size(); i++) {
            Map<String, Object> p = lista.get(i);
            sb.append("{\"id\":").append(p.get("id"))
                    .append(",\"descricao\":\"").append(p.get("descricao"))
                    .append("\",\"valor\":").append(p.get("valor")).append("}");
            if (i < lista.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}