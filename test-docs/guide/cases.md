# 完整測試案例

以下路徑都是相對於 `test-docs/guide/` 這個資料夾。

## 這 5 個應該被抓到（目標檔案不存在）

1. [搬走的安裝說明](./setup.md)
2. [不存在的資料夾](./missing/page.md)
3. [上一層不存在的檔](../outside.md)
4. [帶錨點的壞連結](./gone.md#section)
5. [帶空格的檔名](./my%20notes.md)

## 這些不該被抓到

- [同資料夾的 intro](./intro.md) — 存在
- [上一層的 index](../index.md) — 存在
- [外部網址](https://www.jetbrains.com)
- [另一個外部網址](http://example.com/a.md)
- [信箱](mailto:someone@example.com)
- [純錨點](#完整測試案例)
