# PDF Reader Implementation Plan

> Use superpowers:executing-plans inline. Track completion here and in verification notes.

**Goal:** 独立墨水屏 PDF 阅读器，支持裁切缩放、双向触摸滚动、实体键纵向滚动/边界翻页、外部打开及 MD5 阅读记录。
**Architecture:** Java framework Activity + custom tiled View, serialized PdfRenderer worker, local document import and atomic JSON state.
**Tech Stack:** JDK17 / AGP8.11.1 / Gradle8.13 / compileSdk36 / minSdk26 / targetSdk35.
**Spec:** ../specs/2026-11-12-pdf-reader-design.md

## Global constraints
- Project pdf-reader; applicationId com.jy.pdf.read.io; version 1.0.0/1.
- Android10, 128MiB heap, no root/system signing/network requirement.
- scanCode115 previous,114 next. Keys never alter horizontal offset. Common whole-document crop.

## Review focus
- Nonseekable/expired content grants and invalid/encrypted PDFs must fail visibly without corrupting existing progress.
- Keys at page boundaries, held keys, and horizontal offset preservation.
- Zoom/crop/render stale callbacks and bounded bitmap memory.
- Rotation/process recreation/other-app VIEW switching must restore correct document position.
- Crop geometry across differently sized pages, invalid saved state, and render exceptions.

## Tasks
- [x] 1. Write failing JVM tests for crop validity, viewport fit/zoom anchor/clamping and key navigation. Implement pure geometry and navigation until green.
- [x] 2. Implement seekable import/MD5 and atomic JSON progress; real PDF fixture and state roundtrip instrumentation.
- [x] 3. Implement serialized engine + tiled viewport, touch gestures and scrollbars; validate with long/wide real PDF.
- [x] 4. Implement home/open VIEW intents/crop UI/reader controls/key dispatch and debounced lifecycle state saves.
- [x] 5. Run unit, lint, Android instrumentation and host UI smoke (external VIEW, progress restore, crop, key boundaries, touch zoom/scroll). Fix evidence-backed failures.
- [x] 6. Build and sign release universal APK, verify manifest/signature, copy APK/guide/screenshots to C:/Users/lin/Downloads.

Ruling: new standalone folder is isolation; no existing project or branch is modified. User has specified implementation and selected crop behavior, so continue inline without another approval gate.
