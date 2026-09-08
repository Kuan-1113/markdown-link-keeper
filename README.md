# Markdown Link Keeper

**搬檔案時，自動修好所有指向它的 Markdown 連結。**

內建的 Markdown 支援只會在你打開某個檔案時標出壞連結。一個上百份文件的專案，
搬動一個檔案會弄壞散落各處的連結，而你不會知道。

---

## 現況

| 項目 | 狀態 |
|---|---|
| 主功能：搬移／改名時自動修連結 | ✅ 完成，可 Ctrl+Z 復原 |
| 副功能：全專案壞連結掃描 | ✅ 完成（`Tools` 選單） |
| 自動化測試 | ✅ **43 項全過** |
| IDE 相容性驗證 | ✅ `Compatible` (IC-262.10315.125) |
| CI 自動化 | ✅ 已設定（每週一自動檢查） |
| 上架 | ⬜ 待辦（見下方） |

---

## 三個指令

```bash
gradlew test          # 43 項測試，約 25 秒，不需要開 IDE
gradlew verifyPlugin  # 對 IDE 做相容性驗證，約 2 分鐘
gradlew buildPlugin   # 打包成可上架的 zip
```

`gradlew runIde` 會開一個沙盒 IDE 手動玩，但**平常不需要**——測試涵蓋了邏輯。

> ⚠️ `runIde` 關閉時回傳 exit code 11，Gradle 會標成 FAILED。**那是關窗，不是崩潰。**
> ⚠️ 沙盒開著時 `buildPlugin` 會失敗（jar 被鎖住）。先關掉沙盒。

---

## 架構：為什麼能自動測試

```
純邏輯（38 項單元測試）           IDE 相關（5 項整合測試）
├── MarkdownLinkParser           ├── MarkdownLinkUpdater
│   找連結 / 改寫連結             │   監聽搬移事件 → 改寫檔案
└── RelativePathUtil             └── CheckMarkdownLinksAction
    相對路徑計算                      選單動作
```

**所有會出錯的算術都在左邊，而左邊完全不需要 IDE。**
右邊用 `BasePlatformTestCase` 起一個無畫面的真專案來測，一樣不需要人。

測試涵蓋的邊界情況：保留錨點、保留連結標題、同行多連結、外部網址絕不改寫、
`mailto` 絕不改寫、程式碼區塊裡的假連結絕不改寫、爬出根目錄回傳 null 不爆炸、
Windows 反斜線路徑、不相關的檔案一個字都不能變。

---

## 維護自動化（`.github/workflows/ci.yml`）

**這一段是整個專案存活的關鍵。**

那個 539 萬下載的競品死因是 `No longer compatible with IntelliJ 2021.2` ——
**不是作者不會修，是沒發現壞了**，等使用者跑來罵已經過了好幾個月。

CI 做的事：

| 觸發時機 | 做什麼 |
|---|---|
| 每次推程式碼 | 跑 43 項測試 + 相容性驗證 |
| **每週一自動** | 檢查有沒有被新版 IDE 弄壞 |
| 手動觸發 | GitHub 網頁按一下 |

**沒壞的時候完全不會通知你；只有壞了才寄信。**
你的角色不是「維護者」，是「收信的人」——收到信轉給 Claude 修就好。

想改成每月檢查，把 `ci.yml` 裡的 cron 換成 `'0 1 1 * *'`。

### JetBrains 每出新版時

在 `build.gradle.kts` 的 `pluginVerification.ides` 加一行：

```kotlin
create(IntelliJPlatformType.IntelliJCommunity, "2026.3")
```

---

## 設定檔的五個坑（都寫在 `build.gradle.kts` 註解裡）

| # | 症狀 | 解法 |
|---|---|---|
| 1 | `runIde` 崩潰 exit 11 | 對 Community (IC) 編譯，不要用本機的 Ultimate |
| 2 | `Couldn't resolve ... download URL` | `useInstaller = false` |
| 3 | `No parameter with name 'useInstaller'` | 它是區塊內屬性，不是函式參數 |
| 4 | 版本不合 | `sinceBuild` 要跟著平台版本改 |
| 5 | 找不到 JDK 21 | 別用 `jvmToolchain(21)`，改設 `jvmTarget` |

額外兩個（Verifier 抓到的）：

| # | 症狀 | 解法 |
|---|---|---|
| 6 | `pluginVerification` 報 no IDE versions | 別用 `recommended()`（會是空清單），用 `current()`；方法名是 `create()` 不是 `ide()` |
| 7 | `Invalid plugin descriptor 'description'` | **說明必須以拉丁字母開頭、至少 40 字元**，中文開頭上架會被退件 |

---

## 上架待辦（這幾步只有你本人能做）

| 步驟 | 誰做 |
|---|---|
| 填 `plugin.xml` 的 `<vendor>`（信箱、名字） | 你 |
| 把 `group` 從 `dev.hello` 改成你的（如 `dev.yourname`） | 你或 Claude |
| 註冊 JetBrains 帳號 | **只能你**（我不能建立帳號） |
| 簽 Developer Agreement | **只能你**（有法律效力的合約） |
| 填銀行帳戶 + 稅務識別號 | **只能你** |
| 上傳 zip、送審 | 你（Claude 可準備好所有素材） |
| 開啟付費授權 | 你 |

**上架規定**（2026-09-08 查證）：個人即可販售，需年滿 18 歲；
抽成合約明訂**上限 25%**（你至少拿 75%）；出款門檻 US$200，未達則每年 12/31 結算；
VAT 與預扣稅由 JetBrains 代處理。

實際抽成幾 % 未公開 —— 投入前先寫信問 **marketplace@jetbrains.com**。
