package com.tiktok.video.audio.tts;

import com.sun.net.httpserver.HttpServer;
import com.tiktok.video.audio.util.WavAudioUtil;
import com.tiktok.video.core.contract.TtsRequest;
import com.tiktok.video.core.exception.TtsException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VieneuTtsClientTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(null);
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("F7 & F8: Successful HTTP 200 response with 0xFFFFFFFF streaming header is patched automatically")
    void testSynthesizeSuccessWithStreamingHeaderPatching() throws Exception {
        byte[] pcm = new byte[9600]; // 0.1s
        byte[] streamingWav = WavAudioUtil.buildWav(pcm);

        // Put 0xFFFFFFFF in sizes
        ByteBuffer buf = ByteBuffer.wrap(streamingWav).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(4, 0xFFFFFFFF);
        buf.putInt(40, 0xFFFFFFFF);

        server.createContext("/v1/audio/speech", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "audio/wav");
            exchange.sendResponseHeaders(200, streamingWav.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(streamingWav);
            }
        });

        VieneuTtsClient client = new VieneuTtsClient("http://127.0.0.1:" + port);
        byte[] result = client.synthesize(new TtsRequest("Xin chào", "vieneu-v3-turbo", "Hải Đăng", "wav", 1.0));

        assertThat(result).isNotNull();
        assertThat(result.length).isEqualTo(streamingWav.length);

        ByteBuffer resBuf = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(resBuf.getInt(4)).isEqualTo(streamingWav.length - 8);
        assertThat(resBuf.getInt(40)).isEqualTo(streamingWav.length - 44);
    }

    @Test
    @DisplayName("F7: HTTP 400 Bad Request throws TtsException with status code and error message")
    void testSynthesizeHttp400BadRequest() {
        server.createContext("/v1/audio/speech", exchange -> {
            String errJson = "{\"error\":{\"message\":\"unknown voice 'BadVoice'\",\"type\":\"invalid_request_error\"}}";
            byte[] bytes = errJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(400, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        VieneuTtsClient client = new VieneuTtsClient("http://127.0.0.1:" + port);
        assertThatThrownBy(() -> client.synthesize(new TtsRequest("Hello", "m", "BadVoice", "wav", 1.0)))
            .isInstanceOf(TtsException.class)
            .hasMessageContaining("400")
            .hasMessageContaining("unknown voice");
    }

    @Test
    @DisplayName("F7: HTTP 500 Server Error throws TtsException")
    void testSynthesizeHttp500ServerError() {
        server.createContext("/v1/audio/speech", exchange -> {
            byte[] bytes = "Internal Server Error".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        VieneuTtsClient client = new VieneuTtsClient("http://127.0.0.1:" + port);
        assertThatThrownBy(() -> client.synthesize(new TtsRequest("Hello", "m", "v", "wav", 1.0)))
            .isInstanceOf(TtsException.class)
            .hasMessageContaining("500");
    }

    @Test
    @DisplayName("F7: Connection failure throws TtsException")
    void testSynthesizeConnectionFailure() {
        // Connect to port that is closed immediately
        server.stop(0);
        VieneuTtsClient client = new VieneuTtsClient("http://127.0.0.1:" + port);

        assertThatThrownBy(() -> client.synthesize(new TtsRequest("Hello", "m", "v", "wav", 1.0)))
            .isInstanceOf(TtsException.class);
    }

    @Test
    @DisplayName("isAvailable returns true when health endpoint reports ok")
    void testIsAvailableHealthy() {
        server.createContext("/health", exchange -> {
            String json = "{\"status\":\"ok\",\"backend\":\"onnx\"}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        VieneuTtsClient client = new VieneuTtsClient("http://127.0.0.1:" + port);
        assertThat(client.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("isAvailable returns false when server is unreachable")
    void testIsAvailableUnreachable() {
        server.stop(0);
        VieneuTtsClient client = new VieneuTtsClient("http://127.0.0.1:" + port);
        assertThat(client.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("Request validation throws IllegalArgumentException")
    void testNullOrBlankRequestValidation() {
        VieneuTtsClient client = new VieneuTtsClient("http://127.0.0.1:" + port);

        assertThatThrownBy(() -> client.synthesize(null))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> client.synthesize(new TtsRequest("", "m", "v", "wav", 1.0)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
