# 測試文件

這個資料夾是拿來驗證「Markdown 連結檢查器」的，裡面故意放了壞掉的連結。

## 應該要被抓到的（壞連結）

- [搬走的安裝說明](./setup.md)
- [不存在的資料夾](./missing/page.md)
- [錯誤的相對路徑](../outside.md)
- [帶錨點的壞連結](./guide/gone.md#section)
- [帶空格的檔名](./my%20notes.md)

## 不應該被抓到的（正常）

- [同資料夾的正常檔案](./guide/intro.md)
- [外部網址](https://www.jetbrains.com)
- [另一個外部網址](http://example.com/a.md)
- [信箱連結](mailto:someone@example.com)
- [純錨點](#測試文件)
