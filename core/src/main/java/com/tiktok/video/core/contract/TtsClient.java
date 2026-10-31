package com.tiktok.video.core.contract;

import com.tiktok.video.core.exception.TtsException;

/**
 * Client interface for synthesizing spoken text into audio waveforms.
 */
@FunctionalInterface
public interface TtsClient {

    /**
     * Synthesizes audio for the given speech request.
     *
     * @param request speech synthesis request containing text, voice, model, format
     * @return raw byte array of synthesized audio (e.g. 48kHz mono 16-bit PCM WAV)
     * @throws TtsException if network communication fails, server returns error status, or audio cannot be generated
     * @throws IllegalArgumentException if request is null
     */
    byte[] synthesize(TtsRequest request) throws TtsException;
}
