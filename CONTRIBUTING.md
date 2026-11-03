# Contributing

English | [简体中文](CONTRIBUTING.zh_cn.md)

Building and running require JDK 21, with `JAVA_HOME` set to that JDK.

## Build and test

Windows:
```powershell
./gradlew.bat test build
```
Linux:
```sh
bash ./gradlew test build
```

Choose a configuration for regression GameTests as needed:
```powershell
./gradlew.bat runGameTestServer -PwithoutJei
./gradlew.bat runGameTestServer -PwithKubejs
```

KubeJS-specific test example:
```powershell
./gradlew.bat runGameTestServer -PwithKubejs '-PgraphAssembly.testNamespaces=graph_assembly_examples' '-PgraphAssembly.expectedRecipes=test:kube_native_heat'
```

This task stages the scripts in `src/gametest/kubejs` automatically; CreateJS-specific assertions are skipped when CreateJS is not installed. Run the client with `./gradlew.bat runClient`, and the test client with `./gradlew.bat runVerificationClient`.

Regular GameTests and `-PwithKubejs` share the `run/gametest` configuration directory. If a KubeJS-specific scenario finds scripts staged from an earlier run while KubeJS is disabled, that scenario will not take effect; GameTests can still run.

## Directories

- `src/main/java`: production mod code.
- `src/main/resources`: mod resources and data.
- `src/test`: JUnit unit tests.
- `src/gametest`: GameTests, test client, and test resources; this source set is not packaged in the release JAR.
- `examples`: recipe-author examples.
- `docs`: player, recipe-author, and resource-pack guides and references.

When changing recipe rules, machine execution, or script behavior, add the corresponding regression tests.
