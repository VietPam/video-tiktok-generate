package com.tiktok.video.script.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiLlmClientTest {

    @Mock
    private HttpClient mockHttpClient;

    @Mock
    private HttpResponse<String> mockHttpResponse;

    @Test
    @DisplayName("F4.2: Successful HTTP 200 response parses JSON content correctly")
    void testGenerateSuccess() throws Exception {
        String responseJson = """
            {
              "choices": [
                {
                  "message": {
                    "content": "{\\"hook\\": \\"Welcome to Family lesson!\\"}"
                  }
                }
              ]
            }
            """;

        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(responseJson);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockHttpResponse);

        OpenAiLlmClient client = new OpenAiLlmClient(
            URI.create("https://api.openai.com/v1/chat/completions"),
            "test-key",
            "gpt-4o-mini",
            Duration.ofSeconds(5),
            mockHttpClient,
            new ObjectMapper()
        );

        String result = client.generate("System prompt", "User prompt");
        assertThat(result).isEqualTo("{\"hook\": \"Welcome to Family lesson!\"}");
    }

    @Test
    @DisplayName("F4.3: Strips markdown code block fences (```json ... ```)")
    void testStripMarkdownCodeBlock() {
        String markdown = "```json\n{\"hook\": \"Clean JSON\"}\n```";
        assertThat(OpenAiLlmClient.stripMarkdownCodeBlock(markdown))
            .isEqualTo("{\"hook\": \"Clean JSON\"}");
    }

    @Test
    @DisplayName("HTTP 500 error throws LlmException")
    void testHttpErrorThrows() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(500);
        when(mockHttpResponse.body()).thenReturn("Internal Server Error");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockHttpResponse);

        OpenAiLlmClient client = new OpenAiLlmClient(
            URI.create("https://api.openai.com/v1/chat/completions"),
            "test-key",
            "gpt-4o-mini",
            Duration.ofSeconds(5),
            mockHttpClient,
            new ObjectMapper()
        );

        assertThatThrownBy(() -> client.generate("Sys", "User"))
            .isInstanceOf(LlmException.class)
            .hasMessageContaining("500");
    }

    @Test
    @DisplayName("HttpTimeoutException is wrapped into LlmException")
    void testTimeoutThrows() throws Exception {
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new HttpTimeoutException("Timeout"));

        OpenAiLlmClient client = new OpenAiLlmClient(
            URI.create("https://api.openai.com/v1/chat/completions"),
            "test-key",
            "gpt-4o-mini",
            Duration.ofSeconds(5),
            mockHttpClient,
            new ObjectMapper()
        );

        assertThatThrownBy(() -> client.generate("Sys", "User"))
            .isInstanceOf(LlmException.class)
            .hasMessageContaining("timed out");
    }
}
