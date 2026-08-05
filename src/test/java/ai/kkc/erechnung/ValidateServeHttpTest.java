package ai.kkc.erechnung;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ValidateServeHttpTest {

  @Test
  void healthzAndValidateXml() throws Exception {
    int port = freePort();
    var serve =
        new ValidateServe("127.0.0.1", port, Duration.ofSeconds(20), XmlSizeLimit.DEFAULT_BYTES);
    var exec = Executors.newSingleThreadExecutor();
    Future<Integer> running =
        exec.submit(
            () -> {
              try {
                return serve.runBlocking();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
    try {
      waitHealthz(port);
      HttpClient client = HttpClient.newHttpClient();
      HttpResponse<String> health =
          client.send(
              HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/healthz"))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertEquals(200, health.statusCode());
      assertTrue(health.body().contains("ok"));

      String boundary = "----junitboundary";
      byte[] body =
          ValidateServe.buildMultipart(
              boundary, "plain.xml", "<hello/>".getBytes(StandardCharsets.UTF_8));
      HttpRequest req =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/validate"))
              .timeout(Duration.ofMinutes(2))
              .header("Content-Type", "multipart/form-data; boundary=" + boundary)
              .POST(HttpRequest.BodyPublishers.ofByteArray(body))
              .build();
      HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
      assertEquals(200, res.statusCode());
      assertTrue(res.body().contains("\"verdict\""));
      assertNotNull(res.body());
    } finally {
      serve.stopForTests();
      running.get(15, TimeUnit.SECONDS);
      exec.shutdownNow();
    }
  }

  private static void waitHealthz(int port) throws Exception {
    HttpClient client = HttpClient.newHttpClient();
    long deadline = System.currentTimeMillis() + 30_000;
    while (System.currentTimeMillis() < deadline) {
      try {
        HttpRequest req =
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/healthz"))
                .GET()
                .timeout(Duration.ofSeconds(2))
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200 && res.body().contains("ok")) {
          return;
        }
      } catch (Exception ignored) {
        // retry
      }
      Thread.sleep(100);
    }
    throw new IllegalStateException("serve did not become ready on port " + port);
  }

  private static int freePort() throws Exception {
    try (var s = new java.net.ServerSocket(0)) {
      return s.getLocalPort();
    }
  }
}
