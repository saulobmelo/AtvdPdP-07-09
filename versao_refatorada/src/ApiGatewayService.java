import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

public class ApiGatewayService {

    // ======= Dependências (IoC via construtor) =======
    private final HttpClient httpClient;
    private final CircuitBreaker usuarioCB;
    private final CircuitBreaker pedidoCB;
    private final CircuitBreaker pagamentoCB;

    private final BulkheadExecutor usuarioBulkhead;
    private final BulkheadExecutor pedidoBulkhead;
    private final BulkheadExecutor pagamentoBulkhead;

    public ApiGatewayService(
            HttpClient httpClient,
            CircuitBreaker usuarioCB,
            CircuitBreaker pedidoCB,
            CircuitBreaker pagamentoCB,
            BulkheadExecutor usuarioBulkhead,
            BulkheadExecutor pedidoBulkhead,
            BulkheadExecutor pagamentoBulkhead
    ) {
        this.httpClient = httpClient;
        this.usuarioCB = usuarioCB;
        this.pedidoCB = pedidoCB;
        this.pagamentoCB = pagamentoCB;
        this.usuarioBulkhead = usuarioBulkhead;
        this.pedidoBulkhead = pedidoBulkhead;
        this.pagamentoBulkhead = pagamentoBulkhead;
    }

    public static void main(String[] args) throws Exception {
        // Configs padrões (pode ajustar se quiser)
        HttpClient http = new HttpClient(2000, 2000); // timeouts (ms)

        // Circuit Breakers: 3 falhas seguidas abre, tenta meia-vida após 5s
        CircuitBreaker usuarioCB = new CircuitBreaker(3, 5000);
        CircuitBreaker pedidoCB = new CircuitBreaker(3, 5000);
        CircuitBreaker pagamentoCB = new CircuitBreaker(3, 5000);

        // Bulkheads: 5 threads, fila 20, timeout 3s pra adquirir
        BulkheadExecutor usuarioBH = new BulkheadExecutor("usuarioBH", 5, 20, 3000);
        BulkheadExecutor pedidoBH  = new BulkheadExecutor("pedidoBH",  5, 20, 3000);
        BulkheadExecutor pagtoBH   = new BulkheadExecutor("pagtoBH",   5, 20, 3000);

        ApiGatewayService gateway = new ApiGatewayService(http, usuarioCB, pedidoCB, pagamentoCB, usuarioBH, pedidoBH, pagtoBH);
        gateway.start(9000);
    }

    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Rota de saúde
        server.createContext("/api/health", exchange -> respondText(exchange, 200, "OK"));

        // /api/usuario/** -> http://localhost:8000/usuario/**
        server.createContext("/api/usuario", new ProxyHandler(
                "http://localhost:8000",
                usuarioCB,
                usuarioBulkhead,
                httpClient
        ));

        // /api/pedido/** -> http://localhost:8001/pedido/**
        server.createContext("/api/pedido", new ProxyHandler(
                "http://localhost:8001",
                pedidoCB,
                pedidoBulkhead,
                httpClient
        ));

        // /api/pagamento/** -> http://localhost:8002/pagamento/**
        server.createContext("/api/pagamento", new ProxyHandler(
                "http://localhost:8002",
                pagamentoCB,
                pagamentoBulkhead,
                httpClient
        ));

        System.out.println("API Gateway rodando na porta " + port);
        server.start();
    }

    private static void respondText(HttpExchange exchange, int status, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(data); }
    }

    // ========== ProxyHandler ==========
    static class ProxyHandler implements HttpHandler {
        private final String upstreamBase; // ex: http://localhost:8000
        private final CircuitBreaker circuitBreaker;
        private final BulkheadExecutor bulkhead;
        private final HttpClient httpClient;

        public ProxyHandler(String upstreamBase, CircuitBreaker cb, BulkheadExecutor bh, HttpClient client) {
            this.upstreamBase = upstreamBase;
            this.circuitBreaker = cb;
            this.bulkhead = bh;
            this.httpClient = client;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                URI uri = exchange.getRequestURI();
                String originalPath = uri.getPath(); // ex: /api/usuario/1
                // remove o prefixo /api -> /usuario/1
                String proxiedPath = originalPath.replaceFirst("^/api", "");

                String targetUrl = upstreamBase + proxiedPath + (uri.getQuery() != null ? "?" + uri.getQuery() : "");

                String method = exchange.getRequestMethod().toUpperCase();
                byte[] requestBody = exchange.getRequestBody().readAllBytes();

                Callable<HttpClient.Response> work = () -> circuitBreaker.call(() ->
                        httpClient.forward(method, targetUrl, requestBody, exchange.getRequestHeaders())
                );

                HttpClient.Response upstreamResp = bulkhead.submit(work);

                // Repasse status, headers e corpo
                exchange.getResponseHeaders().putAll(upstreamResp.headers);
                exchange.sendResponseHeaders(upstreamResp.status, upstreamResp.body.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(upstreamResp.body); }

            } catch (CircuitBreaker.OpenCircuitException oce) {
                String msg = "{\"erro\":\"Circuit Breaker aberto para este serviço\"}";
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(503, msg.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(msg.getBytes(StandardCharsets.UTF_8)); }
            } catch (BulkheadExecutor.BulkheadFullException bfe) {
                String msg = "{\"erro\":\"Bulkhead saturado (limite de concorrência/fila atingido)\"}";
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(429, msg.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(msg.getBytes(StandardCharsets.UTF_8)); }
            } catch (Exception e) {
                String msg = "{\"erro\":\"Falha no gateway: " + e.getClass().getSimpleName() + "\"}";
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(502, msg.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(msg.getBytes(StandardCharsets.UTF_8)); }
            }
        }
    }
}