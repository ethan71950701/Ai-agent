import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from phoneai_transfer import updater, version  # noqa: E402


class Updater(unittest.TestCase):
    def test_parse_release_finds_exe(self):
        data = {"tag_name": "build-1042-abc1234", "assets": [
            {"name": "PhoneAI.apk", "browser_download_url": "x"},
            {"name": version.ASSET_NAME, "browser_download_url": "https://example/exe"}]}
        self.assertEqual((1042, "https://example/exe"), updater.parse_release(data))

    def test_parse_release_without_exe(self):
        self.assertEqual((None, None), updater.parse_release({"tag_name": "build-1043-abc", "assets": []}))

    def test_parse_release_other_tag(self):
        self.assertEqual((None, None), updater.parse_release({"tag_name": "windows-latest", "assets": [
            {"name": version.ASSET_NAME, "browser_download_url": "u"}]}))

    def test_pick_latest_skips_releases_without_exe(self):
        exe = [{"name": version.ASSET_NAME, "browser_download_url": "u"}]
        rels = [{"tag_name": "build-1050-c", "assets": []},                       # 最新但沒有 exe
                {"tag_name": "build-1049-b", "assets": exe},
                {"tag_name": "build-1040-a", "assets": [{"name": version.ASSET_NAME, "browser_download_url": "old"}]},
                {"tag_name": "build-1060-d", "prerelease": True, "assets": exe}]   # 預發行不算
        self.assertEqual((1049, "u"), updater.pick_latest(rels))
        self.assertEqual((None, None), updater.pick_latest([]))

    def test_is_newer(self):
        self.assertTrue(updater.is_newer(5, 4))
        self.assertFalse(updater.is_newer(4, 4))
        self.assertFalse(updater.is_newer(None, 0))


if __name__ == "__main__":
    unittest.main()
