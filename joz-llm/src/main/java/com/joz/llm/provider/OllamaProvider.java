package com.joz.llm.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.joz.common.exception.JozException;
import com.joz.common.model.Message;
import com.joz.common.model.ToolCall;
import com.joz.llm.ChatRequest;
import com.joz.llm.LLMResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Ollama local LLM provider using /api/chat endpoint. */
public class OllamaProvider implements LLMProvider {

    private static final Logger log = LoggerFactory.getLogger(OllamaProvider.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final String baseUrl;
    private final HttpClient httpClient;

    public OllamaProvider(String baseUrl) {
        this.baseUrl = baseUrl != null ? baseUrl : "http://localhost:11434";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String name() {
        return "ollama";
    }

    @Override
    public LLMResponse chat(ChatRequest request) {
        try {
            var body = buildRequestBody(request);
            var jsonBody = mapper.writeValueAsString(body);

            var httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/chat"))
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofMinutes(10))
                    .build();

            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new JozException.LLMException(
                        "Ollama API error (HTTP %d): %s".formatted(response.statusCode(), response.body()));
            }

            return parseResponse(mapper.readTree(response.body()));
        } catch (JozException e) {
            throw e;
        } catch (Exception e) {
            throw new JozException.LLMException("Failed to call Ollama API", e);
        }
    }

    @Override
    public List<String> supportedModels() {
        return List.of(
                "qwen2.5-coder:14b",
                "deepseek-coder-v2",
                "codellama",
                "llama3.1");
    }

    private ObjectNode buildRequestBody(ChatRequest request) {
        var body = mapper.createObjectNode();
        body.put("model", request.model());
        body.put("stream", false);

        var options = body.putObject("options");
        options.put("temperature", request.temperature());

        var messagesArray = body.putArray("messages");

        // Add system message
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            var sysMsg = messagesArray.addObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", request.systemPrompt());
        }

        // Add conversation messages
        for (var message : request.messages()) {
            convertMessage(message, messagesArray);
        }

        // Add tools (OpenAI-compatible format)
        if (!request.tools().isEmpty()) {
            var toolsArray = body.putArray("tools");
            for (var tool : request.tools()) {
                var toolNode = toolsArray.addObject();
                toolNode.put("type", "function");
                var funcNode = toolNode.putObject("function");
                funcNode.put("name", tool.name());
                funcNode.put("description", tool.description());
                funcNode.set("parameters", tool.inputSchema());
            }
        }

        return body;
    }

    private void convertMessage(Message message, ArrayNode messagesArray) {
        switch (message) {
            case Message.UserMessage(var content) -> {
                var node = messagesArray.addObject();
                node.put("role", "user");
                node.put("content", content);
            }
            case Message.AssistantMessage(var text, var toolCalls) -> {
                var node = messagesArray.addObject();
                node.put("role", "assistant");
                node.put("content", text != null ? text : "");
                if (!toolCalls.isEmpty()) {
                    var tcArray = node.putArray("tool_calls");
                    for (var tc : toolCalls) {
                        var tcNode = tcArray.addObject();
                        var funcNode = tcNode.putObject("function");
                        funcNode.put("name", tc.name());
                        funcNode.set("arguments", tc.input());
                    }
                }
            }
            case Message.SystemMessage(var content) -> {
                var node = messagesArray.addObject();
                node.put("role", "system");
                node.put("content", content);
            }
            case Message.ToolResultMessage(var unused, var name, var content) -> {
                var node = messagesArray.addObject();
                node.put("role", "tool");
                node.put("content", content);
            }
        }
    }

    private LLMResponse parseResponse(JsonNode responseJson) {
        var messageNode = responseJson.get("message");
        var text = messageNode.has("content") ? messageNode.get("content").asText() : "";

        var toolCalls = new ArrayList<ToolCall>();
        if (messageNode.has("tool_calls") && messageNode.get("tool_calls").isArray()) {
            int idx = 0;
            for (var tc : messageNode.get("tool_calls")) {
                var func = tc.get("function");
                toolCalls.add(new ToolCall(
                        "ollama_tc_" + idx++,
                        func.get("name").asText(),
                        func.get("arguments")));
            }
        }

        var stopReason = toolCalls.isEmpty()
                ? LLMResponse.StopReason.END_TURN
                : LLMResponse.StopReason.TOOL_USE;

        return new LLMResponse(text, toolCalls, stopReason, new LLMResponse.Usage(0, 0));
    }
}
