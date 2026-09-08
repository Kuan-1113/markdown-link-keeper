import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

// ============================================================================
//  JetBrains 插件起手範本  —— 已在 Windows 11 實測通過 (2026-09-08)
//  實測環境：IDEA 2026.2.2 / Gradle 9.7.1 / JBR 25.0.4
//  這份設定是踩過 5 個坑之後的結果，每個坑都標在下面，不要隨意改。
// ============================================================================

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.20"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "io.github.kuan1113"     // 對應 github.com/Kuan-1113
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {

        // ┌─ 坑 1：一定要用 Community (IC)，不能用本機那套 Ultimate ────────────┐
        // │ winget 裝的 IntelliJ IDEA 是 Ultimate (IU) 二進位檔。             │
        // │ 用 local("...IntelliJ IDEA 2026.2.2") 指過去，編譯會過，          │
        // │ 但 runIde 啟動沙盒時因為沒有授權 → 直接崩潰 exit 11。             │
        // │ 對 IC 編譯則完全免授權，做出來的插件一樣能裝進 Ultimate。          │
        // └──────────────────────────────────────────────────────────────┘

        // ┌─ 坑 2：useInstaller 必須設成 false ───────────────────────────┐
        // │ 預設 true 走「安裝檔下載」，而 Community 安裝檔在 2025.3 之後    │
        // │ 就不再發布 → 任何近期版本都會報                                │
        // │   "Couldn't resolve IntellijIdeaCommunity download URL"       │
        // │ 設成 false 改走 Maven 平台檔，2026.x 才拿得到。                 │
        // └──────────────────────────────────────────────────────────────┘

        // ┌─ 坑 3：useInstaller 是「區塊內的屬性」，不是函式參數 ─────────────┐
        // │ 寫成 intellijIdeaCommunity("2026.2.2", useInstaller = false)   │
        // │ 會編譯失敗：No parameter with name 'useInstaller' found.        │
        // └──────────────────────────────────────────────────────────────┘

        intellijIdeaCommunity("2026.2.2") {
            useInstaller = false
        }

        // 整合測試需要平台測試框架（可在無畫面環境跑起一個真的專案）
        testFramework(TestFrameworkType.Platform)
    }

    // 單元測試：純 Kotlin 邏輯用普通 JUnit 測，不需要開 IDE
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // BasePlatformTestCase 是 JUnit3/4 血統，要用 vintage engine 才跑得起來
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:6.1.3")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            // 坑 4：sinceBuild 要對應上面的版本。2026.2.x → "262"，2026.1.x → "261"
            // 版本號看 IDEA 的 Help → About
            sinceBuild = "262"
        }
    }

    // ── 維護自動化的核心 ────────────────────────────────────────────
    // Plugin Verifier：拿這個插件去對多個 IDE 版本做相容性檢查。
    //
    // 那個 539 萬下載的競品死因是「No longer compatible with IntelliJ 2021.2」——
    // 不是作者不會修，是沒發現壞了，等使用者來罵時已經過了好幾個月。
    // 這一段的存在意義，就是把「發現」變成自動的。
    //
    // 跑法：gradlew verifyPlugin
    pluginVerification {
        ides {
            // ⚠ 不要用 recommended()：實測會解析成空清單，整個任務直接失敗。
            // ⚠ 方法名是 create()，不是 ide()。

            // current() = 對「目前編譯所用的平台版本」驗證，永遠有效
            current()

            // 要加更多版本時在這裡加，例如：
            //   create(IntelliJPlatformType.IntelliJCommunity, "2026.3")
            // JetBrains 每年出三次大版本，出新版時加一行即可。
        }
    }

    // ── 自動上架 ────────────────────────────────────────────────────
    // 跑法：gradlew publishPlugin
    //
    // 需要一組 Marketplace token（只有你本人拿得到，見 README「上架待辦」）。
    // token 從環境變數讀，絕對不要寫死在這個檔案裡 —— 它等同你的商店帳號密碼。
    //
    //   Windows:  $env:PUBLISH_TOKEN = "你的token"
    //   CI:       存成 GitHub Secret，名稱 PUBLISH_TOKEN
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")

        // 首次上架建議先發到 stable 以外的頻道試水溫，例如 "beta"；
        // 留空即為 stable（正式頻道）。
        // channels = listOf("beta")
    }

    // 上傳前的自檢：ID、名稱、說明、圖示、相容性範圍有沒有符合商店規定
    // 跑法：gradlew verifyPluginProjectConfiguration
}

// ┌─ 坑 5：不要用 kotlin { jvmToolchain(21) } ─────────────────────────────┐
// │ 機器上只有 IDEA 內建的 JBR 25，沒有獨立的 JDK 21。                      │
// │ 指定 toolchain(21) 會讓 Gradle 去找一個不存在的 JDK。                   │
// │ 正解：用手上的 JDK 25 編譯，但指定產出 Java 21 相容的位元碼。            │
// └────────────────────────────────────────────────────────────────────┘
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
