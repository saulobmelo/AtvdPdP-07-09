package utils;

import com.sun.net.httpserver.Headers;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class HttpClient {

    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public HttpClient() {
        this(2000, 2000);
    }

    public HttpClient(int connectTimeoutMs, int readTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    public static class Response {
        public final int status;
        public final byte[] body;
        public final Map<String, List<String>> headers;

        public Response(int status, byte[] body, Map<String, List<String>> headers) {
            this.status = status;
            this.body = body;
            this.headers = headers;
        }
    }

    public Response forward(String method, String targetUrl, byte[] body, Headers inboundHeaders) throws Exception {
        URL url = new URL(targetUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);
        conn.setRequestMethod(method);

        if (inboundHeaders != null) {
            for (Map.Entry<String, List<String>> e : inboundHeaders.entrySet()) {
                String name = e.getKey();
                if (name == null) continue;
                if (name.equalsIgnoreCase("Host") ||
                        name.equalsIgnoreCase("Content-Length") ||
                        name.equalsIgnoreCase("Connection") ||
                        name.equalsIgnoreCase("Transfer-Encoding")) {
                    continue;
                }
                for (String v : e.getValue()) {
                    conn.addRequestProperty(name, v);
                }
            }
        }

        if (!method.equals("GET") && body != null && body.length > 0) {
            conn.setDoOutput(true);
            conn.getOutputStream().write(body);
        }

        int status = conn.getResponseCode();
        InputStream is = (status >= 200 && status < 400) ? conn.getInputStream() : conn.getErrorStream();
        byte[] resp = readAllBytesSafe(is);

        Map<String, List<String>> outHeaders = new HashMap<>();
        for (Map.Entry<String, List<String>> e : conn.getHeaderFields().entrySet()) {
            String key = e.getKey();
            if (key == null) continue;
            outHeaders.put(key, new ArrayList<>(e.getValue()));
        }
        outHeaders.putIfAbsent("Content-Type", List.of("application/json; charset=utf-8"));

        return new Response(status, resp, outHeaders);
    }

    private static byte[] readAllBytesSafe(InputStream is) throws Exception {
        if (is == null) return "{}".getBytes(StandardCharsets.UTF_8);
        try (InputStream in = is; ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int r;
            while ((r = in.read(buf)) != -1) baos.write(buf, 0, r);
            return baos.toByteArray();
        }
    }
}