# PDF 阅读器 1.0.2 验证

2026-11-13，包名com.jy.pdf.read.io，versionCode3。

裁切改用非浮动全屏Dialog：隐藏系统栏、保留屏幕安全区，标题/说明和底部操作栏之间的预览占满剩余高度。保持四边中心点单边控制、全书裁切、重置/取消/应用及切换文件时关闭旧窗口的行为。

- JVM：Debug/Release各22项，共44项通过。
- 现有Android阅读回归：29项通过。
- 系统触摸流程：11项通过，包含中点裁切拖动、应用保存、阅读窗口恢复和进程重启。
- 正式包6项检查通过：1.0.1→1.0.2原位升级保留阅读进度，裁切窗口覆盖物理屏幕，预览高度占屏幕65%以上，三个按钮可见，取消与系统返回均保持阅读位置。
- Debug/Release构建和Lint通过；Release Lint 0 errors / 8既有提示。
- 正式签名和zipalign通过，证书与1.0.0/1.0.1一致，可覆盖升级。
- 首次全屏的系统引导在模拟器上手动确认后，触摸检查通过。

产物：dist/pdf-reader-1.0.2-universal.apk，49967 bytes。
SHA256：d971fe5337153c3963dc6b7b8a7866b4f81c13b4ff261b04fa5a6e3a92e0da25。
证书SHA256：5bb63a149c485982fac2aeffae635f361ea328259d0411febc6170da07216a12。

ADB操作均限定本机emulator-5554（Android16/API36），未操作实体掌阅。Android10 API兼容由minSdk/API Lint检查；墨水屏手感待实机验证。
