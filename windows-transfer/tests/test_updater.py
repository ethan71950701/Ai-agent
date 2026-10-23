import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from phoneai_transfer import updater, version  # noqa: E402


class Updater(unittest.TestCase):
    def test_parse_release_finds_exe(self):
        data = {"name": "windows-42", "assets": [
            {"name": "other.zip", "browser_download_url": "x"},
            {"name": version.ASSET_NAME, "browser_download_url": "https://example/exe"}]}
        self.assertEqual((42, "https://example/exe"), updater.parse_release(data))

    def test_parse_release_without_exe(self):
        self.assertEqual((None, None), updater.parse_release({"name": "windows-3", "assets": []}))

    def test_is_newer(self):
        self.assertTrue(updater.is_newer(5, 4))
        self.assertFalse(updater.is_newer(4, 4))
        self.assertFalse(updater.is_newer(None, 0))


if __name__ == "__main__":
    unittest.main()
