package com.tiktok.video.script.llm;

@FunctionalInterface
public interface LlmClient {
    /**
     * Generates text/JSON completion from system and user prompts.
     *
     * @param systemPrompt instruction/schema prompt
     * @param userPrompt   user input / transcript text
     * @return raw response string
     * @throws LlmException if network, timeout, or HTTP error occurs
     */
    String generate(String systemPrompt, String userPrompt) throws LlmException;
}
