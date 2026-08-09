package com.seggellion.britannia_mod.server.http;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.*;

class BoundedHttpTest {
    @Test
    void appliesExplicitConnectionAndReadTimeouts() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1/").openConnection();
        BoundedHttp.configure(connection);
        assertEquals(5_000, connection.getConnectTimeout());
        assertEquals(10_000, connection.getReadTimeout());
        assertEquals(15, BoundedHttp.OVERALL_TIMEOUT_SECONDS);
    }

    @Test
    void rejectsResponsesOverTheConfiguredLimit() {
        ByteArrayInputStream input = new ByteArrayInputStream("12345".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> BoundedHttp.readUtf8(input, 4));
    }

    @Test
    void executorIsBoundedAndUsesDaemonThreads() {
        ThreadPoolExecutor executor = ServerHttpExecutor.newExecutor();
        try {
            assertEquals(ServerHttpExecutor.QUEUE_CAPACITY, executor.getQueue().remainingCapacity());
            var thread = executor.getThreadFactory().newThread(() -> {});
            assertTrue(thread.isDaemon());
            assertTrue(thread.getName().startsWith("britannia-server-http-"));
        } finally {
            executor.shutdownNow();
        }
    }
}
