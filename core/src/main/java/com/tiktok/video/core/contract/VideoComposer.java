package com.tiktok.video.core.contract;

import com.tiktok.video.core.exception.RenderException;
import com.tiktok.video.core.model.RenderBlueprint;
import com.tiktok.video.core.model.RenderResult;

/**
 * Service contract for video composition, orchestrating FFmpeg CLI via ProcessBuilder
 * to combine background video, visual card PNG overlays, mixed audio, and subtitles.
 */
public interface VideoComposer {

    /**
     * Composes all media assets described in the blueprint into a finished MP4 video.
     *
     * @param blueprint container holding paths to all media assets, timeline, and rendering settings
     * @return render execution result detailing status, duration, file size, and output path
     * @throws RenderException if FFmpeg process execution fails or output file verification fails
     * @throws IllegalArgumentException if blueprint is null
     */
    RenderResult composeVideo(RenderBlueprint blueprint) throws RenderException;
}
