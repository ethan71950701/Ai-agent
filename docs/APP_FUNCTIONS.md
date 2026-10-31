# Android 16 AppFunctions：怎麼呼叫三星提醒、時鐘（2026-10-30 實測）

Gemini 能「幫你新增三星提醒」，是因為三星提醒（還有時鐘）宣告了 **AppFunctions**（Android 16 的功能：App 宣告可被 AI 呼叫的函式，系統建立索引）。
我們在 root／Shizuku 底下可以用系統的 `cmd app_function` 直接呼叫，不用操作畫面。**以下都是在作者的 Samsung S25+（Android 16）上實測的結果，其他機型和 One UI 版本不保證。**

## 怎麼發現的
1. `dumpsys package com.samsung.android.app.reminder` 裡有 `MainAppFunctionService`（action `android.app.appfunctions.AppFunctionService`）。
2. `cmd app_function help` 有 `list-app-functions`、`execute-app-function`、`set-enabled`。
3. `cmd app_function list-app-functions` 列出全部函式（JSON，約 330 KB）。
4. 呼叫權限 `android.permission.EXECUTE_APP_FUNCTIONS` 是 `internal|privileged`，一般 App 拿不到；用 root／shell 的 `cmd` 可以。`restrictCallersWithExecuteAppFunctions` 是 false。

## 三星提醒（`com.samsung.android.app.reminder`）
函式 ID 格式：`com.samsung.android.app.reminder.data.crossappactions#<名稱>`。

| 名稱 | 用途 |
|---|---|
| `TaskFunctions_createTask` / `updateTask` / `deleteTasks` / `findTasks` / `getTasks` / `showTask` | 提醒的新增、修改、刪除、搜尋、讀取、顯示 |
| `CategoryFunctions_createTaskCategory` / `updateTaskCategory` / `deleteTaskCategories` / `findTaskCategories` / `showTaskCategory` | 分類 |
| `AlarmCrossAppActions_*`、`TimerCrossAppFunctions_*`、`StopwatchCrossAppFunctions_*` | 時鐘 App 的鬧鐘、計時器、碼表（尚未接進 App） |

### createTask 的參數（實測）
```
cmd app_function execute-app-function --package com.samsung.android.app.reminder \
  --function 'com.samsung.android.app.reminder.data.crossappactions#TaskFunctions_createTask' \
  --parameters '{"createTaskParams":{"title":"交報告","dateTime":{"timeZone":"Asia/Taipei","date":{"year":2026,"month":10,"day":31},"timeOfDay":{"hours":15,"minutes":0,"seconds":0,"nanos":0}}}}'
```
- 參數名稱是 `createTaskParams`（物件）。`title` 是標題。
- 時間是 `dateTime` 物件：必填 `timeZone`；`date`（`year`/`month`/`day`）；`timeOfDay`（`hours`/`minutes`/`seconds`/`nanos`，**複數**）。沒有 `timeOfDay` 就是只有日期；`allDay: true` 是全天。
- 傳了不認得的欄位會被**默默忽略**（不會報錯），回傳裡沒有的欄位就是沒被收下。這是找出欄位名稱的方法：看回傳有沒有出現。
- `getTasks` 要 `taskIds`；`findTasks` 要 `findTasksParams`（物件，空物件會回空清單，搜尋條件欄位名還沒查）。
- 成功回傳 `androidAppfunctionsReturnValue`，裡面有 title、dateTime、`taskCategoryId`（預設 `LOCAL_SPACE`）。

## 怎麼查欄位名稱（欄位被混淆成 a、b、c 時）
`dumpsys` 與 `af.json` 裡沒有參數格式。App 的 dex 裡有資料類別：`com/google/android/appfunctions/schema/common/v1/tasks/`（`CreateTaskParams`、`Task`…）和 `…/types/`（`DateTime`、`Date`、`TimeOfDay`）。
欄位**型別**看得到（例如某個欄位是 `DateTime`），但名稱被混淆；真正名稱靠「傳猜測的名字、看回傳有沒有收下」，或錯誤訊息（`Parameter [xxx] cannot be null` 會直接說出缺什麼）。

## App 裡的使用
- `AgentLoop.reminderCreate`：組 JSON、用 `Maintenance.suOnce` 執行、檢查回傳有 `androidAppfunctionsReturnValue` 和 `title` 才算成功。
- 聊天助理的 `samsung_reminder` 工具、操作畫面迴圈的 `reminder_create` 動作都走它；沒有 root／Shizuku 時退回 `reminder_draft`（用三星提醒公開的分享入口 `ShareActivity` 開新增畫面並填好標題，使用者自己設時間與儲存）。
- Google Tasks 是否有同樣的函式還沒查（做法：`cmd app_function list-app-functions` 找它的套件）。

---

# 給其他專案用：提醒、鬧鐘、行事曆、計時器、碼表怎麼接（2026-10-31 整理）

以下都是在 Samsung Galaxy S25+（Android 16、One UI 8）實測或讀程式碼得到的，**換機型、換系統版本不保證**。每一項都標了「實測過」或「沒實測」。

## 0. 三條路，由穩到不穩

| 做法 | 需要 | 能做什麼 | 穩定度 |
|---|---|---|---|
| A. 系統標準 Intent（`AlarmClock`、`CalendarContract`） | 一般權限 | 鬧鐘、行事曆事件 | 最穩，所有手機都有 |
| B. 目標 App 公開的 Intent（三星提醒的分享入口） | 不用權限 | 開新增畫面、預填標題；時間與儲存要人按 | 穩，但不能靜默新增有時間的提醒 |
| C. AppFunctions（`cmd app_function`） | **root 或 shell 權限** | 靜默新增有時間的三星提醒；時鐘的鬧鐘／計時器／碼表（沒實測） | 最強但最脆弱：欄位名被混淆、App 更新可能改 |

## 1. 鬧鐘（做法 A，實測過）— `AlarmTool.java`
- 用 `AlarmClock.ACTION_SET_ALARM`，欄位：`EXTRA_HOUR`（0–23）、`EXTRA_MINUTES`、`EXTRA_MESSAGE`、`EXTRA_SKIP_UI=true`（不開時鐘畫面直接設好）。
- 權限：`<uses-permission android:name="com.android.alarm.permission.SET_ALARM" />`（一般權限，裝上就有）。
- 三星時鐘對一般 App 的 SET_ALARM 比較挑剔，所以程式先用 root 的 shell 啟動同一個 Intent，失敗才退回 `startActivity`：
  ```
  am start -a android.intent.action.SET_ALARM --ei android.intent.extra.alarm.HOUR 15 --ei android.intent.extra.alarm.MINUTES 30 --es android.intent.extra.alarm.MESSAGE '標題' --ez android.intent.extra.alarm.SKIP_UI true
  ```
- 「N 分鐘後響」：自己用 `Calendar` 加分鐘算出 hour/minute 再送。這個 Intent 只能設「今天或明天的某個時間」，**不能設星期重複以外的特定日期**。
- 限制：只能**新增**，查詢、修改、刪除鬧鐘用 Intent 做不到（`AlarmClock` 有 `ACTION_DISMISS_ALARM` 等，但我們沒用，沒實測）。

## 2. 行事曆事件（做法 A，實測過）— `SystemIntents.addEvent`
- 有權限時直接寫資料庫：`CalendarContract.Events.CONTENT_URI` 的 `insert`，欄位 `CALENDAR_ID`（挑主要行事曆，沒有就第一個可寫的）、`TITLE`、`DTSTART`、`DTEND`、`EVENT_TIMEZONE`、`EVENT_LOCATION`、`DESCRIPTION`。需要 `READ_CALENDAR`、`WRITE_CALENDAR`。
- 沒權限：有 root 就 `pm grant <套件> android.permission.WRITE_CALENDAR`（和 READ）；還是不行就打開行事曆的新增畫面（`Intent.ACTION_INSERT` + `Events.CONTENT_URI`，帶 `title`、`beginTime`、`endTime`），使用者要按儲存。
- 提醒時間（開始前 N 分鐘）要另外寫 `CalendarContract.Reminders`，我們的版本寫了預設提醒，細節看 `addDirect` 後半段。
- 這是「日曆事件」，**不是三星提醒 App 裡的提醒**，兩個是不同的資料庫。

## 3. 三星提醒（做法 B 與 C）
### B. 公開的分享入口（實測過，不用 root）
`dumpsys package com.samsung.android.app.reminder` 看到 `ShareActivity` 接受 `android.intent.action.SEND`：
```java
startActivity(new Intent(Intent.ACTION_SEND).setType("text/plain")
    .setClassName("com.samsung.android.app.reminder", "com.samsung.android.app.reminder.ui.share.ShareActivity")
    .putExtra(Intent.EXTRA_TEXT, "標題").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
```
會開新增畫面並填好標題，時間與儲存要人按。Android 11 以上用明確的元件名稱啟動，不需要在 manifest 宣告 `<queries>`。

### C. AppFunctions 靜默新增（實測過，要 root／shell）
見本文件上半部：`cmd app_function execute-app-function --package … --function '…#TaskFunctions_createTask' --parameters '{…}'`。程式用 `Maintenance.suOnce(...)`（背景執行緒，root 的 shell）執行，檢查輸出含 `androidAppfunctionsReturnValue` 和 `"title"` 才算成功。
- 組 JSON 一律用 `org.json`，shell 引號用單引號包起來，內容裡的單引號換成 `'\''`。
- 時區用 `TimeZone.getDefault().getID()`；日期格式是 `year`/`month`（1–12）/`day`；時間 `hours`/`minutes`/`seconds`/`nanos`（複數）。
- 沒有時間只有日期：不給 `timeOfDay`、給 `allDay: true`。
- **權限：** `android.permission.EXECUTE_APP_FUNCTIONS` 是 `internal|privileged`，一般 App 自己呼叫不到（`AppFunctionManager` 會拒絕）。我們是借 root 的 `cmd`。Shizuku（shell 身分）能不能呼叫**沒實測**。

## 4. 時鐘的鬧鐘、計時器、碼表（做法 C，只有函式清單，**沒實測**）
`cmd app_function list-app-functions` 在時鐘／提醒相關套件下看到的函式 ID（前綴同上，`#` 後面）：
- 鬧鐘：`AlarmCrossAppActions_createAlarm`、`createAlarms`、`updateAlarm`、`deleteAlarms`、`findAlarms`、`getAlarms`、`showAlarm`
- 計時器：`TimerCrossAppFunctions_createTimer`、`createTimers`、`updateTimer`、`deleteTimers`、`findTimers`、`getTimers`、`showTimer`
- 碼表：`StopwatchCrossAppFunctions_startStopwatch`、`stopStopwatch`、`resetStopwatch`、`showStopwatch`

這些函式在 `dumpsys` 裡出現在**三星提醒的套件**底下（`com.samsung.android.app.reminder`）的清單中？—— **我們只看到函式 ID，沒有確認每個函式屬於哪個套件**。請先用 `cmd app_function list-app-functions > af.json`，搜 `AlarmCrossAppActions` 看它掛在哪個 `packageName` 底下，再用那個套件名稱呼叫。

### 怎麼找參數格式（我們用過、有效的方法）
1. **用空參數呼叫**，看錯誤：`Parameter [createTaskParams] cannot be null` 會直接告訴你參數名叫什麼、是物件。
2. **逐層補**：給 `{"xxxParams":{"title":"測試"}}`，錯誤會說下一個缺什麼（例如 `DateTime.<init> parameter timeZone`）。
3. **看回傳**：成功時回傳會列出它真的收下的欄位；傳了不認得的欄位**不會報錯，只是被忽略**，回傳裡沒出現就是沒被收下。這是確認欄位名稱最可靠的方法。
4. **看型別**：把 App 的 APK 複製出來，掃 dex 裡 `com/google/android/appfunctions/schema/common/v1/…` 的類別欄位型別（欄位名被混淆成 a、b、c，但型別看得到，例如某欄位是 `DateTime`、`Boolean`），知道哪個欄位是巢狀物件。腳本（Termux，python3）：
   ```python
   import zipfile,struct
   z=zipfile.ZipFile('rem.apk'); out=set()
   for n in z.namelist():
       if not n.endswith('.dex'): continue
       d=z.read(n)
       so=struct.unpack_from('<I',d,0x3C)[0]; to=struct.unpack_from('<I',d,0x44)[0]
       fs,fo=struct.unpack_from('<II',d,0x50)
       def S(i):
           o=struct.unpack_from('<I',d,so+4*i)[0]
           while d[o]&0x80: o+=1
           o+=1
           return d[o:d.index(0,o)].decode('utf8','replace')
       def T(i): return S(struct.unpack_from('<I',d,to+4*i)[0])
       for i in range(fs):
           c,t,nm=struct.unpack_from('<HHI',d,fo+8*i); cn=T(c)
           if 'schema/common/v1/' in cn and '$$' not in cn: out.add(cn.split('/')[-1]+' . '+S(nm)+' : '+T(t).split('/')[-1])
   print('\n'.join(sorted(out)))
   ```
   APK 取得：`su -c "pm path <套件>"` 找到路徑，`su -c "cat <路徑>" > rem.apk`。
5. 巢狀物件的欄位真名靠回傳得知（例如 `timeOfDay` 回傳是 `hours/minutes/seconds/nanos`）。

### 碼表／計時器的預期做法（推測）
- 碼表沒有資料可建，應該只有動作：`startStopwatch`／`stopStopwatch`／`resetStopwatch`，參數可能是空物件或一個 `…Params`。先用空參數呼叫看錯誤。
- 計時器的 `createTimer` 大概需要「時長」（秒或 hours/minutes/seconds 物件）與標題。
- **以上都是推測**，要實測。若函式不存在或被拒絕，標準做法是 `AlarmClock.ACTION_SET_TIMER`（`EXTRA_LENGTH` 秒、`EXTRA_MESSAGE`、`EXTRA_SKIP_UI`），這個是系統標準、不用 root（沒實測）。碼表沒有標準 Intent。

## 5. 在別的專案重用的建議
1. **先走做法 A**（`AlarmClock`、`CalendarContract`、`ACTION_SET_TIMER`），最穩。
2. 想靜默操作某個廠商 App，再試 C；程式裡**一定要檢查回傳**有沒有真的收下欄位，失敗退回 B 或 A。
3. 把「組 JSON → 單引號跳脫 → 在背景執行緒用 root shell 執行 → 解析回傳」包成一個函式；不要在主執行緒做。
4. 不要信任回傳以外的任何東西：App 更新後欄位名稱或必填欄位可能變。
5. 安全：呼叫參數裡的文字（標題）要當成不可信資料處理；不要把 AI 生成的字串直接拼進 shell，要用 `org.json` 序列化後再單引號跳脫（見 `AgentLoop.reminderCreate`）。
6. 這些都不是公開 API：發布到 Play 商店的 App 不能依賴 root／privileged 權限。
