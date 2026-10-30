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
