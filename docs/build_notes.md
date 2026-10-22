# FFmpeg 静态库：为什么要绕 WSL，以及怎么验

为什么绕 WSL：media3 官方的 FFmpeg 构建脚本明写**不支持在 Windows 上构建**，
而且官方脚本硬编 4 个 ABI，这里改成只跑 arm64 一趟。
验证有没有编进去：看 `third_party/ffmpeg/config_components.h`（FFmpeg 6 起组件开关不在 `config.h`），
再 `llvm-nm` 数一下 `ff_dca_decoder` 这类符号在不在 `.a` 里。

