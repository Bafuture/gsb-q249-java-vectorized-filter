# 列式向量化过滤组件

Pair-wise GSB 标注任务仓库（第 16 批 / 249）。

| 项目 | 内容 |
|------|------|
| 任务类型 | Feature 迭代 |
| 任务难度 | 困难 |
| 语言/框架 | Java, Maven, JUnit 5 |
| 环境可复现等级 | 无外部依赖 |
| 构建方式 | Maven（含 mvnw wrapper，无需本机安装 Maven） |

> 本仓库是**初始环境快照**：只有工程骨架，不含任何实现代码。
> 分支说明：`main` 为初始环境；`A`、`B` 为两次独立执行各自的工作分支，均从 `main` 的同一个提交拉出。

## 运行方式

```bash
./mvnw -q verify
```

## 任务提示词

以下为本题完整的 User Prompt 原文，两次执行必须使用完全相同的文本。

我们的列式数据逐行判断谓词太慢，希望按批做向量化过滤并输出选择向量。请从零实现一个列式向量化过滤组件。仓库目前只有一个空的 Maven 工程（pom.xml 只声明 JUnit 5 与 AssertJ）。要求：1) 数据按列存储，支持定长数值列与字符串列；2) 支持把比较谓词（大于、小于、等于、区间）应用到整列，产出布尔选择向量；3) 支持选择向量合并：多个谓词的与或非运算按位合并，并说明空值与 null 的三值逻辑处理；4) 支持根据选择向量物化结果：只输出被选中的行，且保持原有顺序；5) 支持批次边界：跨批的区间条件要能正确切分，不得漏行或重复；6) 正确性对照：与逐行判断的结果完全一致，需在大批量数据上验证；7) 给出向量化与逐行方案在相同数据量下的耗时对比；8) 测试覆盖各谓词、三值逻辑、选择向量合并、物化顺序、跨批边界与性能对比；`mvn -q verify` 一条命令跑通。

## 提交要求

1. 在本仓库中完成提示词要求的全部内容。
2. `./mvnw -q verify` 必须通过。
3. 完成后在所属分支（A 或 B）上提交，产物快照的父提交必须是初始环境快照。

## 实现说明（列式向量化过滤组件）

包：`com.example.gsb.vectorized`，零外部依赖（仅 JUnit 5 / AssertJ 测试）。

### 核心类型

| 类型 | 职责 |
|------|------|
| `ColumnVector` / `LongColumnVector` / `DoubleColumnVector` / `StringColumnVector` | 列式存储：定长数值列用基本类型数组（`long[]`/`double[]`）+ 显式 null 位图，字符串列用 `String[]`（元素为 `null` 即 NULL）。暴露原始数组供向量化循环零装箱扫描。 |
| `ComparisonPredicate`（`Predicates` 工厂） | 比较谓词：`greaterThan` / `lessThan` / `equalTo` / `between`（闭区间）。按列类型分派到基本类型内层循环，一次连续扫描产出选择向量。 |
| `SelectionVector` | 逐行的三态选择向量（`byte[]`），支持 `and` / `or` / `not` 按位合并、`toRowIndexes()` 物化取行。 |
| `RowBatch` | 一批同长、保序的列；`materialize` 按选择向量 gather 出行，`split` 做不重不漏的批次切分。 |
| `VectorizedFilter` | 向量化入口：`select`（产出选择向量）、`filter`（求值+物化）、`filterBatches`（逐批过滤）。 |
| `RowWiseFilter` | 逐行参考实现（逐行装箱、逐行判定），作为正确性 oracle 与性能基线。 |

### 空值与 NULL 的三值逻辑

NULL 参与比较时结果既不是 true 也不是 false，而是 **UNKNOWN**（SQL 三值逻辑）。选择向量每个位置是 `FALSE(0)` / `UNKNOWN(1)` / `TRUE(2)` 三态，编码使连接词退化为无分支的逐元素 min/max：

| AND | T | F | U |   | OR | T | F | U |
|-----|---|---|---|---|----|---|---|---|
| **T** | T | F | U |   | **T** | T | T | T |
| **F** | F | F | F |   | **F** | T | F | U |
| **U** | U | F | U |   | **U** | T | U | U |

`NOT` 翻转 T/F，`NOT UNKNOWN = UNKNOWN`。物化时只有 `TRUE` 行被输出，`FALSE` 与 `UNKNOWN` 都被排除——与 SQL `WHERE` 语义一致，因此 `NOT(col > 5)` 不会把 NULL 行“翻”进结果集。

### 批次边界

谓词是行局部的：`RowBatch.split(n)` 把整批切成连续、不重叠的半开区间 `[offset, offset+length)`，对每个切片独立求值、按批顺序拼接物化结果，与在整批上过滤完全等价——每行恰好访问一次，不会漏行或重复。区间谓词（`BETWEEN`）同样逐行独立，不需要跨行状态，因此跨批边界（包括边界行恰好命中、行数不能被批大小整除）行为不变。

### 用法示例

```java
LongColumnVector id    = LongColumnVector.of(0, 1, 2, 3);
LongColumnVector value = new LongColumnVector(new long[]{7, 42, 1, 9}, new boolean[4]);
RowBatch batch = new RowBatch(Map.of("id", id, "value", value));

// WHERE value > 5 AND value < 10
SelectionVector low  = Predicates.greaterThan(5L).evaluate(batch.column("value"));
SelectionVector high = Predicates.lessThan(10L).evaluate(batch.column("value"));
RowBatch result = VectorizedFilter.filter(batch, low.and(high)); // 行 0、3，顺序保持

// 跨批
List<RowBatch> parts = VectorizedFilter.filterBatches(batch.split(1024), "value",
        Predicates.between(5L, 10L));
```

### 正确性与性能

- `PredicateTest`：四种谓词 × long/double/String 列、NULL→UNKNOWN、非法参数。
- `SelectionVectorTest`：AND/OR/NOT 完整三值真值表、多谓词合并、长度校验。
- `MaterializationTest`：只输出 TRUE 行、原顺序、NULL 被排除、空结果保留 schema。
- `BatchBoundaryTest`：奇数总行数 `100003` × 多种批大小（含 1、不能整除、超过总行数），验证无重复/漏行；边界行不丢不重。
- `CorrectnessTest`：100 万行随机数据（含 5% NULL）上，所有谓词及 AND/OR/NOT 组合的选择向量与物化结果与逐行实现逐行一致；4096 大小逐批拼接结果一致。
- `PerformanceTest`：200 万行、5 次取最优、先预热。参考机实测（数字随机器浮动，仅打印不断言）：

```
[perf] BETWEEN (single predicate), rows=2,000,000
[perf] evaluate only  -> vectorized: ~5.2 ms (~380 M rows/s) | row-wise: ~8.6 ms (~234 M rows/s) | ~1.7x
[perf] incl. material -> vectorized: ~14 ms  (~143 M rows/s) | row-wise: ~18 ms  (~110 M rows/s) | ~1.3x
[perf] value > lo AND value < hi (merged), rows=2,000,000
[perf] evaluate only  -> vectorized: ~5.5 ms (~363 M rows/s) | row-wise: ~11 ms  (~174 M rows/s) | ~2.1x
[perf] incl. material -> vectorized: ~14 ms  (~141 M rows/s) | row-wise: ~21 ms  (~97 M rows/s)  | ~1.5x
```

纯谓词求值（热点循环：无装箱、无虚分派、顺序访存）约 1.7–2.1x；端到端受 gather 物化（逐列数组拷贝，两种实现共享）摊薄后约 1.3–1.5x。

```bash
mvn -q verify      # 或 ./mvnw -q verify
```
