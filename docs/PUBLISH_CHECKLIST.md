# 公開 repo 之前的清單（2026-10-18 清查結果與建議做法）

> ✅ **2026-10-30：這個 repo 已經公開**（使用者確認）。當天在這個 repo 的全部 git 歷史掃過常見的 Key 格式（`AIza`、`sk-ant-`、`ghp_`、`github_pat_`、`AKIA`、`xox*`、私鑰區塊），沒有命中；歷史裡也沒有新增過 `*.keystore`／`*.jks`／`*.key`／`.env`。下面的清查表與「私人 repo」的說法是公開以前的狀態。
> ⚠️ **2026-10-30 備註：** 這份是 2026-10-18～20 的清查紀錄。其中提到的 `scripts/audit_secrets.py` 已隨 Termux 腳本一起刪除；「簽名金鑰」一節（用 GitHub Secrets 設定 4 個值、`gen-keystore.yml` 產生金鑰）仍然有效，工作流程的錯誤訊息也指向它。清查結果表是當時的狀態，公開前請重新掃一次（`git log -p` 搜尋金鑰格式）。

> 狀態（2026-10-20 更新）：簽名金鑰已移出 repo（改用 GitHub Secrets＋Release 發布，見下面「簽名金鑰」）、repo 位置不再寫死；其餘（授權、作者 Email、文件整理、建新 repo）還沒做。目前這個 repo 仍是私人的。
> 清查工具：`python scripts/audit_secrets.py --words 你的名字,你的Wi-Fi名稱`（只讀，可重複執行；離線測試涵蓋它）。

## 清查結果（對象：目前的私人 repo，含全部歷史）
| 項目 | 結果 | 說明 |
|---|---|---|
| API Key / 權杖 / 私鑰 | ✅ 沒有 | 工作樹與全部歷史都掃過（`sk-ant-`、`ghp_`、`github_pat_`、`AKIA`、私鑰區塊、`xox*`、`AIza*`） |
| 截圖、紀錄檔、APK、模型檔、`.env` | ✅ 沒有被提交過 | 歷史上出現過的風險檔名只有 `android-overlay/debug.keystore` |
| 檔案內容裡的個人資訊（名字、Email、裝置型號、Wi-Fi 名稱） | ✅ 沒找到 | 搜尋字眼由 `--words` 指定，沒有搜的就不知道，公開前請把你在意的字眼都放進去再跑一次 |
| `debug.keystore`（密碼 `android` 寫在 `app/build.gradle`） | 🟡 **已從工作樹移除、改用 Secrets**；舊歷史裡仍有舊金鑰（所以公開要用全新的 repo，並確認新 repo 沒有帶歷史） | 公開等於把簽名金鑰交給別人，別人可以做出簽名一樣的假 App；這個 App 有無障礙與 root 相關能力，風險不小 |
| 提交作者含真實姓名與 Email（63 個提交） | ❌ **必須處理** | 公開後永久顯示在每個提交上 |
| `LICENSE` | ⚠️ 沒有 | 沒有授權，別人預設不能合法使用 |
| 文件內容 | ⚠️ 建議整理 | `HANDOFF.md`/`CLAUDE.md`/`PROMPT_FOR_NEXT_AI.md` 含大量個人使用過程；`docs/archive/ROADMAP_OVERLAY.md` 描述了反編譯他人 App（LiveBridge、SystemUI）做研究，只有描述沒有程式碼，公開前請自己斟酌 |

## 建議做法：另建一個「乾淨的公開 repo」（不要動現在這個私人 repo 的歷史）
1. **先決定授權**（MIT 或 Apache-2.0 都常見；scrcpy 是 Apache-2.0，我們只下載它的伺服器檔、沒有收進 repo）。
2. **換簽名金鑰**：在雲端產生新金鑰庫，**不放進 repo**，以 GitHub Actions 的 Secret 提供（base64），`build.gradle` 改成讀環境變數。代價：新舊簽名不同，**你的手機要把 App 解除安裝一次**再裝新版（無障礙等設定會掉，`overlay_install.sh` 會重開）。
3. **用現在的檔案（移除 `debug.keystore`）建一個全新的 repo**，只有一個起始提交，作者用 GitHub 的 noreply 信箱（`<編號>+<帳號>@users.noreply.github.com`）。舊歷史不帶過去。
4. 在新 repo 跑 `audit_secrets.py`，確認全部 ✅（LICENSE 加了之後 ⚠️ 會消失）。
5. 整理文件：決定哪些個人使用過程要留；`docs/` 的研究紀錄裡你不想公開的細節先刪。
6. 公開之前再檢查一次 GitHub 帳號設定裡的「隱藏我的 Email」。

## 為什麼不直接改寫現在的歷史
改寫歷史（`filter-repo`、BFG）要對所有分支與已合併的 PR 動手，出錯很難救，而且你的手機與 Actions 都依賴現在的 `main`。另建新 repo 對現有流程零風險。


## 簽名金鑰（Secrets）與更新方式（2026-10-20）
APK 由 GitHub Actions 編譯，**用 Secrets 裡的金鑰簽名**（簽名固定，之後才能直接覆蓋安裝）；金鑰庫不進 repo（`.gitignore` 擋了 `*.keystore`、`*.jks`）。
編譯成功後在 `main` 上自動發布成 **GitHub Release**（只留最近 10 筆）；App 的「檢查並更新」與 `update_app.py` 都從「最新的 Release」下載，**公開的 repo 不需要權杖**（私人 repo 才需要唯讀權杖）。

### 第一次設定（repo 還是私人的時候做；換新 repo 也要重做一次）
1. GitHub → Actions → 左邊選「Generate signing key」→ Run workflow。跑完（約 1 分鐘）點進那次執行，最下面 Artifacts 下載 `signing-key`（zip，裡面 4 個 txt）。
2. repo 的 Settings → Secrets and variables → Actions → New repository secret，**建立 4 個**（名稱要完全一樣，內容就是對應 txt 檔的全部文字）：
   `KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`
3. 回到 Actions，**把那次「Generate signing key」的執行紀錄刪掉**（右邊 ⋯ → Delete workflow run；Artifact 會一起刪），免得公開後有人下載到金鑰。
4. Actions → 「Android APK」→ Run workflow（選 main）。成功後 repo 首頁右側 Releases 會出現第一筆。
5. 手機（第一次換金鑰）：新 APK 的簽名跟舊的不同，覆蓋安裝會失敗；`overlay_install.sh` 會自動「移除舊版再重裝」（root 授權、無障礙要重設，App 內「修復權限」可以補回）。之後的更新都是直接覆蓋。

### fork 的人
Fork 之後自己做上面 1～4（Secrets 是每個 repo 各自的，不會跟著 fork 走）。App 內建的 repo 位置在編譯時由 `GITHUB_REPOSITORY` 決定，所以 fork 編譯出來的 App 會自動指向自己的 repo；Termux 端 `install.sh` 依 `git remote` 寫 `~/.phoneai_repo`，`update_app.py` 讀它。
