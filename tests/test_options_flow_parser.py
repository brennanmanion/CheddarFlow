import json
import sys
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
SERVICE_DIR = REPO_ROOT / "service"
sys.path.insert(0, str(SERVICE_DIR))

from options_flow import parse_numeric_with_suffix, parse_options_row_html  # noqa: E402


FIXTURE_PATH = REPO_ROOT / "tests" / "fixtures" / "options_flow_live_samples.json"


class OptionsFlowParserTests(unittest.TestCase):
    def test_parse_numeric_with_suffix_handles_decimal_suffixes(self) -> None:
        self.assertEqual(parse_numeric_with_suffix("$1.5M"), 1_500_000.0)
        self.assertEqual(parse_numeric_with_suffix("2.25K"), 2_250.0)
        self.assertEqual(parse_numeric_with_suffix("3B"), 3_000_000_000.0)

    def test_live_samples_replay(self) -> None:
        samples = json.loads(FIXTURE_PATH.read_text(encoding="utf-8"))
        self.assertGreater(len(samples), 0)

        for sample in samples:
            with self.subTest(raw_event_id=sample["raw_event_id"]):
                parsed = parse_options_row_html(sample["source_html"]).to_dict()
                expected = sample["expected"]
                for key, expected_value in expected.items():
                    self.assertEqual(parsed[key], expected_value, key)


if __name__ == "__main__":
    unittest.main()
