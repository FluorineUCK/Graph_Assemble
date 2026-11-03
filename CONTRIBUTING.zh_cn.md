# 贡献指南

[English](CONTRIBUTING.md) | 简体中文

构建与运行需要 JDK 21，并设置 `JAVA_HOME` 指向该 JDK。

## 构建与测试

Windows：

```powershell
./gradlew.bat test build
```

Linux：

```sh
bash ./gradlew test build
```

回归 GameTest 可按需要选择配置：

```powershell
./gradlew.bat runGameTestServer -PwithoutJei
./gradlew.bat runGameTestServer -PwithKubejs
```

KubeJS 专项测试示例：

```powershell
./gradlew.bat runGameTestServer -PwithKubejs '-PgraphAssembly.testNamespaces=graph_assembly_examples' '-PgraphAssembly.expectedRecipes=test:kube_native_heat'
```

此任务会自动暂存 `src/gametest/kubejs` 脚本；未安装 CreateJS 时会跳过 CreateJS 专属断言。运行客户端使用 `./gradlew.bat runClient`，运行测试客户端使用 `./gradlew.bat runVerificationClient`。

普通 GameTest 与 `-PwithKubejs` 共用 `run/gametest` 配置目录。KubeJS 专项场景若遇到此前暂存的脚本但未启用 KubeJS，该场景不会生效，GameTest 仍可运行。

## 目录

- `src/main/java`：模组生产代码。
- `src/main/resources`：模组资源与数据。
- `src/test`：JUnit 单元测试。
- `src/gametest`：GameTest、测试客户端及测试资源；该源集不打入发布 JAR。
- `examples`：配方作者示例。
- `docs`：玩家、配方作者与资源包指南及参考资料。

提交改动时，为涉及的配方规则、机器执行或脚本行为补充相应回归测试。
