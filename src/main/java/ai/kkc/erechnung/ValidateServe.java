package ai.kkc.erechnung;

import ai.kkc.erechnung.engine.ValidationOrchestrator;
import ai.kkc.erechnung.json.ReportJson;
import ai.kkc.erechnung.model.ValidationReport;
import ai.kkc.erechnung.model.Verdict;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Localhost HTTP daemon: warm JVM, single validation worker, FIFO queue.
 *
 * <p>{@code POST /validate} multipart field {@code file}; queue wait capped (default 20s) → 503.
 * Temp files live under a process-scoped directory (gone when the process exits).
 */
final class ValidateServe {

  private static final int QUEUE_CAPACITY = 64;
  private static final String HEALTHZ_BODY = "{\"status\":\"ok\"}\n";

  private final String bind;
  private final int port;
  private final Duration queueWait;
  private final ValidationOrchestrator orchestrator;
  private final ReportJson json = new ReportJson();
  private final Path tempRoot;
  private final BlockingQueue<Job> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
  private final AtomicBoolean running = new AtomicBoolean(true);
  private final Thread worker;
  private final java.util.concurrent.CountDownLatch shutdown =
      new java.util.concurrent.CountDownLatch(1);
  private HttpServer server;

  ValidateServe(String bind, int port, Duration queueWait, long maxXmlBytes) throws IOException {
    this.bind = bind;
    this.port = port;
    this.queueWait = queueWait;
    this.orchestrator = ValidationOrchestrator.createDefault(maxXmlBytes);
    this.tempRoot = Files.createTempDirectory("erechnung-serve-");
    this.worker = new Thread(this::workerLoop, "erechnung-validate-worker");
    this.worker.setDaemon(false);
  }

  int runBlocking() throws IOException, InterruptedException {
    worker.start();
    server = HttpServer.create(new InetSocketAddress(bind, port), 0);
    server.createContext("/healthz", this::healthz);
    server.createContext("/validate", this::validate);
    server.setExecutor(null);
    server.start();
    System.err.println("eRechnung serve listening on http://" + bind + ":" + port + "/");
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  stopInternal();
                },
                "erechnung-serve-shutdown"));
    shutdown.await();
    try {
      deleteRecursively(tempRoot);
    } catch (IOException ignored) {
      // ignore
    }
    return 0;
  }

  /** Test helper: stop server and release {@link #runBlocking()}. */
  void stopForTests() {
    stopInternal();
  }

  private void stopInternal() {
    running.set(false);
    if (server != null) {
      server.stop(0);
    }
    worker.interrupt();
    shutdown.countDown();
  }

  private void healthz(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      exchange.sendResponseHeaders(405, -1);
      exchange.close();
      return;
    }
    byte[] body = HEALTHZ_BODY.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
    exchange.sendResponseHeaders(200, body.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(body);
    }
  }

  private void validate(HttpExchange exchange) throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      exchange.sendResponseHeaders(405, -1);
      exchange.close();
      return;
    }
    Path uploaded = null;
    try {
      uploaded = readMultipartFile(exchange);
      Job job = new Job(uploaded, Instant.now(), new CompletableFuture<>());
      if (!queue.offer(job)) {
        respondJson(exchange, 503, busyReport("queue full"));
        return;
      }
      ValidationReport report;
      try {
        report = job.future.get();
      } catch (Exception ex) {
        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
        if (cause instanceof QueueTimeoutException) {
          respondJson(exchange, 503, busyReport("queue wait exceeded " + queueWait.toSeconds() + "s"));
          return;
        }
        respondJson(exchange, 500, toolErrorReport(cause));
        return;
      }
      int status = switch (report.getVerdict()) {
        case CONFORMANT, NONCONFORMANT, NOT_ERECHNUNG -> 200;
        case TOOL_ERROR -> 502;
      };
      respondJson(exchange, status, report);
    } catch (IllegalArgumentException ex) {
      respondJson(exchange, 422, toolErrorReport(ex));
    } catch (Exception ex) {
      respondJson(exchange, 500, toolErrorReport(ex));
    } finally {
      if (uploaded != null) {
        try {
          Files.deleteIfExists(uploaded);
        } catch (IOException ignored) {
          // ignore
        }
      }
    }
  }

  private void workerLoop() {
    while (running.get()) {
      Job job;
      try {
        job = queue.poll(500, TimeUnit.MILLISECONDS);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        break;
      }
      if (job == null) {
        continue;
      }
      if (Duration.between(job.enqueued, Instant.now()).compareTo(queueWait) > 0) {
        job.future.completeExceptionally(new QueueTimeoutException());
        continue;
      }
      try {
        job.future.complete(orchestrator.validate(job.path));
      } catch (Exception ex) {
        job.future.completeExceptionally(ex);
      }
    }
    // Fail waiters on shutdown
    Job leftover;
    while ((leftover = queue.poll()) != null) {
      leftover.future.completeExceptionally(new IOException("server shutting down"));
    }
  }

  private Path readMultipartFile(HttpExchange exchange) throws IOException {
    String contentType = header(exchange.getRequestHeaders(), "Content-Type");
    if (contentType == null || !contentType.toLowerCase(Locale.ROOT).contains("multipart/form-data")) {
      throw new IllegalArgumentException("Content-Type must be multipart/form-data");
    }
    String boundary = multipartBoundary(contentType);
    if (boundary == null || boundary.isBlank()) {
      throw new IllegalArgumentException("multipart boundary missing");
    }
    byte[] raw = exchange.getRequestBody().readAllBytes();
    MultipartFile part = extractFilePart(raw, boundary);
    if (part == null) {
      throw new IllegalArgumentException("multipart field 'file' missing");
    }
    String name = part.filename == null || part.filename.isBlank() ? "upload.bin" : Path.of(part.filename).getFileName().toString();
    if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf") && !name.toLowerCase(Locale.ROOT).endsWith(".xml")) {
      throw new IllegalArgumentException("Nur .pdf oder .xml sind erlaubt.");
    }
    Path target = tempRoot.resolve(System.nanoTime() + "-" + name);
    Files.write(target, part.bytes);
    return target;
  }

  static String multipartBoundary(String contentType) {
    for (String piece : contentType.split(";")) {
      String p = piece.trim();
      if (p.toLowerCase(Locale.ROOT).startsWith("boundary=")) {
        String b = p.substring("boundary=".length()).trim();
        if (b.startsWith("\"") && b.endsWith("\"") && b.length() >= 2) {
          b = b.substring(1, b.length() - 1);
        }
        return b;
      }
    }
    return null;
  }

  /**
   * Minimal multipart parser: finds part with name="file" (or first filename= part).
   */
  static MultipartFile extractFilePart(byte[] body, String boundary) {
    byte[] delim = ("--" + boundary).getBytes(StandardCharsets.UTF_8);
    int pos = indexOf(body, delim, 0);
    while (pos >= 0) {
      int headersStart = pos + delim.length;
      if (headersStart + 1 < body.length && body[headersStart] == '-' && body[headersStart + 1] == '-') {
        break; // closing boundary
      }
      if (headersStart < body.length && body[headersStart] == '\r') {
        headersStart++;
      }
      if (headersStart < body.length && body[headersStart] == '\n') {
        headersStart++;
      }
      int headerEnd = indexOf(body, "\r\n\r\n".getBytes(StandardCharsets.UTF_8), headersStart);
      if (headerEnd < 0) {
        break;
      }
      String headers = new String(body, headersStart, headerEnd - headersStart, StandardCharsets.UTF_8);
      int dataStart = headerEnd + 4;
      int next = indexOf(body, delim, dataStart);
      if (next < 0) {
        break;
      }
      int dataEnd = next;
      if (dataEnd >= 2 && body[dataEnd - 2] == '\r' && body[dataEnd - 1] == '\n') {
        dataEnd -= 2;
      }
      String name = dispositionParam(headers, "name");
      String filename = dispositionParam(headers, "filename");
      if ("file".equals(name) || (filename != null && !filename.isBlank())) {
        byte[] bytes = new byte[dataEnd - dataStart];
        System.arraycopy(body, dataStart, bytes, 0, bytes.length);
        return new MultipartFile(filename, bytes);
      }
      pos = next;
    }
    return null;
  }

  static String dispositionParam(String headers, String key) {
    for (String line : headers.split("\r\n")) {
      if (!line.toLowerCase(Locale.ROOT).startsWith("content-disposition:")) {
        continue;
      }
      for (String piece : line.split(";")) {
        String p = piece.trim();
        if (p.toLowerCase(Locale.ROOT).startsWith(key.toLowerCase(Locale.ROOT) + "=")) {
          String v = p.substring(key.length() + 1).trim();
          if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
            v = v.substring(1, v.length() - 1);
          }
          return v;
        }
      }
    }
    return null;
  }

  private static int indexOf(byte[] haystack, byte[] needle, int from) {
    outer:
    for (int i = from; i <= haystack.length - needle.length; i++) {
      for (int j = 0; j < needle.length; j++) {
        if (haystack[i + j] != needle[j]) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
  }

  private static String header(Headers headers, String name) {
    return headers.getFirst(name);
  }

  private void respondJson(HttpExchange exchange, int status, ValidationReport report)
      throws IOException {
    byte[] body = json.toJson(report).getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
    exchange.sendResponseHeaders(status, body.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(body);
    }
  }

  private static ValidationReport busyReport(String message) {
    ValidationReport report = new ValidationReport();
    report.setVerdict(Verdict.TOOL_ERROR);
    report.setSummary(Map.of("error", message));
    report.setErechnungXml("");
    report.setMustangPruefbericht("");
    return report;
  }

  private static ValidationReport toolErrorReport(Throwable ex) {
    ValidationReport report = new ValidationReport();
    report.setVerdict(Verdict.TOOL_ERROR);
    report.setSummary(
        Map.of(
            "error",
            ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
    report.setErechnungXml("");
    report.setMustangPruefbericht("");
    return report;
  }

  private static void deleteRecursively(Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    try (var walk = Files.walk(root)) {
      walk.sorted((a, b) -> b.compareTo(a))
          .forEach(
              p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (IOException ignored) {
                  // ignore
                }
              });
    }
  }

  record MultipartFile(String filename, byte[] bytes) {}

  private record Job(Path path, Instant enqueued, CompletableFuture<ValidationReport> future) {}

  private static final class QueueTimeoutException extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }

  // visible for tests — collect multipart body for unit tests without HTTP
  static byte[] buildMultipart(String boundary, String filename, byte[] content) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      String preamble =
          "--"
              + boundary
              + "\r\n"
              + "Content-Disposition: form-data; name=\"file\"; filename=\""
              + filename
              + "\"\r\n"
              + "Content-Type: application/octet-stream\r\n\r\n";
      out.write(preamble.getBytes(StandardCharsets.UTF_8));
      out.write(content);
      out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    return out.toByteArray();
  }
}
