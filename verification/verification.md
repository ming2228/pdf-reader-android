# PDF 阅读器 1.0.0 验证

日期：2026-11-12，包名 com.jy.pdf.read.io，versionCode1。

## 结果

- `:app:test`：Debug/Release 各16个几何和按键测试，32项通过。
- 几何实现前初次执行：16项中的12项失败；实现后全部通过。
- `:app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease`：最终源码全部成功。
- Debug / Release Lint：均0 errors、10 warnings。包含固定 targetSdk35 的版本提示、备份新配置建议、自绘布局工具构造器/分配建议、中文文案拼接以及 GestureDetector 内已处理 performClick 的静态提示。
- Android Instrumentation：29项通过，见根目录 android-instrumentation.log。
- 系统输入 UI 检查：11项通过，见 ui-smoke.log 与 build/ui-smoke/results.json。
- 独立正式签名 APK：7项发布包检查通过，见 release-smoke.log 与 build/release-smoke/results.json。
- apksigner 签名和 zipalign 校验通过。APK不带debuggable属性，不包含JNI库，不含测试Provider/Activity。
- aapt：minSdk26 / targetSdk35；无网络权限；仅Android29及以下声明READ_EXTERNAL_STORAGE。

## 已覆盖的实际场景

1. 测试 APK 作为不同 UID 的文件来源：私有不可 seek pipe Provider，经临时 URI read grant 用 ACTION_VIEW 打开到阅读器。
2. 600×1800、600×1200、1200×600 三种 PDF 页尺寸，真实 PdfRenderer 预览/分块显示内容。
3. 裁切后适配宽度、100–400%缩放，双指 MotionEvent、单指双轴拖动，高对比滚动条。
4. scanCode115/114和音量键/PAGE_UP/DOWN回退，纵向80%滚动、横向偏移不变、边界需新按下才跨页、长按不连续跨页。
5. 顶底悬浮层单击同时显隐，视口bounds不变，拖动不触发显隐。
6. 拖动裁切角点并应用，整份PDF共用，重置必须再次应用。
7. 原子JSON、新Store实例、退出重开、强停进程后重开、同内容不同文件名：恢复页码、缩放、页内横纵坐标和裁切。
8. 加载中返回、取消/替换导入无未索引副本、外部切文档关闭旧裁切和跳页对话框。
9. 加载期间外部传入非PDF，错误后返回书库，原记录保留且能重新打开有效PDF。
10. 发布包确实参与 application/pdf 的 Open With 解析，不参与 text/plain。

## 审查修复

- 自定义View程序化打开平台滚动条在模拟器系统产生ScrollBarDrawable空指针：改为高对比自绘滚动条，首屏及后续检查通过。
- 页面切换取消/失败后旧View token可能落后于native renderer：取消/错误统一安全回书库、关闭旧会话，保存最后完成的页码；回归覆盖。
- 导入完成到首帧期间取消可能遗留无索引副本：用带同步的导入所有权追踪清理，volatile序列拒绝过期任务；取消压力检查覆盖。
- 文档对话框跨ACTION_VIEW可能误作用到新文档：切换时关闭，回调还绑定文档所有者及序列；裁切/跳页分别覆盖。
- 首次布局前不保存未恢复的视口坐标，避免覆盖旧进度。

## 平台与限制

本机模拟器：Android16/API36，x86_64。没有Android10镜像；Android10兼容性由minSdk/API Lint和分支检查验证，仍需掌阅实机确认墨水屏刷新、物理键手感、定制文件选择器及旧file URI权限入口。
所有ADB测试明确限定emulator-5554，未安装或修改实体阅读器。
一次代表性发布包内存快照为TOTAL PSS约59MiB；这不是所有PDF的峰值保证。位图缓存上限16MiB，整页预览约1.2MP，放大采用512px可见分块。
不支持需要密码的PDF、OCR、批注及文字重排。

## 发布产物

`dist/pdf-reader-1.0.0-universal.apk`，45703 bytes。
SHA256：1500f676fec1ada2147c38c4ee7906230453897ae7733a5b2757343e5b53bc0d。
签名证书SHA256：5bb63a149c485982fac2aeffae635f361ea328259d0411febc6170da07216a12。
专用签名私钥与密码在忽略的`.keys`目录，后续升级应保留同一签名。
