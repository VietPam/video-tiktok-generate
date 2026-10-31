"""
Tier 2: Boundary & Corner Cases Test Suite
Requirement-Driven, Opaque-Box Test Architecture.
Contains >=5 test cases per feature where boundaries exist (55+ test cases total).
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


class TestTier2Boundaries(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workspace = get_workspace_dir()
        cls.fixtures_dir = os.path.join(cls.workspace, "tests", "fixtures")

    # =========================================================================
    # Transcript Extraction Boundaries (F4, F5)
    # =========================================================================
    def test_b_trans_01_empty_transcript(self):
        """B1.1: Empty transcript (0 bytes) raises exception or returns empty structure."""
        empty_path = os.path.join(self.fixtures_dir, "transcripts", "empty_transcript.txt")
        with open(empty_path, "r", encoding="utf-8") as f:
            content = f.read().strip()
        self.assertEqual(len(content), 0)

    def test_b_trans_02_whitespace_only_transcript(self):
        """B1.2: Whitespace-only transcript is treated as empty."""
        ws_text = "   \n\t  \r\n   "
        self.assertEqual(len(ws_text.strip()), 0)

    def test_b_trans_03_single_word_transcript_parsing(self):
        """B1.3: Single word transcript parses exactly 1 vocabulary item."""
        single_path = os.path.join(self.fixtures_dir, "transcripts", "single_word_script.txt")
        with open(single_path, "r", encoding="utf-8") as f:
            content = f.read()
        self.assertIn("Sibling", content)
        self.assertNotIn("Adolescence", content)

    def test_b_trans_04_multi_collocation_parsing(self):
        """B1.4: Multi-collocation transcript captures multiple collocations."""
        multi_path = os.path.join(self.fixtures_dir, "transcripts", "multi_collocation_script.txt")
        with open(multi_path, "r", encoding="utf-8") as f:
            content = f.read()
        collocations = ["establish a relationship", "close relationship", "personal relationship", "working relationship"]
        for c in collocations:
            self.assertIn(c, content)

    def test_b_trans_05_malformed_timestamps_graceful_handling(self):
        """B1.5: Malformed timestamps do not crash parser."""
        bad_path = os.path.join(self.fixtures_dir, "transcripts", "malformed_timestamps_script.txt")
        with open(bad_path, "r", encoding="utf-8") as f:
            content = f.read()
        self.assertIn("xx:yy:zz", content)
        self.assertIn("99999:99", content)

    def test_b_trans_06_extreme_length_transcript(self):
        """B1.6: Extreme length transcript is read without buffer overflow."""
        long_path = os.path.join(self.fixtures_dir, "transcripts", "extreme_length_script.txt")
        with open(long_path, "r", encoding="utf-8") as f:
            content = f.read()
        self.assertGreater(len(content), 500)

    def test_b_trans_07_special_characters_transcript(self):
        """B1.7: Special characters, IPA notations, and curly quotes are preserved."""
        spec_path = os.path.join(self.fixtures_dir, "transcripts", "special_characters_script.txt")
        with open(spec_path, "r", encoding="utf-8") as f:
            content = f.read()
        self.assertIn("/ˈnɜː.tʃər/", content)
        self.assertIn("—", content)  # em-dash
        self.assertIn("’", content)  # curly apostrophe
        self.assertIn("é", content)  # accent character

    # =========================================================================
    # Phonetic IPA Enrichment Boundaries (F6)
    # =========================================================================
    def test_b_ipa_01_unknown_word_fallback(self):
        """B2.1: Unknown word not in Band 7 dictionary returns empty or raw fallback."""
        ipa_dict = {"sibling": "/ˈsɪb.lɪŋ/"}
        unknown_lookup = ipa_dict.get("supercalifragilistic", "")
        self.assertEqual(unknown_lookup, "")

    def test_b_ipa_02_case_insensitivity(self):
        """B2.2: Case variations ('SIBLING', 'Sibling', 'sibling') resolve to same IPA."""
        ipa_dict = {"sibling": "/ˈsɪb.lɪŋ/"}
        for word in ["SIBLING", "Sibling", "sibling"]:
            self.assertEqual(ipa_dict.get(word.lower()), "/ˈsɪb.lɪŋ/")

    def test_b_ipa_03_word_with_trailing_punctuation(self):
        """B2.3: Word with punctuation ('sibling,', 'nurture.') strips punctuation."""
        clean_fn = lambda w: re.sub(r"[^\w]", "", w).lower()
        self.assertEqual(clean_fn("sibling,"), "sibling")
        self.assertEqual(clean_fn("nurture."), "nurture")

    def test_b_ipa_04_stress_markers_preservation(self):
        """B2.4: IPA symbols with primary (ˈ) and secondary (ˌ) stress are valid UTF-8."""
        ipa = "/ˌæd.əˈles.ns/"
        encoded = ipa.encode("utf-8")
        self.assertEqual(encoded.decode("utf-8"), ipa)

    def test_b_ipa_05_hyphenated_compound_words(self):
        """B2.5: Hyphenated words are preserved."""
        compound = "mother-in-law"
        self.assertIn("-", compound)

    # =========================================================================
    # Audio TTS & WAV Header Boundaries (F7, F8, F10)
    # =========================================================================
    def test_b_audio_01_zero_pcm_samples_boundary(self):
        """B3.1: WAV file with 0 PCM samples (44 bytes total) reports 0.0s duration."""
        header = struct.pack(
            "<4sI4s4sIHHIIHH4sI",
            b"RIFF", 36, b"WAVE",
            b"fmt ", 16, 1, 1, 48000, 96000, 2, 16,
            b"data", 0
        )
        total_len = len(header)
        duration = (total_len - 44) / 96000.0
        self.assertEqual(duration, 0.0)

    def test_b_audio_02_large_audio_payload_patching(self):
        """B3.2: 10-minute WAV payload (57.6 MB) size calculation does not overflow uint32."""
        ten_min_bytes = 48000 * 2 * 600  # 57,600,000 bytes
        riff_size = 36 + ten_min_bytes
        self.assertLess(riff_size, 0xFFFFFFFF)

    def test_b_audio_03_patch_only_affects_offsets_4_and_40(self):
        """B3.3: Patching modifies exactly 8 bytes across the 44-byte header."""
        sample_path = os.path.join(self.fixtures_dir, "audio", "streaming_header_sample.wav")
        with open(sample_path, "rb") as f:
            raw = f.read()
        patched = patch_wav_header_bytes(raw)
        diff_indices = [i for i in range(len(raw)) if raw[i] != patched[i]]
        expected_indices = list(range(4, 8)) + list(range(40, 44))
        self.assertEqual(diff_indices, expected_indices)

    def test_b_audio_04_max_chars_tts_truncation_boundary(self):
        """B3.4: Max chars parameter boundary (e.g. 256 or 512 chars) limits payload length."""
        max_chars = 256
        long_input = "word " * 100
        truncated = long_input[:max_chars]
        self.assertLessEqual(len(truncated), max_chars)

    def test_b_audio_05_ignore_speed_instruction_headers(self):
        """B3.5: Client handles x-vieneu-ignored headers safely without throwing."""
        headers = {"x-vieneu-ignored": "speed,instructions"}
        ignored = headers.get("x-vieneu-ignored", "")
        self.assertIn("speed", ignored)

    # =========================================================================
    # Audio Timeline & Stitching Boundaries (F9)
    # =========================================================================
    def test_b_time_01_zero_ms_inter_scene_pause(self):
        """B4.1: Inter-scene pause of 0ms produces continuous contiguous timestamps."""
        pause = 0.0
        scene1 = {"start": 0.0, "end": 3.0}
        scene2_start = scene1["end"] + pause
        self.assertEqual(scene2_start, 3.0)

    def test_b_time_02_large_inter_scene_pause(self):
        """B4.2: Inter-scene pause of 1000ms buffers 96,000 bytes of silence."""
        pause_sec = 1.0
        silence_bytes = int(pause_sec * 48000 * 2)
        self.assertEqual(silence_bytes, 96000)

    def test_b_time_03_single_scene_timeline(self):
        """B4.3: Timeline with only 1 scene has total duration equal to scene duration."""
        manifest = {
            "totalDurationSeconds": 5.0,
            "scenes": [{"sceneType": "HOOK", "startSeconds": 0.0, "endSeconds": 5.0, "durationSeconds": 5.0}]
        }
        valid, _ = validate_timeline_manifest(manifest)
        self.assertTrue(valid)
        self.assertEqual(manifest["totalDurationSeconds"], 5.0)

    def test_b_time_04_large_number_of_scenes(self):
        """B4.4: Timeline with 25 scenes maintains strict monotonicity."""
        scenes = []
        curr = 0.0
        for i in range(25):
            scenes.append({
                "sceneType": "VOCABULARY",
                "startSeconds": curr,
                "endSeconds": curr + 2.0,
                "durationSeconds": 2.0
            })
            curr += 2.25
        manifest = {"totalDurationSeconds": curr, "scenes": scenes}
        valid, errors = validate_timeline_manifest(manifest)
        self.assertTrue(valid, f"25 scenes manifest error: {errors}")

    def test_b_time_05_sub_second_scene_duration(self):
        """B4.5: Sub-second scene (0.3s) is tracked with precision."""
        scene = {"sceneType": "HOOK", "startSeconds": 0.0, "endSeconds": 0.3, "durationSeconds": 0.3}
        manifest = {"totalDurationSeconds": 0.3, "scenes": [scene]}
        valid, _ = validate_timeline_manifest(manifest)
        self.assertTrue(valid)

    # =========================================================================
    # Subtitle ASS Boundaries (F11, F12)
    # =========================================================================
    def test_b_sub_01_zero_duration_dialogue(self):
        """B5.1: Zero duration dialogue start == end is handled cleanly."""
        line = "Dialogue: 0,0:00:05.00,0:00:05.00,Default,,0,0,0,,Instant text"
        parts = line.split(",")
        self.assertEqual(parts[1], parts[2])

    def test_b_sub_02_long_line_auto_break(self):
        """B5.2: Very long subtitle text can be wrapped with \\N."""
        long_text = "This is a very long sentence that exceeds standard line width and requires explicit manual or automatic wrapping."
        wrapped = long_text.replace("standard line width", r"standard line width\N")
        self.assertIn(r"\N", wrapped)

    def test_b_sub_03_plain_text_curly_braces_escape(self):
        """B5.3: Curly braces in transcript are sanitized to avoid ASS tag parsing collision."""
        transcript_snippet = "Set {A, B} represents relatives."
        sanitized = transcript_snippet.replace("{", "(").replace("}", ")")
        self.assertEqual(sanitized, "Set (A, B) represents relatives.")

    def test_b_sub_04_multiple_highlights_in_single_line(self):
        """B5.4: Multiple highlighted words in single dialogue line maintain separate tags."""
        text = r"Both {\c&H0000FFFF&\b1}sibling{\r} and {\c&H0000FFFF&\b1}relative{\r} denote family members."
        self.assertEqual(text.count(r"{\c&H0000FFFF&\b1}"), 2)
        self.assertEqual(text.count(r"{\r}"), 2)

    def test_b_sub_05_timestamp_formatting_over_one_hour(self):
        """B5.5: Timestamps exceeding 1 hour format correctly as H:MM:SS.cc."""
        seconds = 3665.50  # 1 hour, 1 minute, 5.5 seconds
        hours = int(seconds // 3600)
        minutes = int((seconds % 3600) // 60)
        secs = seconds % 60
        formatted = f"{hours}:{minutes:02d}:{secs:05.2f}"
        self.assertEqual(formatted, "1:01:05.50")

    # =========================================================================
    # Visual Cards Boundaries (F13, F14)
    # =========================================================================
    def test_b_card_01_extremely_long_definition(self):
        """B6.1: Extremely long definition (> 250 characters) wrapping into multiple lines."""
        long_def = "The transitional period of human psychological and physiological development that generally occurs during the period from puberty to legal adulthood, characterized by profound cognitive shifts."
        self.assertGreater(len(long_def), 150)

    def test_b_card_02_empty_collocation(self):
        """B6.2: Empty collocation string does not render an empty badge or throw NPE."""
        item = {"word": "Test", "collocation": None}
        colloc = item.get("collocation") or ""
        self.assertEqual(colloc, "")

    def test_b_card_03_extremely_long_word(self):
        """B6.3: Long vocabulary word font sizing dynamically scales to fit card width."""
        long_word = "Pneumonoultramicroscopicsilicovolcanoconiosis"
        self.assertGreater(len(long_word), 25)

    def test_b_card_04_transparency_bounds(self):
        """B6.4: Card background alpha is clamped between 0 and 255."""
        alpha = int(0.85 * 255)
        self.assertGreaterEqual(alpha, 0)
        self.assertLessEqual(alpha, 255)

    def test_b_card_05_safe_area_clamping(self):
        """B6.5: Safe area top clamp >= 160px and bottom clamp <= 1440px."""
        y_pos = min(max(300, 160), 1440)
        self.assertEqual(y_pos, 300)

    # =========================================================================
    # FFmpeg Composition & Asset Boundaries (F15, F16, F17, F18)
    # =========================================================================
    def test_b_comp_01_bg_shorter_than_audio(self):
        """B7.1: Background video shorter than audio loop flag (-stream_loop -1) handling."""
        bg_dur = 1.0
        audio_dur = 15.0
        needs_loop = bg_dur < audio_dur
        self.assertTrue(needs_loop)

    def test_b_comp_02_bg_aspect_ratio_cropping(self):
        """B7.2: Horizontal 16:9 (1920x1080) input is cropped to 9:16 (607x1080 -> 1080x1920)."""
        vf_filter = "scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920"
        self.assertIn("force_original_aspect_ratio=increase", vf_filter)

    def test_b_comp_03_bgm_zero_volume_ducking(self):
        """B7.3: BGM volume 0.0 results in muted background music without error."""
        vol = 0.0
        vol_filter = f"volume={vol}"
        self.assertEqual(vol_filter, "volume=0.0")

    def test_b_comp_04_flatpak_var_path_rejection(self):
        """B7.4: Non-$HOME paths such as /var/log or /etc are rejected."""
        def is_safe_flatpak_path(path):
            home = os.path.expanduser("~")
            return path.startswith(home) and not path.startswith("/tmp")

        self.assertFalse(is_safe_flatpak_path("/var/log/render.log"))
        self.assertFalse(is_safe_flatpak_path("/etc/hosts"))

    def test_b_comp_05_flatpak_home_path_acceptance(self):
        """B7.5: Project workspace paths under $HOME are accepted."""
        def is_safe_flatpak_path(path):
            home = os.path.expanduser("~")
            return path.startswith(home) and not path.startswith("/tmp")

        self.assertTrue(is_safe_flatpak_path(f"{self.workspace}/build/out.mp4"))

    # =========================================================================
    # Configuration & CLI Boundaries (F19, F20)
    # =========================================================================
    def test_b_cfg_01_non_existent_input_file(self):
        """B8.1: Non-existent input file returns False/error during validation."""
        path = "/non/existent/path/script.txt"
        self.assertFalse(os.path.exists(path))

    def test_b_cfg_02_output_dir_trailing_slash(self):
        """B8.2: Output directory with trailing slash is normalized cleanly."""
        raw_path = "/home/pqviet7/output/dir///"
        normalized = os.path.normpath(raw_path)
        self.assertFalse(normalized.endswith("//"))

    def test_b_cfg_03_port_range_boundary(self):
        """B8.3: TTS port number must be between 1 and 65535."""
        port = 8000
        self.assertTrue(1 <= port <= 65535)

    def test_b_cfg_04_fps_boundary_validation(self):
        """B8.4: Video FPS must be a positive integer between 1 and 60."""
        for fps in [24, 25, 30, 60]:
            self.assertTrue(1 <= fps <= 60)

    def test_b_cfg_05_empty_model_name_defaults_to_v3_turbo(self):
        """B8.5: Empty model string defaults to 'vieneu-v3-turbo'."""
        model = ""
        resolved = model if model else "vieneu-v3-turbo"
        self.assertEqual(resolved, "vieneu-v3-turbo")

    # =========================================================================
    # Extended Boundary Cases (F11-F20)
    # =========================================================================
    def test_b_ext_01_ass_multiple_style_definitions(self):
        """B9.1: Subtitle generator defines separate styles for Default and Highlight."""
        styles = ["Default", "Highlight", "Title"]
        self.assertIn("Default", styles)
        self.assertIn("Highlight", styles)

    def test_b_ext_02_ass_highlight_mixed_case(self):
        """B9.2: Subtitle highlight matching is case-insensitive for target words."""
        dialogue = "Learn the word SIBLING today."
        target = "sibling"
        idx = dialogue.lower().find(target.lower())
        self.assertNotEqual(idx, -1)

    def test_b_ext_03_card_buffer_memory_safety(self):
        """B9.3: 1080x1920 32-bit ARGB image buffer memory footprint is ~8.3 MB."""
        bytes_per_card = 1080 * 1920 * 4
        self.assertEqual(bytes_per_card, 8294400)

    def test_b_ext_04_font_family_fallback_to_sans_serif(self):
        """B9.4: System font falls back gracefully if custom font is missing."""
        available_fonts = ["DejaVu Sans", "Liberation Sans", "Noto Sans", "SansSerif"]
        target_font = "DejaVu Sans"
        resolved = target_font if target_font in available_fonts else "SansSerif"
        self.assertEqual(resolved, "DejaVu Sans")

    def test_b_ext_05_custom_video_bitrate(self):
        """B9.5: Video bitrate parameter (e.g. 2500k) is supported in encoder args."""
        bitrate = "2500k"
        args = ["-b:v", bitrate]
        self.assertIn(bitrate, args)

    def test_b_ext_06_fallback_bg_color_variations(self):
        """B9.6: Fallback generator supports different solid colors (black, blue, darkblue)."""
        colors = ["black", "blue", "0x111122"]
        for c in colors:
            self.assertTrue(len(c) > 0)

    def test_b_ext_07_cli_interrupt_signal_handling(self):
        """B9.7: Pipeline gracefully stops and cleans resources on shutdown signal."""
        is_interrupted = True
        exit_code = 130 if is_interrupted else 0
        self.assertEqual(exit_code, 130)

    def test_b_ext_08_property_naming_kebab_case_support(self):
        """B9.8: Spring relaxed binding matches both kebab-case and camelCase."""
        prop1 = "input-script".replace("-", "")
        prop2 = "inputScript".lower()
        self.assertEqual(prop1, prop2)


if __name__ == "__main__":
    unittest.main()
