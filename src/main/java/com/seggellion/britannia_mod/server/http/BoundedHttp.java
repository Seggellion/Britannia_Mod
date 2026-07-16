package com.seggellion.britannia_mod.server.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

public final class BoundedHttp {
    public static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    public static final int READ_TIMEOUT_MILLIS = 10_000;
    public static final int OVERALL_TIMEOUT_SECONDS = 15;

    private BoundedHttp() {}

    public static void configure(HttpURLConnection connection) {
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
    }

    public static String readUtf8(InputStream input, int maxBytes) throws IOException {
        return new String(readBytes(input, maxBytes), StandardCharsets.UTF_8);
    }

    public static byte[] readBytes(InputStream input, int maxBytes) throws IOException {
        if (input == null) return new byte[0];
        if (maxBytes <= 0) throw new IllegalArgumentException("maxBytes must be positive");
        try (input) {
            byte[] bytes = input.readNBytes(maxBytes + 1);
            if (bytes.length > maxBytes) throw new ResponseTooLargeException();
            return bytes;
        }
    }

    public static final class ResponseTooLargeException extends IOException {
        public ResponseTooLargeException() {
            super("HTTP response exceeded the configured size limit");
        }
    }
}
