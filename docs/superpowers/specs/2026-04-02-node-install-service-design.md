# 节点安装服务名与卸载命令设计

## 背景

当前节点安装脚本 `install.sh` 将 agent 固定安装到：

- 安装目录：`/etc/flux_agent`
- systemd 服务：`flux_agent.service`

这导致同一台服务器无法并行安装多个 agent 实例。虽然脚本里已有“卸载”菜单，但它只能卸载固定服务名实例，也无法由面板直接生成对应的卸载命令。

本次需求目标是：

1. 支持为节点配置可自定义的安装服务名。
2. 让同一台服务器可安装多个独立 agent 实例。
3. 支持生成安装、更新、卸载三类命令。
4. 面板保存服务名配置，避免每次手工输入。

## 目标

### 功能目标

- 节点支持保存一个 `installServiceName` 字段。
- 节点安装命令支持显式指定服务名：`-n <service_name>`。
- 脚本支持命令行更新：`--update`。
- 脚本支持命令行卸载：`--uninstall`。
- 面板安装弹窗展示三条命令：
  - 安装
  - 更新
  - 卸载

### 非目标

- 不引入“一个节点绑定多个安装实例”的新数据模型。
- 不实现服务实例列表、远程探测、远程卸载结果回传。
- 不修改 panel 的 Docker 部署方式。

## 方案选择

### 方案 1：只改脚本

- 脚本支持 `-n`、`--update`、`--uninstall`
- 面板不保存服务名

优点：

- 改动最小

缺点：

- 面板无法生成完整命令
- 用户需要自己记住服务名

### 方案 2：节点保存安装服务名，由面板生成完整命令

- `node` 表新增 `install_service_name`
- 脚本支持 `-n`、`--update`、`--uninstall`
- 面板展示安装、更新、卸载三条命令

优点：

- 配置闭环完整
- 同机多实例使用成本低
- 用户无需手改命令

缺点：

- 需要联动脚本、后端、前端、表结构

### 方案 3：单独建“节点安装实例”模型

- 每个节点支持多个安装实例记录

优点：

- 扩展性最好

缺点：

- 明显超出当前需求范围

### 结论

采用方案 2。

## 数据模型

### `node` 表新增字段

- 字段名：`install_service_name`
- 类型：`VARCHAR(100)` / SQLite 中按 `VARCHAR(100)` 存储
- 默认值：空

### 兼容策略

- 老节点 `install_service_name` 为空时，后端生成命令使用默认值 `flux_agent`
- 前端编辑节点时，若字段为空则显示 `flux_agent`

## 服务名规则

服务名必须满足：

- 非空
- 长度 1 到 100
- 仅允许：
  - 字母
  - 数字
  - `-`
  - `_`
  - `.`

推荐正则：

`^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$`

限制原因：

- 避免 systemd 文件名和目录路径注入风险
- 保证 shell 命令拼接简单稳定

## 后端设计

### DTO 与实体

以下结构新增字段 `installServiceName`：

- `Node`
- `NodeDto`
- `NodeUpdateDto`

### 创建与更新

- 创建节点时保存 `installServiceName`
- 更新节点时允许修改 `installServiceName`
- 若前端未传，则后端写入空值或默认值逻辑统一收口在 service 层

### 安装命令接口

当前 `/api/v1/node/install` 返回一条字符串。

改为返回对象：

```json
{
  "serviceName": "flux_agent_node1",
  "installCommand": "curl ... && ./install.sh -n flux_agent_node1 -a ... -s ...",
  "updateCommand": "curl ... && ./install.sh --update -n flux_agent_node1",
  "uninstallCommand": "curl ... && ./install.sh --uninstall -n flux_agent_node1"
}
```

### 命令构造规则

- 安装命令包含：
  - `-n`
  - `-a`
  - `-s`
- 更新命令包含：
  - `--update`
  - `-n`
- 卸载命令包含：
  - `--uninstall`
  - `-n`

注意：

- 更新和卸载不需要再次传 `-a`、`-s`
- 安装命令仍然下载 release 中的 `install.sh`

## 脚本设计

### 新增参数

- `-n <service_name>`
- `--update`
- `--uninstall`

### 默认值

- 未传 `-n` 时默认使用 `flux_agent`

### 目录与文件映射

以服务名 `flux_agent_node1` 为例：

- 安装目录：`/etc/flux_agent_node1`
- 二进制：`/etc/flux_agent_node1/flux_agent`
- 配置文件：`/etc/flux_agent_node1/config.json`
- gost 配置：`/etc/flux_agent_node1/gost.json`
- systemd：`/etc/systemd/system/flux_agent_node1.service`

### 安装逻辑

- 若目标服务已存在，先停止并禁用同名服务
- 仅覆盖当前服务名对应目录和 service 文件
- 不扫描或处理其他服务名实例

### 更新逻辑

- 仅更新指定服务名实例
- 若对应安装目录或 service 文件不存在，提示“未安装该服务名实例”

### 卸载逻辑

- 仅卸载指定服务名实例
- 删除：
  - 同名 service 文件
  - 同名安装目录
- 不删除其他实例目录

### 交互模式

- 无命令参数时保留菜单
- 菜单中的安装/更新/卸载操作，如果未传 `-n`，交互输入服务名
- 菜单默认提示值为 `flux_agent`

## 前端设计

### 节点表单

节点新增/编辑弹窗增加字段：

- 标签：`安装服务名`
- 默认值：`flux_agent`

校验：

- 必填
- 使用与后端一致的正则约束

### 安装命令弹窗

当前仅展示一条安装命令。

改为展示三段：

1. 安装命令
2. 更新命令
3. 卸载命令

每段都支持单独复制。

### 展示原则

- 弹窗标题仍使用节点名
- 同时展示当前服务名，避免用户复制错实例

## 迁移设计

### SQLite 启动迁移

启动时检查 `node` 表是否缺少 `install_service_name`。

若缺失：

```sql
ALTER TABLE node ADD COLUMN install_service_name VARCHAR(100);
```

不强制回填旧数据，由运行时默认值逻辑兜底。

### `schema.sql`

新安装环境的 `node` 表结构直接包含 `install_service_name`。

## 风险与边界

### 风险 1：服务名注入

处理方式：

- 前后端双重正则校验
- 脚本中引用路径时统一加双引号

### 风险 2：误卸载

处理方式：

- 卸载命令只针对指定服务名
- 保留二次确认提示
- 提示将删除的具体 service 文件和目录

### 风险 3：老节点无服务名

处理方式：

- 后端和前端统一兜底为 `flux_agent`

## 测试计划

### 后端

- 节点创建时保存 `installServiceName`
- 节点更新时修改 `installServiceName`
- `/api/v1/node/install` 返回三条命令
- 老节点 `installServiceName = null` 时返回默认服务名 `flux_agent`

### 脚本

- `-n flux_agent_a -a ... -s ...` 安装成功
- `--update -n flux_agent_a` 仅更新实例 A
- `--uninstall -n flux_agent_a` 仅卸载实例 A
- 同机安装 `flux_agent_a` 和 `flux_agent_b` 后互不影响

### 前端

- 节点表单可填写并保存服务名
- 安装命令弹窗正确显示三条命令
- 复制按钮复制的命令与当前节点服务名一致

## 实施顺序

1. 扩展 `node` 表结构与启动迁移
2. 扩展实体、DTO、service 校验
3. 改造 `/api/v1/node/install` 返回结构
4. 改造 `install.sh`
5. 改造前端节点表单与安装命令弹窗
6. 运行后端测试、前端构建、脚本手动验证
