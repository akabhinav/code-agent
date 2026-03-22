package com.joz.llm.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joz.common.model.Message;
import com.joz.llm.ChatRequest;
import com.joz.llm.LLMResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Integration test using a mock Ollama HTTP server. */
class OllamaProviderIntegrationTest {

    private HttpServer mockServer;
    private OllamaProvider provider;
    private final ObjectMapper mapper = new ObjectMapper();
    private int port;

    @BeforeEach
    void setup() throws IOException {
        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        port = mockServer.getAddress().getPort();
        provider = new OllamaProvider("http://localhost:" + port);
    }

    @AfterEach
    void teardown() {
        if (mockServer != null) mockServer.stop(0);
    }

    @Test
    void sendsRequestAndParsesTextResponse() {
        mockServer.createContext("/api/chat", exchange -> {
            // Verify we got a valid request
            var body = new String(exchange.getRequestBody().readAllBytes());
            var json = mapper.readTree(body);
            assertEquals("test-model", json.get("model").asText());
            assertFalse(json.get("stream").asBoolean());

            // Verify messages array
            var messages = json.get("messages");
            assertTrue(messages.isArray());
            // system + user = 2 messages
            assertEquals(2, messages.size());
            assertEquals("system", messages.get(0).get("role").asText());
            assertEquals("user", messages.get(1).get("role").asText());
            assertEquals("Hello!", messages.get(1).get("content").asText());

            var response = """
                    {
                      "model": "test-model",
                      "message": {
                        "role": "assistant",
                        "content": "Hello! I'm a local LLM running via Ollama. How can I help you today?"
                      },
                      "done": true
                    }
                    """;
            var bytes = response.getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        });
        mockServer.start();

        var request = new ChatRequest(
                "You are a helpful assistant.",
                List.of(new Message.UserMessage("Hello!")),
                List.of(),
                "test-model",
                0.0,
                4096);

        var response = provider.chat(request);

        assertEquals(LLMResponse.StopReason.END_TURN, response.stopReason());
        assertTrue(response.text().contains("local LLM"));
        assertFalse(response.hasToolCalls());
    }

    @Test
    void parsesToolCallResponse() {
        mockServer.createContext("/api/chat", exchange -> {
            var response = """
                    {
                      "model": "test-model",
                      "message": {
                        "role": "assistant",
                        "content": "Let me read that file for you.",
                        "tool_calls": [
                          {
                            "function": {
                              "name": "file_read",
                              "arguments": {"path": "src/main/java/App.java"}
                            }
                          }
                        ]
                      },
                      "done": true
                    }
                    """;
            var bytes = response.getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        });
        mockServer.start();

        var request = new ChatRequest(
                "You are a coding assistant.",
                List.of(new Message.UserMessage("Read App.java")),
                List.of(new ChatRequest.ToolDefinition("file_read", "Read a file",
                        mapper.createObjectNode()
                                .put("type", "object")
                                .<com.fasterxml.jackson.databind.node.ObjectNode>set("properties",
                                        mapper.createObjectNode().set("path",
                                                mapper.createObjectNode().put("type", "string"))))),
                "test-model",
                0.0,
                4096);

        var response = provider.chat(request);

        assertEquals(LLMResponse.StopReason.TOOL_USE, response.stopReason());
        assertTrue(response.hasToolCalls());
        assertEquals(1, response.toolCalls().size());
        assertEquals("file_read", response.toolCalls().getFirst().name());
        assertEquals("src/main/java/App.java",
                response.toolCalls().getFirst().input().get("path").asText());
    }

    @Test
    void handlesServerError() {
        mockServer.createContext("/api/chat", exchange -> {
            var response = "Internal Server Error";
            exchange.sendResponseHeaders(500, response.length());
            exchange.getResponseBody().write(response.getBytes());
            exchange.getResponseBody().close();
        });
        mockServer.start();

        var request = new ChatRequest(
                null,
                List.of(new Message.UserMessage("test")),
                List.of(),
                "test-model",
                0.0,
                4096);

        assertThrows(Exception.class, () -> provider.chat(request));
    }

    @Test
    void providerNameIsOllama() {
        assertEquals("ollama", provider.name());
    }

    @Test
    void supportedModelsIncludesQwen() {
        assertTrue(provider.supportedModels().contains("qwen2.5-coder:14b"));
    }
}
