package com.seggellion.britannia_mod.server.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** A single-use bounded HTTP request whose active connection can be cancelled safely. */
public final class CancellableHttpRequest {
    public static final int MAX_ERROR_BYTES = 64 * 1024;

    private final URI uri;
    private final int maxBodyBytes;
    private final ConnectionFactory connectionFactory;
    private final AtomicReference<HttpURLConnection> activeConnection = new AtomicReference<>();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean started = new AtomicBoolean();

    public CancellableHttpRequest(URI uri, int maxBodyBytes) {
        this(uri, maxBodyBytes, target -> (HttpURLConnection) target.toURL().openConnection());
    }

    CancellableHttpRequest(URI uri, int maxBodyBytes, ConnectionFactory connectionFactory) {
        this.uri = Objects.requireNonNull(uri, "uri");
        if (maxBodyBytes <= 0) throw new IllegalArgumentException("maxBodyBytes must be positive");
        this.maxBodyBytes = maxBodyBytes;
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory");
    }

    public Response execute(ConnectionConfigurer configurer) throws RequestException {
        Objects.requireNonNull(configurer, "configurer");
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("HTTP request can only be executed once");
        }
        if (cancelled.get()) throw failure(FailureCode.CANCELLED, null);

        HttpURLConnection connection = null;
        InputStream stream = null;
        try {
            connection = connectionFactory.open(uri);
            if (!activeConnection.compareAndSet(null, connection)) {
                throw new IllegalStateException("HTTP request is already active or was already executed");
            }
            if (cancelled.get()) throw failure(FailureCode.CANCELLED, null);

            BoundedHttp.configure(connection);
            connection.setInstanceFollowRedirects(false);
            configurer.configure(connection);
            try {
                connection.connect();
            } catch (SocketTimeoutException timeout) {
                throw failure(FailureCode.CONNECT_TIMEOUT, timeout);
            }
            if (cancelled.get()) throw failure(FailureCode.CANCELLED, null);

            final int status;
            try {
                status = connection.getResponseCode();
                stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
                int limit = status >= 200 && status < 300 ? maxBodyBytes : MAX_ERROR_BYTES;
                byte[] body = BoundedHttp.readBytes(stream, limit);
                if (cancelled.get()) throw failure(FailureCode.CANCELLED, null);
                return new Response(status, body);
            } catch (SocketTimeoutException timeout) {
                throw failure(FailureCode.READ_TIMEOUT, timeout);
            } catch (BoundedHttp.ResponseTooLargeException tooLarge) {
                throw failure(FailureCode.RESPONSE_TOO_LARGE, tooLarge);
            }
        } catch (CancellationException cancelledRequest) {
            throw failure(FailureCode.CANCELLED, cancelledRequest);
        } catch (RequestException classified) {
            throw classified;
        } catch (IOException transportFailure) {
            if (cancelled.get()) throw failure(FailureCode.CANCELLED, transportFailure);
            throw failure(FailureCode.TRANSPORT_ERROR, transportFailure);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                    // The connection is disconnected below regardless.
                }
            }
            if (connection != null) {
                activeConnection.compareAndSet(connection, null);
                connection.disconnect();
            }
        }
    }

    public void cancel() {
        cancelled.set(true);
        HttpURLConnection connection = activeConnection.getAndSet(null);
        if (connection != null) connection.disconnect();
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    private static RequestException failure(FailureCode code, Throwable cause) {
        return new RequestException(code, cause);
    }

    public record Response(int status, byte[] body) {
        public Response {
            body = body == null ? new byte[0] : body.clone();
        }

        @Override
        public byte[] body() {
            return body.clone();
        }
    }

    public enum FailureCode {
        CONNECT_TIMEOUT("connect_timeout"),
        READ_TIMEOUT("read_timeout"),
        RESPONSE_TOO_LARGE("response_too_large"),
        CANCELLED("cancelled"),
        TRANSPORT_ERROR("transport_error");

        private final String safeCode;

        FailureCode(String safeCode) {
            this.safeCode = safeCode;
        }

        public String safeCode() {
            return safeCode;
        }
    }

    public static final class RequestException extends IOException {
        private final FailureCode code;

        private RequestException(FailureCode code, Throwable cause) {
            super(code.safeCode(), cause);
            this.code = code;
        }

        public FailureCode code() {
            return code;
        }
    }

    @FunctionalInterface
    public interface ConnectionConfigurer {
        void configure(HttpURLConnection connection) throws IOException;
    }

    @FunctionalInterface
    interface ConnectionFactory {
        HttpURLConnection open(URI uri) throws IOException;
    }
}
