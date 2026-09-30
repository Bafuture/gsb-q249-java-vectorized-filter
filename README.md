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
mvn -q verify        # 或 ./mvnw -q verify（需能下载 Maven 发行版）
```

## 实现说明

### 架构

包 `com.example.gsb.vectorized`（`src/main/java`）：

| 类 | 职责 |
|----|------|
| `ColumnVector` | 单列一批数据：`long[]`/`double[]`/`String[]` 定长或字符串值数组 + 位图 `nullMap` 记录 NULL |
| `Table` / `ColumnBatch` | 不可变列式表；`Table.batches(n)` 把行区间 `[0, rowCount)` 连续、保序、不重不漏地切成批 |
| `SelectionVector` | 位压缩选择向量：每行两位（`match`/`known`）承载三值结果，`and`/`or`/`not` 为纯按位字级合并，`concat` 跨批拼接 |
| `FilterPredicate` 及实现 | 谓词树：`ComparisonPredicate`（GT/LT/EQ/BETWEEN）、`NullPredicate`（IS [NOT] NULL）、`LogicalPredicate`（AND/OR/NOT），工厂见 `Predicates` |
| `VectorizedFilter` | 向量化引擎：逐批求值 → 选择向量拼接 → 按 TRUE 位升序物化 |
| `RowWiseFilter` | 逐行参照实现：同一谓词树走 `RowView` 逐行求值，作为正确性基准 |
| `MaterializedTable` | 物化结果：只含谓词为 TRUE 的行，保持源表行序，可回溯源行号 |

### 空值与三值逻辑（3VL）

遵循 SQL 语义：任何比较谓词（`>`、`<`、`=`、区间）遇到 NULL 结果都是
**UNKNOWN**；`IS NULL` / `IS NOT NULL` 永远产生确定的 TRUE/FALSE。

```
AND:  FALSE 优先   —— FALSE AND UNKNOWN = FALSE，UNKNOWN AND TRUE = UNKNOWN
OR :  TRUE  优先   —— TRUE  OR  UNKNOWN = TRUE， UNKNOWN OR FALSE = UNKNOWN
NOT:  UNKNOWN 不变 —— NOT UNKNOWN = UNKNOWN
```

选择向量用两位编码：`known=0` 即 UNKNOWN（此时 `match` 恒为 0），
`known=1` 时 `match` 区分 TRUE/FALSE。`and`/`or`/`not` 直接对 `long[]`
字做位运算，无需逐行分支。物化时只有 TRUE 行被输出（SQL WHERE 语义），
FALSE 与 UNKNOWN 行都被丢弃；需要 NULL 行时用 `isNull` 谓词显式选择。

### 批次边界

谓词都是针对常量的行级比较，因此每批独立求值即可。批是对行区间的连续
切分，`SelectionVector.concat` 按批序把各批位向量精确拼回全表向量：
区间边界值（含等于上下界的行）无论落在哪个批、是否正好在批切点上，
都恰好被判断一次，不漏不重。`BatchBoundaryTest` 用 18 种批大小（含
1、素数、跨 64 位字边界、整除/不整除）验证了这一点。

### 用法示例

```java
Table table = Table.builder()
        .addColumn("id",    ColumnVector.ofLong(ids, idNulls))
        .addColumn("score", ColumnVector.ofDouble(scores, scoreNulls))
        .addColumn("name",  ColumnVector.ofString(names, nameNulls))
        .build();

FilterPredicate p = Predicates.and(
        Predicates.betweenLong("id", 100, 999),
        Predicates.or(Predicates.gtDouble("score", 60.0),
                      Predicates.isNull("score")));

MaterializedTable out = new VectorizedFilter(4096).filter(table, p);
```

### 正确性与性能

- `LargeDataCorrectnessTest`：20 万行随机数据（含 NULL），9 组谓词 × 5 种
  批大小，向量化结果与逐行参照在**完整三值向量**与物化结果上逐一相等。
- `VectorizedVsRowWisePerformanceTest`：200 万行、3 轮预热 + 6 轮计时，
  硬校验两种实现结果完全一致，并打印/落盘耗时对比（`target/perf-results.txt`）。
  本机参考：简单 BETWEEN 约 2.0x，复合谓词约 2.5–2.8x 加速。

## 任务提示词

以下为本题完整的 User Prompt 原文，两次执行必须使用完全相同的文本。

我们的列式数据逐行判断谓词太慢，希望按批做向量化过滤并输出选择向量。请从零实现一个列式向量化过滤组件。仓库目前只有一个空的 Maven 工程（pom.xml 只声明 JUnit 5 与 AssertJ）。要求：1) 数据按列存储，支持定长数值列与字符串列；2) 支持把比较谓词（大于、小于、等于、区间）应用到整列，产出布尔选择向量；3) 支持选择向量合并：多个谓词的与或非运算按位合并，并说明空值与 null 的三值逻辑处理；4) 支持根据选择向量物化结果：只输出被选中的行，且保持原有顺序；5) 支持批次边界：跨批的区间条件要能正确切分，不得漏行或重复；6) 正确性对照：与逐行判断的结果完全一致，需在大批量数据上验证；7) 给出向量化与逐行方案在相同数据量下的耗时对比；8) 测试覆盖各谓词、三值逻辑、选择向量合并、物化顺序、跨批边界与性能对比；`mvn -q verify` 一条命令跑通。

## 提交要求

1. 在本仓库中完成提示词要求的全部内容。
2. `./mvnw -q verify` 必须通过。
3. 完成后在所属分支（A 或 B）上提交，产物快照的父提交必须是初始环境快照。
