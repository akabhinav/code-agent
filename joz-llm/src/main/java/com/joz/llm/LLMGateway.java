package com.joz.llm;

import com.joz.common.config.LLMConfig;
import com.joz.common.exception.JozException;
import com.joz.llm.provider.AnthropicProvider;
import com.joz.llm.provider.LLMProvider;
import com.joz.llm.provider.OllamaProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Routes chat requests to the configured LLM provider. */
public class LLMGateway {

    private static final Logger log = LoggerFactory.getLogger(LLMGateway.class);
    private final Map<String, LLMProvider> providers = new ConcurrentHashMap<>();
    private final String activeProvider;

    public LLMGateway(LLMConfig config, LLMConfig localConfig, boolean useLocal) {
        if (useLocal && localConfig != null) {
            this.activeProvider = localConfig.provider();
            registerProvider(new OllamaProvider(localConfig.baseUrl()));
        } else {
            this.activeProvider = config.provider();
            registerProvider(new AnthropicProvider(config.apiKey(), config.baseUrl()));
            if (localConfig != null && localConfig.baseUrl() != null) {
                registerProvider(new OllamaProvider(localConfig.baseUrl()));
            }
        }
    }

    /** Sends a chat request to the active provider. */
    public LLMResponse chat(ChatRequest request) {
        var provider = providers.get(activeProvider);
        if (provider == null) {
            throw new JozException.LLMException("No provider registered for: " + activeProvider);
        }
        log.info("Sending chat request via {} (model: {})", provider.name(), request.model());
        return provider.chat(request);
    }

    /** Returns the active provider name. */
    public String activeProviderName() {
        return activeProvider;
    }

    /** Returns the active provider. */
    public LLMProvider activeProvider() {
        var provider = providers.get(activeProvider);
        if (provider == null) {
            throw new JozException.LLMException("No provider registered for: " + activeProvider);
        }
        return provider;
    }

    private void registerProvider(LLMProvider provider) {
        providers.put(provider.name(), provider);
        log.debug("Registered LLM provider: {}", provider.name());
    }
}
