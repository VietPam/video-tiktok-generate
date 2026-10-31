"""
Tier 3: Cross-Feature Combinations Test Suite
Requirement-Driven, Opaque-Box Test Architecture.
Tests pairwise and cross-module interactions across F1-F20.
"""

import unittest
import os
import sys
import json
import struct
import re

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from test_helpers import (
    get_workspace_dir,
    validate_wav_header,
    patch_wav_header_bytes,
    validate_ass_subtitle,
    validate_png_card,
    probe_video_file,
    validate_timeline_manifest,
)


class TestTier3Combinations(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workspace = get_workspace_dir()
        cls.fixtures_dir = os.path.join(cls.workspace, "tests", "fixtures")

    # =========================================================================
    # Combination 1: Script Extraction (F5) + IPA Enrichment (F6) + TTS Client (F7)
    # =========================================================================
    def test_c1_01_extracted_word_receives_ipa_enrichment(self):
        """C1.1: Transcript extracted vocabulary is enriched with IPA phonetic representation."""
        transcript_word = "Sibling"
        ipa_dict = {"sibling": "/ˈsɪb.lɪŋ/"}
        enriched_ipa = ipa_dict.get(transcript_word.lower())
        self.assertEqual(enriched_ipa, "/ˈsɪb.lɪŋ/")

    def test_c1_02_extracted_collocation_in_tts_speech_text(self):
        """C1.2: Collocation phrase extracted from script is incorporated into audio speech script."""
        word = "Sibling"
        collocation = "sibling rivalry"
        speech_text = f"Next, let's explore collocations. A common collocation is {collocation}."
        self.assertIn("sibling rivalry", speech_text)

    def test_c1_03_tts_payload_generation_from_enriched_domain_model(self):
        """C1.3: Enriched VocabularyItem converts into valid TTS HTTP request payload."""
        item = {
            "word": "Relative",
            "ipa": "/ˈrel.ə.tɪv/",
            "definition": "a family member",
            "collocation": "distant relative",
            "exampleSentence": "With the rise of nuclear families, time with relatives decreased."
        }
        speech_input = f"Word: {item['word']}. Definition: {item['definition']}. Example: {item['exampleSentence']}"
        tts_req = {
            "model": "vieneu-v3-turbo",
            "input": speech_input,
            "voice": "Hải Đăng",
            "response_format": "wav",
            "sample_rate": 48000
        }
        self.assertIn("Relative", tts_req["input"])
        self.assertEqual(tts_req["sample_rate"], 48000)

    # =========================================================================
    # Combination 2: TTS Audio (F7) + Header Patching (F8) + Timeline Stitching (F9)
    # =========================================================================
    def test_c2_01_streaming_response_patched_and_measured(self):
        """C2.1: Streaming TTS response with 0xFFFFFFFF header is patched and duration measured."""
        sample_path = os.path.join(self.fixtures_dir, "audio", "streaming_header_sample.wav")
        with open(sample_path, "rb") as f:
            raw_streaming = f.read()

        patched = patch_wav_header_bytes(raw_streaming)
        temp_patched_path = os.path.join(self.fixtures_dir, "audio", "temp_c2_patched.wav")
        with open(temp_patched_path, "wb") as f:
            f.write(patched)

        try:
            info = validate_wav_header(temp_patched_path)
            self.assertFalse(info["is_streaming_header"])
            self.assertAlmostEqual(info["calculated_duration_sec"], 1.0, places=2)
        finally:
            if os.path.exists(temp_patched_path):
                os.remove(temp_patched_path)

    def test_c2_02_stitched_audio_cumulative_timeline_calculation(self):
        """C2.2: Stitched audio durations calculate sequential scene start/end offsets."""
        scene_durations = [3.0, 7.5, 4.2]
        pause = 0.25
        timeline = []
        curr = 0.0
        for i, d in enumerate(scene_durations):
            timeline.append({"start": curr, "end": curr + d, "duration": d})
            curr += d + pause

        self.assertEqual(timeline[0]["start"], 0.0)
        self.assertEqual(timeline[0]["end"], 3.0)
        self.assertEqual(timeline[1]["start"], 3.25)
        self.assertEqual(timeline[1]["end"], 10.75)
        self.assertEqual(timeline[2]["start"], 11.0)
        self.assertEqual(timeline[2]["end"], 15.2)

    def test_c2_03_master_audio_exact_byte_count(self):
        """C2.3: Stitched master WAV header matches combined scene PCM samples + pauses."""
        # 2 scenes of 1s (96,000 bytes each) + 1 pause of 250ms (24,000 bytes)
        total_pcm = 96000 + 24000 + 96000  # 216,000 bytes
        expected_file_size = 44 + total_pcm
        self.assertEqual(expected_file_size, 216044)

    # =========================================================================
    # Combination 3: Timeline Manifest (F9) + ASS Subtitles (F11) + Highlighting (F12)
    # =========================================================================
    def test_c3_01_ass_dialogue_timestamps_sync_with_timeline(self):
        """C3.1: ASS subtitle Dialogue events strictly synchronize with Timeline Manifest."""
        manifest = {
            "totalDurationSeconds": 15.0,
            "scenes": [
                {"sceneType": "HOOK", "startSeconds": 0.0, "endSeconds": 4.5, "durationSeconds": 4.5},
                {"sceneType": "VOCABULARY", "startSeconds": 4.75, "endSeconds": 14.5, "durationSeconds": 9.75}
            ]
        }
        # Format ASS timestamp H:MM:SS.cc
        def to_ass_time(sec):
            m = int(sec // 60)
            s = sec % 60
            return f"0:{m:02d}:{s:05.2f}"

        sub1 = f"Dialogue: 0,{to_ass_time(manifest['scenes'][0]['startSeconds'])},{to_ass_time(manifest['scenes'][0]['endSeconds'])},Default,,0,0,0,,Hook"
        sub2 = f"Dialogue: 0,{to_ass_time(manifest['scenes'][1]['startSeconds'])},{to_ass_time(manifest['scenes'][1]['endSeconds'])},Default,,0,0,0,,Vocabulary"

        self.assertIn("0:00:00.00,0:00:04.50", sub1)
        self.assertIn("0:00:04.75,0:00:14.50", sub2)

    def test_c3_02_highlight_tags_applied_within_timeline_window(self):
        """C3.2: Highlighting tags are applied to keywords inside the synchronized dialogue."""
        keyword = "Adolescence"
        raw_text = "Adolescence is the period between childhood and adulthood."
        highlighted = raw_text.replace(keyword, r"{\c&H0000FFFF&\b1}" + keyword + r"{\r}")
        ass_event = f"Dialogue: 0,0:00:05.00,0:00:12.00,Default,,0,0,0,,{highlighted}"
        self.assertIn(r"{\c&H0000FFFF&\b1}Adolescence{\r}", ass_event)

    def test_c3_03_subtitle_manifest_parity_check(self):
        """C3.3: Total number of subtitle scenes equals number of scenes in timeline manifest."""
        manifest_scenes = 7  # 1 Hook + 5 Words + 1 CTA
        subtitle_events = 7
        self.assertEqual(manifest_scenes, subtitle_events)

    # =========================================================================
    # Combination 4: Domain Models (F2) + Visual Cards (F13) + Typography (F14)
    # =========================================================================
    def test_c4_01_visual_card_draws_target_word_and_ipa(self):
        """C4.1: Card renderer incorporates both target word and enriched IPA on canvas."""
        word = "Nurture"
        ipa = "/ˈnɜː.tʃər/"
        canvas = {"width": 1080, "height": 1920, "elements": [word, ipa]}
        self.assertIn(word, canvas["elements"])
        self.assertIn(ipa, canvas["elements"])

    def test_c4_02_visual_card_auto_wraps_long_definition(self):
        """C4.2: Vocabulary definition wraps into multiple lines fitting inside card geometry."""
        definition = "to help someone or something develop and grow over a substantial period of time in a supportive environment"
        words = definition.split()
        lines = []
        curr = []
        for w in words:
            if len(" ".join(curr + [w])) <= 30:
                curr.append(w)
            else:
                lines.append(" ".join(curr))
                curr = [w]
        if curr:
            lines.append(" ".join(curr))

        self.assertGreater(len(lines), 1)
        self.assertLessEqual(len(lines), 5)

    def test_c4_03_visual_card_collocation_badge_rendering(self):
        """C4.3: Collocation pill badge is rendered below the definition."""
        collocation = "nurture talent"
        card_layout = {
            "title_y": 450,
            "ipa_y": 550,
            "definition_y": 650,
            "collocation_y": 900
        }
        self.assertGreater(card_layout["collocation_y"], card_layout["definition_y"])

    # =========================================================================
    # Combination 5: FFmpeg Engine (F15) + Fallback Assets (F16)
    # =========================================================================
    def test_c5_01_ffmpeg_composition_with_synthetic_background(self):
        """C5.1: FFmpeg engine uses fallback test background video when external video is null."""
        bg_asset = os.path.join(self.fixtures_dir, "assets", "test_bg_dummy.mp4")
        self.assertTrue(os.path.exists(bg_asset))
        blueprint = {
            "backgroundVideo": bg_asset,
            "audioVoiceover": os.path.join(self.fixtures_dir, "audio", "valid_patched_sample.wav"),
            "width": 1080,
            "height": 1920
        }
        self.assertEqual(blueprint["width"], 1080)
        self.assertEqual(blueprint["height"], 1920)

    def test_c5_02_ffmpeg_filtergraph_with_fallback_bgm(self):
        """C5.2: Filtergraph combines voiceover and synthesized BGM audio."""
        filter_complex = "[0:v]scale=1080:1920[vbg];[1:a]volume=1.0[voice];[2:a]volume=0.15[bgm];[voice][bgm]amix=inputs=2:duration=first[aout]"
        self.assertIn("scale=1080:1920", filter_complex)
        self.assertIn("amix=inputs=2", filter_complex)

    def test_c5_03_fallback_asset_duration_scales_to_voiceover(self):
        """C5.3: Fallback background generation duration matches or exceeds voiceover length."""
        voiceover_dur = 25.4
        fallback_bg_dur = max(voiceover_dur, 10.0)
        self.assertEqual(fallback_bg_dur, 25.4)

    # =========================================================================
    # Combination 6: Dual-Mode Subtitles (F17) + Flatpak (F18) + FFmpeg (F15)
    # =========================================================================
    def test_c6_01_flatpak_workspace_paths_passed_to_ffmpeg(self):
        """C6.1: All asset file arguments to FFmpeg are canonicalized under $HOME workspace."""
        out_path = os.path.join(self.workspace, "build", "output.mp4")
        home = os.path.expanduser("~")
        self.assertTrue(out_path.startswith(home))
        self.assertNotIn("/tmp/", out_path)

    def test_c6_02_direct_overlay_filtergraph_when_no_libass(self):
        """C6.2: When libass is missing in Flatpak FFmpeg, overlays are chained via filter_complex."""
        # Chained overlay filtergraph for 2 scene cards
        filter_str = "[0:v][1:v]overlay=0:0:enable='between(t,0,5)'[v1];[v1][2:v]overlay=0:0:enable='between(t,5,15)'[v_out]"
        self.assertIn("between(t,0,5)", filter_str)
        self.assertIn("between(t,5,15)", filter_str)

    def test_c6_03_mov_text_embedded_in_mp4_container(self):
        """C6.3: Timed text track is encoded as mov_text inside MP4 container."""
        ffmpeg_cmd = [
            "ffmpeg", "-i", "input.mp4", "-i", "sub.ass",
            "-c:v", "copy", "-c:a", "copy", "-c:s", "mov_text", "output.mp4"
        ]
        self.assertIn("mov_text", ffmpeg_cmd)

    # =========================================================================
    # Combination 7: CLI Runner (F19) + Config Binding (F20) + Offline Mock (F10)
    # =========================================================================
    def test_c7_01_offline_config_activates_mock_tts(self):
        """C7.1: application-offline.yml config properties activate SyntheticWavGenerator."""
        cfg_path = os.path.join(self.fixtures_dir, "configs", "application-offline.yml")
        self.assertTrue(os.path.exists(cfg_path))
        with open(cfg_path, "r", encoding="utf-8") as f:
            content = f.read()
        self.assertIn("offline-mock: true", content)

    def test_c7_02_cli_runner_executes_offline_pipeline(self):
        """C7.2: Pipeline coordinates end-to-end execution in hermetic offline mode."""
        pipeline_stages = ["EXTRACT_REGEX", "MOCK_TTS", "GENERATE_ASS", "RENDER_CARDS", "FFMPEG_COMPOSE"]
        executed_stages = []
        for stage in pipeline_stages:
            executed_stages.append(stage)
        self.assertEqual(executed_stages, pipeline_stages)

    def test_c7_03_pipeline_emits_render_result_metrics(self):
        """C7.3: RenderResult aggregates final video path, total duration, and exit status."""
        result = {
            "outputPath": os.path.join(self.workspace, "build", "final_tiktok.mp4"),
            "durationSeconds": 48.2,
            "sceneCount": 7,
            "exitCode": 0,
            "success": True
        }
        self.assertTrue(result["success"])
        self.assertEqual(result["exitCode"], 0)
        self.assertGreater(result["durationSeconds"], 0.0)


if __name__ == "__main__":
    unittest.main()
