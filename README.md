# Smart Alarm (智能闹钟)

[![CI & Release Build](https://github.com/edompeng/alarm/actions/workflows/ci.yml/badge.svg)](https://github.com/edompeng/alarm/actions/workflows/ci.yml)
[![Platform](https://img.shields.io/badge/Platform-Android%2011%2B%20(API%2030--34)-green.svg)](https://developer.android.com)
[![Build System](https://img.shields.io/badge/Build%20System-Bazel%209-orange.svg)](https://bazel.build)
[![Language](https://img.shields.io/badge/Language-C%2B%2B20%20%7C%20Java%2017-blue.svg)](https://isocpp.org)

Smart Alarm 是一款专为中国法定节假日与复杂唤醒场景设计的高可靠性智能闹钟 Android 应用。应用采用 **C++20 跨平台核心业务层 + JNI 桥接 + 原生 Android UI** 的架构，严格遵循 Clean Architecture 与设计模式原则，支持国务院法定节假日与调休补班自动计算、锁屏防杀极速唤醒、防贪睡趣味挑战、快速小憩与音效震动触觉反馈。

---

## 🌟 核心特性 (Key Features)

1. **🇨🇳 法定节假日与调休补班智能识别**
   - **权威日历遵循**：自动根据国务院办公厅发布的最新节假日与调休补班安排调整闹钟。法定节假日自动跳过不响，周末调休补班日准时响铃。
   - **云端静默与手动同步**：默认通过 GitHub 权威源静默或手动同步最新日历配置，支持用户自定义同步 URL。
   - **离线保底基线**：内置 `statutory_holidays_baseline.json` 离线日历规则，无网络环境下依然准时。

2. **🔔 极致保活与精准响铃 (High-Reliability Alarm Delivery)**
   - 基于 `AlarmManager.setExactAndAllowWhileIdle()` 精确唤醒。
   - 锁屏高优先级拉起：结合 `turnScreenOn` 与 `showWhenLocked`，穿透锁屏全屏提醒。
   - OEM 厂商保活引导：针对 Vivo/iQOO OriginOS、Samsung One UI 等定制系统提供后台自启动及电池优化设置指引。

3. **🧮 防贪睡趣味挑战 (Anti-Snooze Challenges)**
   - **算术解题挑战**：响铃后需解开随机数学算式方可关闭或贪睡，防止无意识关闹钟。
   - **摇一摇挑战**：通过加速度传感器识别持续晃动次数，确保大脑完全清醒。

4. **🛌 快速小憩 (Quick Nap) 与 自定义休假 (Vacation Skip)**
   - 提供 4 个预设小憩槽位（如 15分钟、30分钟、45分钟、60分钟），一键开启倒计时闹钟。
   - 支持自定义休假日期跳过，节假日前后灵活安排。

5. **🎵 天气铃声与动感触觉反馈 (Audio & Haptics)**
   - 内置温和与轻快高质量音频，根据天气或场景自适应映射。
   - 支持高级震动波形（Vibration Effect Waveforms），带来层次分明的震动提醒。

6. **🌐 双语国际化 (Localization)**
   - 完整支持系统跟随、简体中文（zh-CN）与英文（en）。

---

## 🏗️ 架构设计 (Architecture)

整个项目采用整洁架构（Clean Architecture）并充分遵循设计模式六大原则（SOLID）：

```
alarm/
├── app/                  # Android 应用层 (UI, Dialogs, Receivers, Services)
│   ├── src/com/edom/     # Java 业务与界面实现
│   └── res/              # 资源文件 (Layout, Raw Audio & Baseline JSON)
├── core/                 # 跨平台核心业务层 (C++20 & Framework-Free Java Policy)
│   ├── src/holiday/      # 法定节假日计算引擎与云端同步服务
│   ├── src/scheduler/    # 闹钟计算与调度核心
│   ├── src/challenge/    # 防贪睡挑战策略 (策略模式)
│   ├── src/audio/        # 音频与触觉控制器 (工厂模式)
│   ├── src/sensor/       # 传感器融合监听器
│   └── src/assets/       # 官方节假日基准数据 (holidays_2026.json)
├── data/                 # 数据持久化层 (C++ SQLite3 仓储实现)
│   ├── src/db/           # SQLite 数据库助手与契约
│   ├── src/model/        # 闹钟与节假日数据模型
│   └── src/repository/   # 仓储模式实现 (Repository Pattern)
├── scripts/              # 构建、验证与日历生成工具脚本
│   ├── build_apk.sh      # 编译、对齐、签名可运行 APK 核心脚本
│   └── generate_holiday_config.py # 法定节假日配置生成器
└── tests/                # 单元测试集 (C++, Java, Python)
```

- **策略模式 (Strategy Pattern)**：在 `IChallengeEngine` 中抽象出 `MathChallengeStrategy` 与 `ShakeChallengeStrategy`。
- **仓储模式 (Repository Pattern)**：通过 `IAlarmRepository` 与 `IHolidayRepository` 解耦底层数据库访问。
- **工厂模式 (Factory Pattern)**：通过 `HapticWaveformFactory` 构建多样化的触觉震动反馈。
- **适配器模式 (Adapter Pattern)**：通过 `IPlatformScheduler`、`IPlatformAudio`、`IPlatformSensor` 实现跨平台底层接口抽象。

---

## 📅 法定节假日配置与同步规范

### 默认同步 URL
应用内置默认节假日同步源为：
```
https://raw.githubusercontent.com/edompeng/alarm/master/core/src/assets/holidays_2026.json
```

### JSON 数据格式 (HolidaySyncModel)
```json
{
  "year": 2026,
  "authority": "General Office of the State Council",
  "holidays": [
    "2026-01-01",
    "2026-01-02",
    "2026-01-03",
    "..."
  ],
  "workdays": [
    "2026-02-15",
    "2026-02-28",
    "..."
  ]
}
```

### 生成与校验节假日配置
项目中提供了自动生成与校验工具：
```bash
# 生成 2026 年法定节假日与调休补班配置
python3 scripts/generate_holiday_config.py --year 2026 -o core/src/assets/holidays_2026.json

# 执行节假日配置校验单元测试
python3 tests/test_holiday_config.py
```

---

## 🛠️ 编译与构建 (Build & Packaging)

### 环境要求
- **Bazel**: 9.x（可通过 [Bazelisk](https://github.com/bazelbuild/bazelisk) 自动管理）
- **JDK**: OpenJDK 17
- **Android SDK**: API 34 (`platforms;android-34`), Build-Tools `34.0.0`
- **C++ 编译器**: 支持 C++20 的 Clang 或 GCC
- **Python**: 3.10+

### 使用 Bazel 构建

```bash
# 1. 编译并打包可直接安装运行的 Release APK (带签名与对齐)
bazel build //app:alarm_release_apk

# 2. 编译并打包 Debug APK
bazel build //app:alarm_debug_apk

# 3. 产物路径
# Release APK: bazel-bin/app/alarm_release_apk.apk
# Debug APK:   bazel-bin/app/alarm_debug_apk.apk
```

### 使用独立脚本构建
也可直接使用项目提供的自动化构建脚本：
```bash
# 构建 Release APK (自动生成签名或使用指定密钥)
bash scripts/build_apk.sh --mode release --output build/alarm-release.apk

# 使用自定义发布签名密钥
export ALARM_RELEASE_KEYSTORE="/path/to/release.keystore"
export ALARM_RELEASE_KEY_ALIAS="mykey"
export ALARM_RELEASE_KEYSTORE_PASSWORD="password"
export ALARM_RELEASE_KEY_PASSWORD="password"
bash scripts/build_apk.sh --mode release --output build/alarm-release.apk
```

---

## 🧪 自动化测试与验证 (Testing & Verification)

项目中包含完备的单元测试，涵盖核心调度、节假日算法、持久化和业务契约：

```bash
# 1. 运行全部 C++ 单元测试 (10 个测试套件)
bazel test --keep_going //...

# 2. 运行 Java 框架解耦策略测试 (7 个测试套件)
bash scripts/test_java.sh

# 3. 运行 Python 节假日配置与 URL 契约测试
python3 -m unittest discover -s tests -p "test_*.py"

# 4. 校验生成的 Release APK 签名完整性
$ANDROID_HOME/build-tools/34.0.0/apksigner verify --verbose bazel-bin/app/alarm_release_apk.apk
```

如需进行模拟器或真机 E2E 验证：
```bash
# 模拟器端到端验证
bash scripts/verify_emulator.sh <emulator-serial>

# 物理设备验证 (支持 Vivo OriginOS / Samsung One UI)
bash scripts/verify_device.sh <device-serial>
```

---

## 🚀 持续集成与全平台发布 (CI/CD & Releases)

项目在 [`.github/workflows/ci.yml`](.github/workflows/ci.yml) 中配置了全自动持续集成与多平台发布流水线：
- **触发条件**：代码推送到 `master`/`main` 分支、发布版本标签（如 `v1.0.0`）或提交 Pull Request。
- **矩阵与并发流水线**：
  1. **Android & Linux 构建与验证 Job**（Ubuntu-latest）：
     - 执行 Python 节假日规则自检与一致性测试；
     - 执行 Java 框架解耦单元测试；
     - 执行 Bazel C++ 核心单元测试；
     - 自动化构建 Android 全架构 Release APK（Universal、ARM64-v8a、ARMv7、x86_64）及 Linux x86_64 原生发行包；
     - 通过 `apksigner` 执行全量签名校验（v1/v2/v3 签名方案）；
     - 执行 Linux 原生 CLI 工具冒烟测试；
     - 归档平台构建产物。
  2. **macOS 跨平台构建 Job**（macOS-latest Apple Silicon）：
     - 编译核心业务层并运行 C++ 测试；
     - 构建 macOS Apple Silicon (`macos-arm64`) 原生发行包与 `alarm-cli` 命令行工具；
     - 执行 macOS 平台冒烟测试。
  3. **GitHub Releases 自动化发布 Job**：
     - 当代码合并至主干或打版本 Tag 时触发；
     - 自动聚合全平台发行包，生成统一的 `SHA256SUMS.txt` 校验和；
     - 自动发布或更新至 [GitHub Releases 页面](https://github.com/edompeng/alarm/releases)，挂载全部主流平台安装与运行包。

---

## 📦 主流平台发行包清单 (Release Packages)

版本号采用 **`<基准Tag>-<东八区日期时间>`** 格式动态生成（例如 `v1.0.0-202610051305`），并在 App 主界面及设置弹窗中实时显示。发布页面提供以下主流平台二进制文件及校验码：

| 发行包文件名 | 适用平台与架构 | 说明 |
|---|---|---|
| `SmartAlarm-v<版本>-android-universal.apk` | Android 11+ (通用) | 官方推荐，内含完整资源与适配层 |
| `SmartAlarm-v<版本>-android-arm64-v8a.apk` | Android 64位 ARM | 针对现代旗舰与主流芯片深度优化 |
| `SmartAlarm-v<版本>-android-armeabi-v7a.apk` | Android 32位 ARM | 适配老旧设备与特定嵌入式硬件 |
| `SmartAlarm-v<版本>-android-x86_64.apk` | Android x86_64 | 适配 Android 模拟器与 x86 桌面设备 |
| `SmartAlarm-v<版本>-linux-x86_64.tar.gz` | Linux x86_64 | 包含 `alarm-cli` 命令行工具、头文件与 2026 日历库 |
| `SmartAlarm-v<版本>-macos-arm64.tar.gz` | macOS (Apple Silicon) | 包含 `alarm-cli` 命令行工具、头文件与 2026 日历库 |
| `SHA256SUMS.txt` | 全平台校验和 | SHA256 安全完整性校验清单 |

---

## 💻 跨平台命令行工具 (alarm-cli)

除了 Android 移动端应用外，项目还提供了独立的跨平台命令行工具 `alarm-cli`，可直接在 Linux / macOS / 终端运行进行日期判别与闹钟计算：

```bash
# 1. 编译 alarm-cli
bazel build //cli:alarm_cli

# 2. 判别指定日期是否为法定节假日或调休工作日
./bazel-bin/cli/alarm_cli check-date 2026-10-01

# 3. 格式化小憩倒计时
./bazel-bin/cli/alarm_cli countdown 25

# 4. 查询天气铃声声景映射
./bazel-bin/cli/alarm_cli weather thunderstorm

# 5. 生成防贪睡算术挑战
./bazel-bin/cli/alarm_cli math-challenge
```
