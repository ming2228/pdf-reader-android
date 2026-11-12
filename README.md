# PDF 阅读器

项目 `pdf-reader-android`，包名 `com.jy.pdf.read.io`，当前版 1.0.2。面向 Android 10 掌阅 Ocean 4 Turbo 2025，支持 Android 8.0+。

- 只支持 PDF。应用内选择文件，或在文件管理器长按 PDF → 其他应用打开 → PDF 阅读器。
- Android10 另提供设备文件浏览入口，按需请求读取存储权限；系统选择器和外部临时授权无需广泛存储权限。
- 顶部悬浮层为返回箭头、超长尾部省略的文件名、书签图标；底部是翻页、页码跳转、裁切与缩放。顶栏下缘/底栏上缘是虚线。单击页面同时显隐，始终不占 PDF 布局空间。
- 裁切框四边中点各一个控制点，只移动对应的单条边；不支持角落双边联动或框内整体拖动。整份文档共享比例裁切。原 PDF 不被改写，重置后点应用才生效。
- 裁切窗口铺满屏幕，隐藏系统栏并尊重刘海安全区；预览自动填满标题/说明和底部操作栏之间的剩余空间。
- 以裁切后适配屏幕宽度为 100%，可缩放到 400%，不能比适配宽度更小。双指/按钮缩放，双击在 100% 与 200% 切换，单指横纵拖动，显示高对比滚动条。
- scanCode115 / VOLUME_UP 为上翻，114 / VOLUME_DOWN 为下翻，兼容 PAGE_UP/DOWN。每次滚动约 80% 可见高度，保留重叠文本；实体键不改变横向偏移。
- 已在顶部时再按上翻进入上一页；已在底部时再按下翻进入下一页。切换新页时横纵滚动位置都归零。到达边界的那次按键不同时翻页，长按重复只滚动、不连续跨 PDF 页。
- 内容 MD5 关联 JSON 阅读记录：页码、PDF 坐标、缩放、全书裁切；同内容改名可续读。滚动后 500ms 保存，后台、退出、切文档时立即保存。
- 每份PDF仅一个书签。点击当前页书签图标设置/替换，已标记页再次点击取消。重新打开优先书签页并从页首开始；无书签才恢复最后阅读页和页内位置。旋转屏幕保留当前阅读位置。
- 页码输入点击跳转时归一化：超过总页数归最后页；小于1、空白、非数字归1，兼容粘贴负数、小数和任意长度数字。
- 内部保留导入的 PDF 副本，以支持不可 seek 的文件流和临时授权后的续读。没有过期自动删除；书库长按条目可移除副本和记录，来源文件保留。
- 不联网、不使用 root/系统签名，不支持密码 PDF、OCR、批注、文本重排。文件无效、无法读取或加载失败时显示错误并保留上次完成的阅读记录。

## 架构

原生 Java / Android framework PdfRenderer，不含第三方运行库和 JNI 文件，因此发布为通用 APK，可用于 arm64-v8a 和 armeabi-v7a 系统。
低分辨率整页预览 + 512px 可见分块；位图分块缓存最多 16MiB（依堆大小调整）。PdfRenderer 在串行工作线程执行，旧页/旧缩放回调丢弃。
`DocumentStore` 将用户选择的内容流复制成可 seek 文件并计算 MD5；`AtomicFile` 原子写入 `files/reading-progress.json`。

主要源码：`ReaderGeometry`（几何/键导航）、`PdfEngine`（渲染）、`PdfPageView`（手势/分块/滚动条）、`CropSelectionView`、`DocumentStore`、`ReadingState`、`MainActivity`。

## 构建

JDK17+，Android SDK36 / build-tools36.0.0，AGP8.11.1 / Gradle8.13。minSdk26 / targetSdk35。

```powershell
./gradlew.bat :app:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
./build-release.ps1
```

正式签名只用于本项目，私钥和密码保存在忽略的 `.keys/`，请自行安全备份；后续升级使用同一签名。不要提交 `.keys`。

## 验证

JVM 测试覆盖裁切、缩放锚点、边界和按键只纵向移动。自定义 Android Instrumentation 使用独立测试 APK 的私有、不可 seek PDF 提供程序，验证真实临时 URI 授权、页内滚动、跨页、裁切、MD5 续读及取消/切换。
`verification/ui_smoke.py` 仅操作指定本机模拟器，验证实际拖动裁切框、触摸、悬浮层及强停进程后外部重新打开。
测试 APK 的 FixtureProvider/FixtureLauncher 只存在于 androidTest，不打进发布包。
