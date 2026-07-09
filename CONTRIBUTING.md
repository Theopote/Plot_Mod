# 贡献指南 / Contributing Guide

感谢你对 Plot 项目的关注！以下是参与贡献的基本流程。

## 开发环境 / Development Setup

1. 安装 **JDK 21** 或更高版本
2. 克隆仓库并进入项目目录
3. 运行 `gradlew runClient` 启动开发客户端
4. 使用你喜欢的 IDE（推荐 IntelliJ IDEA）导入 Gradle 项目

## 代码规范 / Code Style

- 遵循项目现有的包结构与命名约定
- 公共 API 放在 `com.plot.api` 包下
- UI 相关代码放在 `com.plot.ui` 包下
- 核心逻辑放在 `com.plot.core` 包下
- 新增功能请保持与现有模块的依赖注入风格一致

## 提交规范 / Commit Guidelines

- 使用清晰、简洁的提交信息，说明「为什么」而非仅描述「做了什么」
- 一个提交对应一个逻辑变更，避免将不相关的修改混在一起
- 确保 `gradlew build` 能成功通过后再提交 PR

## Pull Request 流程 / PR Process

1. 从 `main`（或当前主分支）创建功能分支
2. 完成修改并本地验证
3. 提交 PR，简要描述变更内容与测试方式
4. 等待代码审查

## 报告问题 / Reporting Issues

提交 Issue 时请尽量包含：

- Minecraft、Fabric Loader、Plot 版本号
- 复现步骤
- 预期行为与实际行为
- 相关日志或截图（如有）

## 许可证 / License

贡献的代码将按项目 [MIT License](LICENSE) 发布。
