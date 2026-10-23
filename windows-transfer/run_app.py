"""打包用的入口（PyInstaller 要從這裡開始，才能正確處理套件內的 import）。"""
from phoneai_transfer.app import main

if __name__ == "__main__":
    main()
