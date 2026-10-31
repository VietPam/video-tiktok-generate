package com.tiktok.video.script;

import com.tiktok.video.core.contract.ScriptExtractor;
import com.tiktok.video.script.ipa.StandardIpaEnricher;
import com.tiktok.video.script.llm.LlmScriptExtractor;
import com.tiktok.video.script.llm.OpenAiLlmClient;
import com.tiktok.video.script.regex.RegexTranscriptExtractor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.net.URI;

@SpringBootApplication
public class ScriptServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScriptServiceApplication.class, args);
    }

    @Bean
    public ScriptExtractor scriptExtractor(
            @Value("${tiktok.llm.enabled:false}") boolean llmEnabled,
            @Value("${tiktok.llm.base-url:http://localhost:11434/v1/chat/completions}") String llmBaseUrl,
            @Value("${tiktok.llm.api-key:dummy}") String apiKey,
            @Value("${tiktok.llm.model:llama3}") String llmModel) {
        RegexTranscriptExtractor fallback = new RegexTranscriptExtractor(new StandardIpaEnricher());
        if (llmEnabled) {
            LlmScriptExtractor primary = new LlmScriptExtractor(new OpenAiLlmClient(URI.create(llmBaseUrl), apiKey, llmModel));
            return new CompositeScriptExtractor(primary, fallback, true);
        }
        return new CompositeScriptExtractor(fallback);
    }
}
