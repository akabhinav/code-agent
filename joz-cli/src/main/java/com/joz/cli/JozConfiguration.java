package com.joz.cli;

import com.joz.common.config.JozConfig;
import com.joz.llm.LLMGateway;
import com.joz.persistence.ConfigStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/** Spring configuration for wiring up the agent components. */
@Configuration
public class JozConfiguration {

    @Bean
    public JozConfig jozConfig() {
        var globalConfigDir = Path.of(System.getProperty("user.home"), ".joz");
        var projectRoot = Path.of("").toAbsolutePath();
        var configStore = new ConfigStore(globalConfigDir, projectRoot);
        return configStore.load();
    }

    @Bean
    public LLMGateway llmGateway(JozConfig config) {
        return new LLMGateway(config.llm(), config.local(), false);
    }
}
