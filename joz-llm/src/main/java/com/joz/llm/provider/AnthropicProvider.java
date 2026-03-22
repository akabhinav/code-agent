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

/** Anthropic Claude Messages API provider. */
public class AnthropicProvider implements LLMProvider {

    private static final Logger log = LoggerFactory.getLogger(AnthropicProvider.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String API_VERSION = "2023-06-01";

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;

    public AnthropicProvider(String apiKey, String baseUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new JozException.ConfigException(
                    "Anthropic API key is required. Set ANTHROPIC_API_KEY or configure in ~/.joz/config.yaml");
        }
        this.apiKey = apiKey;
        this.baseUrl = baseUrl != null ? baseUrl : "https://api.anthropic.com";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public String name() {
        return "anthropic";
    }

    @Override
    public LLMResponse chat(ChatRequest request) {
        try {
            var body = buildRequestBody(request);
            var jsonBody = mapper.writeValueAsString(body);
            log.debug("Sending request to Anthropic API: {} tokens",
                    jsonBody.length());

            var httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/messages"))
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", API_VERSION)
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofMinutes(5))
                    .build();

            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new JozException.LLMException(
                        "Anthropic API error (HTTP %d): %s".formatted(response.statusCode(), response.body()));
            }

            return parseResponse(mapper.readTree(response.body()));
        } catch (JozException e) {
            throw e;
        } catch (Exception e) {
            throw new JozException.LLMException("Failed to call Anthropic API", e);
        }
    }

    @Override
    public List<String> supportedModels() {
        return List.of(
                "claude-sonnet-4-6-20250514",
                "claude-haiku-4-5-20251001",
                "claude-opus-4-6-20250610");
    }

    private ObjectNode buildRequestBody(ChatRequest request) {
        var body = mapper.createObjectNode();
        body.put("model", request.model());
        body.put("max_tokens", request.maxTokens() > 0 ? request.maxTokens() : 16384);

        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            body.put("system", request.systemPrompt());
        }

        body.put("temperature", request.temperature());

        // Build messages array
        var messagesArray = body.putArray("messages");
        for (var message : request.messages()) {
            convertMessage(message, messagesArray);
        }

        // Build tools array
        if (!request.tools().isEmpty()) {
            var toolsArray = body.putArray("tools");
            for (var tool : request.tools()) {
                var toolNode = toolsArray.addObject();
                toolNode.put("name", tool.name());
                toolNode.put("description", tool.description());
                toolNode.set("input_schema", tool.inputSchema());
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
                var contentArray = node.putArray("content");
                if (text != null && !text.isBlank()) {
                    var textBlock = contentArray.addObject();
                    textBlock.put("type", "text");
                    textBlock.put("text", text);
                }
                for (var tc : toolCalls) {
                    var tcBlock = contentArray.addObject();
                    tcBlock.put("type", "tool_use");
                    tcBlock.put("id", tc.id());
                    tcBlock.put("name", tc.name());
                    tcBlock.set("input", tc.input());
                }
            }
            case Message.SystemMessage s -> {
                // System messages are handled via the system parameter
            }
            case Message.ToolResultMessage(var toolUseId, var unused, var content) -> {
                var node = messagesArray.addObject();
                node.put("role", "user");
                var contentArray = node.putArray("content");
                var resultBlock = contentArray.addObject();
                resultBlock.put("type", "tool_result");
                resultBlock.put("tool_use_id", toolUseId);
                resultBlock.put("content", content);
            }
        }
    }

    private LLMResponse parseResponse(JsonNode responseJson) {
        var content = responseJson.get("content");
        var stopReason = responseJson.get("stop_reason").asText();

        var textBuilder = new StringBuilder();
        var toolCalls = new ArrayList<ToolCall>();

        if (content != null && content.isArray()) {
            for (var block : content) {
                var type = block.get("type").asText();
                switch (type) {
                    case "text" -> textBuilder.append(block.get("text").asText());
                    case "tool_use" -> toolCalls.add(new ToolCall(
                            block.get("id").asText(),
                            block.get("name").asText(),
                            block.get("input")));
                    default -> log.debug("Unknown content block type: {}", type);
                }
            }
        }

        var llmStopReason = switch (stopReason) {
            case "end_turn" -> LLMResponse.StopReason.END_TURN;
            case "tool_use" -> LLMResponse.StopReason.TOOL_USE;
            case "max_tokens" -> LLMResponse.StopReason.MAX_TOKENS;
            default -> LLMResponse.StopReason.END_TURN;
        };

        var usageNode = responseJson.get("usage");
        var usage = usageNode != null
                ? new LLMResponse.Usage(
                    usageNode.get("input_tokens").asInt(),
                    usageNode.get("output_tokens").asInt())
                : new LLMResponse.Usage(0, 0);

        return new LLMResponse(textBuilder.toString(), toolCalls, llmStopReason, usage);
    }
}
