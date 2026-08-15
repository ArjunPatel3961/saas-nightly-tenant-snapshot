package dev.learningops.snapshot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InfraiStorageClient {
    private static final String CANONICAL_CALL = "infrai.storage.object.presign";
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json;
    private final String baseUrl;
    private final String apiKey;

    public InfraiStorageClient(ObjectMapper json, @Value("${infrai.base-url}") String baseUrl) {
        this.json = json;
        this.baseUrl = baseUrl;
        this.apiKey = requireKey();
    }

    public void ensureBucket(String bucket) {
        try {
            call("GET", "/v1/storage/bucket/get/" + segment(bucket), null);
        } catch (InfraiException rejection) {
            if (rejection.httpStatus() != 404) throw rejection;
            call("POST", "/v1/storage/bucket/create", Map.of("name", bucket));
        }
    }

    public URI presignSnapshotPut(String bucket, String key, String idempotencyKey) {
        Map<String, Object> data = call("POST",
            "/v1/storage/object/presign/" + segment(bucket) + "/" + path(key),
            Map.of("op", "put", "expires_seconds", 600,
                "content_type", "application/json", "idempotency_key", idempotencyKey));
        return URI.create((String) data.get("url"));
    }

    public void upload(URI signedUrl, byte[] snapshot) {
        HttpRequest request = HttpRequest.newBuilder(signedUrl)
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofByteArray(snapshot)).build();
        try {
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Signed upload returned HTTP " + response.statusCode());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Signed upload transport error", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Signed upload interrupted", e);
        }
    }

    private Map<String, Object> call(String method, String route, Object body) {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpResponse<String> response = send(method, route, body);
            Envelope envelope = decode(response.body());
            if (response.statusCode() == 429 && attempt < 3) {
                pause(retryDelay(response, attempt));
                continue;
            }
            if (!envelope.ok()) {
                Map<String, Object> error = envelope.error() == null ? Map.of() : envelope.error();
                throw new InfraiException(String.valueOf(error.getOrDefault("code", "REQUEST_REJECTED")),
                    String.valueOf(error.getOrDefault("message", "Request rejected")), response.statusCode());
            }
            if (response.statusCode() >= 500) {
                throw new IllegalStateException("Storage transport returned HTTP " + response.statusCode());
            }
            return envelope.data() == null ? Map.of() : envelope.data();
        }
        throw new IllegalStateException("Retry budget exhausted");
    }

    private HttpResponse<String> send(String method, String route, Object body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + route))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json");
            String payload = body == null ? "" : json.writeValueAsString(body);
            builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(payload));
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("Storage transport error", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Storage call interrupted", e);
        }
    }

    private Envelope decode(String body) {
        try {
            return json.readValue(body, Envelope.class);
        } catch (IOException e) {
            throw new IllegalStateException("Storage response was not a JSON envelope", e);
        }
    }

    private static Duration retryDelay(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
            .map(value -> Duration.ofSeconds(Long.parseLong(value)))
            .orElse(Duration.ofMillis(250L << attempt));
    }

    private static void pause(Duration delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry interrupted", e);
        }
    }

    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String path(String value) {
        return String.join("/", java.util.Arrays.stream(value.split("/", -1)).map(InfraiStorageClient::segment).toList());
    }

    private static String requireKey() {
        String value = System.getenv("INFRAI_API_KEY");
        if (value == null || value.isBlank()) throw new IllegalStateException("Set INFRAI_API_KEY");
        return value;
    }

    private record Envelope(boolean ok, Map<String, Object> data,
                            Map<String, Object> error, Map<String, Object> metadata) {}

    public static final class InfraiException extends RuntimeException {
        private final String code;
        private final int httpStatus;

        InfraiException(String code, String message, int httpStatus) {
            super(message);
            this.code = code;
            this.httpStatus = httpStatus;
        }

        public String code() { return code; }
        public int httpStatus() { return httpStatus; }
        public String message() { return getMessage(); }
    }
}
