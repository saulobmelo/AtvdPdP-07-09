import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class UsuarioService2 {

    private static final Map<Integer, String> USUARIOS = new HashMap<>();
    static {
        USUARIOS.put(1, "João");
        USUARIOS.put(2, "Maria");
    }

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);

        // GET /usuario/{id}
        server.createContext("/usuario", exchange -> {
            try {
                String path = exchange.getRequestURI().getPath(); // /usuario/1
                String[] parts = path.split("/");
                if (parts.length == 3 && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    int id = Integer.parseInt(parts[2]);
                    String nome = USUARIOS.get(id);
                    String body = (nome == null)
                            ? "{\"erro\":\"Usuário não encontrado\"}"
                            : "{\"id\":" + id + ",\"nome\":\"" + nome + "\"}";
                    respondJson(exchange, 200, body);
                } else {
                    respondJson(exchange, 404, "{\"erro\":\"Rota não encontrada\"}");
                }
            } catch (Exception e) {
                respondJson(exchange, 500, "{\"erro\":\"Falha no UsuarioService\"}");
            }
        });

        System.out.println("UsuarioService rodando na porta 8000");
        server.start();
    }

    private static void respondJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(data); }
    }
}