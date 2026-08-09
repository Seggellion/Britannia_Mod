package com.seggellion.britannia_mod.server.http;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CancellableHttpRequestTest {
    private static final URI URI_UNDER_TEST = URI.create("http://127.0.0.1/bootstrap");

    @Test
    void acceptsExactlyTheLimitAndDisconnectsAfterClosingTheSuccessStream() throws Exception {
        TrackingInputStream body = new TrackingInputStream("1234".getBytes(StandardCharsets.UTF_8));
        FakeConnection connection = new FakeConnection(200, body, null);
        CancellableHttpRequest request = request(connection, 4);

        CancellableHttpRequest.Response response = request.execute(conn -> conn.setRequestMethod("GET"));

        assertArrayEquals("1234".getBytes(StandardCharsets.UTF_8), response.body());
        assertTrue(body.closed);
        assertTrue(connection.disconnected);
        assertEquals(BoundedHttp.CONNECT_TIMEOUT_MILLIS, connection.getConnectTimeout());
        assertEquals(BoundedHttp.READ_TIMEOUT_MILLIS, connection.getReadTimeout());
        assertFalse(connection.getInstanceFollowRedirects());
    }

    @Test
    void rejectsOneByteOverTheLimitAndDisconnects() {
        TrackingInputStream body = new TrackingInputStream("12345".getBytes(StandardCharsets.UTF_8));
        FakeConnection connection = new FakeConnection(200, body, null);

        CancellableHttpRequest.RequestException failure = assertThrows(
            CancellableHttpRequest.RequestException.class,
            () -> request(connection, 4).execute(ignored -> {})
        );

        assertEquals(CancellableHttpRequest.FailureCode.RESPONSE_TOO_LARGE, failure.code());
        assertTrue(body.closed);
        assertTrue(connection.disconnected);
    }

    @Test
    void closesBoundedErrorStreamAndDisconnectsOnHttpError() throws Exception {
        TrackingInputStream error = new TrackingInputStream("safe error".getBytes(StandardCharsets.UTF_8));
        FakeConnection connection = new FakeConnection(503, null, error);

        CancellableHttpRequest.Response response = request(connection, 1024).execute(ignored -> {});

        assertEquals(503, response.status());
        assertEquals("safe error", new String(response.body(), StandardCharsets.UTF_8));
        assertTrue(error.closed);
        assertTrue(connection.disconnected);
    }

    @Test
    void connectAndReadTimeoutsAreClassifiedAndDisconnected() {
        FakeConnection connectTimeout = new FakeConnection(200, new TrackingInputStream(new byte[0]), null);
        connectTimeout.connectFailure = new SocketTimeoutException("test connect timeout");
        CancellableHttpRequest.RequestException connectFailure = assertThrows(
            CancellableHttpRequest.RequestException.class,
            () -> request(connectTimeout, 16).execute(ignored -> {})
        );
        assertEquals(CancellableHttpRequest.FailureCode.CONNECT_TIMEOUT, connectFailure.code());
        assertTrue(connectTimeout.disconnected);

        TimeoutInputStream body = new TimeoutInputStream();
        FakeConnection readTimeout = new FakeConnection(200, body, null);
        CancellableHttpRequest.RequestException readFailure = assertThrows(
            CancellableHttpRequest.RequestException.class,
            () -> request(readTimeout, 16).execute(ignored -> {})
        );
        assertEquals(CancellableHttpRequest.FailureCode.READ_TIMEOUT, readFailure.code());
        assertTrue(body.closed);
        assertTrue(readTimeout.disconnected);
    }

    @Test
    void explicitCancellationDisconnectsAndUnblocksTheRead() throws Exception {
        BlockingInputStream body = new BlockingInputStream();
        FakeConnection connection = new FakeConnection(200, body, null);
        CancellableHttpRequest request = request(connection, 16);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<CancellableHttpRequest.FailureCode> result = executor.submit(() -> {
                try {
                    request.execute(ignored -> {});
                    return null;
                } catch (CancellableHttpRequest.RequestException failure) {
                    return failure.code();
                }
            });
            assertTrue(body.readStarted.await(5, TimeUnit.SECONDS));

            request.cancel();

            assertEquals(CancellableHttpRequest.FailureCode.CANCELLED, result.get(5, TimeUnit.SECONDS));
            assertTrue(connection.disconnected);
            assertTrue(body.closed);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void cancellationBeforeExecutionDoesNotOpenAConnection() {
        AtomicInteger opened = new AtomicInteger();
        CancellableHttpRequest request = new CancellableHttpRequest(
            URI_UNDER_TEST, 16, ignored -> {
                opened.incrementAndGet();
                return new FakeConnection(200, new TrackingInputStream(new byte[0]), null);
            }
        );
        request.cancel();

        CancellableHttpRequest.RequestException failure = assertThrows(
            CancellableHttpRequest.RequestException.class,
            () -> request.execute(ignored -> {})
        );

        assertEquals(CancellableHttpRequest.FailureCode.CANCELLED, failure.code());
        assertEquals(0, opened.get());
    }

    @Test
    void connectionIsAlreadyDisconnectedWhenCallerParsesAMalformedBody() throws Exception {
        FakeConnection connection = new FakeConnection(
            200, new TrackingInputStream("not-json".getBytes(StandardCharsets.UTF_8)), null);
        CancellableHttpRequest.Response response = request(connection, 64).execute(ignored -> {});

        assertTrue(connection.disconnected);
        assertThrows(RuntimeException.class,
            () -> JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject());
    }

    @Test
    void writesFixedLengthRequestBodyAndCopiesResponseHeaders() throws Exception {
        FakeConnection connection = new FakeConnection(
            200, new TrackingInputStream("{}".getBytes(StandardCharsets.UTF_8)), null);
        connection.headers = Map.of("Retry-After", List.of("12"));
        byte[] outbound = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);

        CancellableHttpRequest.Response response = request(connection, 64).execute(
            conn -> conn.setRequestMethod("POST"), outbound
        );
        outbound[0] = 'X';

        assertArrayEquals("{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8),
            connection.output.toByteArray());
        assertEquals(connection.output.size(), connection.fixedLength());
        assertEquals("12", response.firstHeader("retry-after"));
        assertThrows(UnsupportedOperationException.class,
            () -> response.headers().put("unsafe", List.of("value")));
    }

    private static CancellableHttpRequest request(FakeConnection connection, int limit) {
        return new CancellableHttpRequest(URI_UNDER_TEST, limit, ignored -> connection);
    }

    private static class FakeConnection extends HttpURLConnection {
        private final InputStream successStream;
        private final InputStream errorStream;
        private IOException connectFailure;
        private boolean disconnected;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private Map<String, List<String>> headers = Map.of();

        FakeConnection(int status, InputStream successStream, InputStream errorStream) {
            super(url());
            this.responseCode = status;
            this.successStream = successStream;
            this.errorStream = errorStream;
        }

        @Override
        public void connect() throws IOException {
            if (connectFailure != null) throw connectFailure;
            connected = true;
        }

        @Override
        public InputStream getInputStream() {
            return successStream;
        }

        @Override
        public InputStream getErrorStream() {
            return errorStream;
        }

        @Override
        public OutputStream getOutputStream() {
            return output;
        }

        @Override
        public Map<String, List<String>> getHeaderFields() {
            return headers;
        }

        int fixedLength() {
            return fixedContentLength;
        }

        @Override
        public void disconnect() {
            disconnected = true;
            connected = false;
            closeQuietly(successStream);
            closeQuietly(errorStream);
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        private static URL url() {
            try {
                return URI_UNDER_TEST.toURL();
            } catch (Exception impossible) {
                throw new AssertionError(impossible);
            }
        }

        private static void closeQuietly(InputStream stream) {
            if (stream == null) return;
            try {
                stream.close();
            } catch (IOException ignored) {}
        }
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        TrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static final class TimeoutInputStream extends InputStream {
        private boolean closed;

        @Override
        public int read() throws IOException {
            throw new SocketTimeoutException("test read timeout");
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            throw new SocketTimeoutException("test read timeout");
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class BlockingInputStream extends InputStream {
        private final CountDownLatch readStarted = new CountDownLatch(1);
        private boolean closed;

        @Override
        public synchronized int read() throws IOException {
            return waitForClose();
        }

        @Override
        public synchronized int read(byte[] bytes, int offset, int length) throws IOException {
            return waitForClose();
        }

        private int waitForClose() throws IOException {
            readStarted.countDown();
            while (!closed) {
                try {
                    wait();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted", interrupted);
                }
            }
            return -1;
        }

        @Override
        public synchronized void close() {
            closed = true;
            notifyAll();
        }
    }
}
