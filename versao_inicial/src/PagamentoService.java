import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.*;

public class PagamentoService {

    private static Map<Integer, String> pagamentos = new HashMap<>();

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8002), 0);

        server.createContext("/pagamento", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String path = exchange.getRequestURI().getPath();
                String[] parts = path.split("/");

                if (parts.length == 3 && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    int pedidoId = Integer.parseInt(parts[2]);
                    pagamentos.put(pedidoId, "pago");

                    String resposta = "{ \"pedidoId\": " + pedidoId + ", \"status\": \"pago\" }";

                    exchange.sendResponseHeaders(200, resposta.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(resposta.getBytes());
                    os.close();
                }
            }
        });

        System.out.println("PagamentoService rodando na porta 8002");
        server.start();
    }
}