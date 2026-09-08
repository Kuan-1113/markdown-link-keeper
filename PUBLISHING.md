# 上架與金流：逐步清單

分成兩欄：**我能做的**已經全部做完；**只有你能做的**要你本人操作。
原因不是我懶——帳號、法律合約、金融資料，這三類我不能代做。

---

## 第 0 步：先寄一封信（5 分鐘，建議最先做）

抽成比例 JetBrains 沒有公開，只知道合約上限 25%。
**這個數字會影響值不值得走完後面所有步驟**，所以先問。

寄到：**marketplace@jetbrains.com**

主旨建議：`Question about revenue share for a paid plugin`

內文草稿（直接複製可用）：

```
Hello,

I am an individual developer preparing to publish a paid plugin
on JetBrains Marketplace, and I would like to confirm the commercial
terms before I complete the vendor registration.

1. What is the current revenue share percentage for paid plugins
   sold by an individual (non-company) vendor?
2. Are there any published statistics on plugin sales that vendors
   can reference when estimating demand?
3. I am based in Taiwan. Are there any additional requirements or
   restrictions for vendors in Taiwan?

Thank you for your time.

Best regards,
（你的名字）
```

---

## 第 1 步：填三個空格（我不能替你決定的）

打開 `src/main/resources/META-INF/plugin.xml`，改這一行：

```xml
<vendor email="請填入你要公開的聯絡信箱" url="">請填入你的名字或品牌名</vendor>
```

⚠️ **這個 email 會公開顯示在商店頁面上。** 建議另開一個對外用的信箱，
不要用你平常的私人信箱。

同時確認這一行是你要的（**發布後永久固定，不能改**）：

```xml
<id>io.github.b0975.mdlinkkeeper</id>
```

如果 `b0975` 不是你的 GitHub 帳號，現在改；上架後就來不及了。

---

## 第 2 步：註冊帳號（只有你能做）

1. 到 https://plugins.jetbrains.com
2. 右上角登入 → 用 JetBrains Account 註冊（沒有的話當場建立）
3. ⚠️ 我不能代你註冊帳號或輸入密碼

---

## 第 3 步：簽 Developer Agreement（只有你能做）

上傳插件之前必須先簽。這是**具法律效力的合約**，只有你本人能簽署。

- 年滿 18 歲
- 有能力簽署具法律效力的合約

---

## 第 4 步：上傳插件

```bash
gradlew clean test verifyPlugin buildPlugin
```

產出：`build/distributions/markdown-link-keeper-0.1.0.zip`

到 https://plugins.jetbrains.com/plugin/add 上傳這個 zip。

**首次上傳會進入人工審核**，通常幾個工作天。

---

## 第 5 步：開啟付費授權（只有你能做）

審核通過後，在插件管理頁面切換成付費模式。需要提供：

| 資料 | 說明 |
|---|---|
| **你本人名下的銀行帳戶** | 收款用，必須是本人帳戶 |
| **稅務識別號** | 台灣身分證字號或統編 |

⚠️ **這些我一律不碰。** 銀行帳號、稅號屬於金融與身分資料，
我不會替你輸入，也不該經手。

**出款規則**：累積達 US$200 撥款；未達門檻則每年 12/31 一律結算。
VAT 與預扣稅由 JetBrains 代處理。

---

## 第 6 步（可選）：把上架也自動化

拿到 Marketplace token 之後（在你的帳號設定頁產生）：

```powershell
$env:PUBLISH_TOKEN = "你的token"
gradlew publishPlugin
```

⚠️ **token 等同你的商店帳號密碼。** 絕對不要寫進程式碼、不要貼給任何人（包括我）。
要放進 CI 的話，存成 GitHub Secret，名稱 `PUBLISH_TOKEN`。

---

## 檢查表

- [ ] 寄信問抽成（第 0 步）
- [ ] 填 vendor email 與名字
- [ ] 確認 plugin id 是你要的（**永久固定**）
- [ ] 註冊 JetBrains 帳號
- [ ] 簽 Developer Agreement
- [ ] `gradlew clean test verifyPlugin buildPlugin` 全綠
- [ ] 上傳 zip 送審
- [ ] 審核通過後開啟付費授權
- [ ] 填銀行帳戶與稅號
