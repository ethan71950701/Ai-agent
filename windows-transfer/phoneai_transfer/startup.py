"""開機自動啟動：寫在目前使用者的「執行」登錄檔項目（不需要系統管理員權限）。只有打包後的 exe 才有意義。"""
import os
import sys

RUN_KEY = r"Software\Microsoft\Windows\CurrentVersion\Run"
NAME = "PhoneAI互傳"


def supported():
    return os.name == "nt" and getattr(sys, "frozen", False)


def is_enabled():
    if not supported():
        return False
    import winreg
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY) as k:
            winreg.QueryValueEx(k, NAME)
            return True
    except OSError:
        return False


def set_enabled(on):
    """開：登錄成「開機執行 --tray」（啟動後直接縮到右下角）；關：移除。"""
    if not supported():
        return
    import winreg
    with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY, 0, winreg.KEY_SET_VALUE) as k:
        if on:
            winreg.SetValueEx(k, NAME, 0, winreg.REG_SZ, f'"{sys.executable}" --tray')
        else:
            try:
                winreg.DeleteValue(k, NAME)
            except OSError:
                pass
