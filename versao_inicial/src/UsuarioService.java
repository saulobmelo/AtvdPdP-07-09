import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class UsuarioService {

    private static Map<Integer, String> usuarios = new HashMap<>();

    static {
        usuarios.put(1, "João");
        usuarios.put(2, "Maria");
    }

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);

        server.createContext("/usuario", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String path = exchange.getRequestURI().getPath();
                String[] parts = path.split("/");

                if (parts.length == 3) { // /usuario/{id}
                    int id = Integer.parseInt(parts[2]);
                    String usuario = usuarios.get(id);

                    String resposta = (usuario != null)
                            ? "{ \"id\": " + id + ", \"nome\": \"" + usuario + "\" }"
                            : "{ \"erro\": \"Usuário não encontrado\" }";

                    exchange.sendResponseHeaders(200, resposta.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(resposta.getBytes());
                    os.close();

                } else if (parts.length == 4 && "pedidos".equals(parts[3])) {
                    // Má prática: chamada direta ao PedidoService
                    int id = Integer.parseInt(parts[2]);
                    URL url = new URL("http://localhost:8001/pedido/usuario/" + id);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");

                    byte[] data = conn.getInputStream().readAllBytes();
                    String resposta = new String(data);

                    exchange.sendResponseHeaders(200, resposta.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(resposta.getBytes());
                    os.close();
                }
            }
        });

        System.out.println("UsuarioService rodando na porta 8000");
        server.start();
    }
}