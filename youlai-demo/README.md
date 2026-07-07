# youlai-demo

`youlai-demo` 是一个用于演示项目实现方式的独立 Spring Boot 模块。

当前包含：

- `DemoApplication`：模块启动入口
- `DemoProjectController`：REST 示例接口
- `DemoProjectService`：业务服务接口
- `DemoProjectServiceImpl`：MyBatis-Plus 落库版业务实现
- `DemoProjectForm` / `DemoProjectVO`：入参与出参模型
- `docs/sql/youlai_demo.sql`：MySQL 建库建表示例数据脚本

数据库：

```bash
mysql -uroot -p < docs/sql/youlai_demo.sql
```

默认连接配置：

- 数据库：`youlai_demo`
- 地址：`127.0.0.1:3306`
- 用户名：`root`
- 密码：`123456`

可通过环境变量覆盖：`DEMO_DB_HOST`、`DEMO_DB_PORT`、`DEMO_DB_NAME`、`DEMO_DB_USERNAME`、`DEMO_DB_PASSWORD`。

启动：

```bash
mvn -pl youlai-demo -am spring-boot:run
```

接口示例：

```bash
curl http://localhost:9400/api/v1/demo/projects
```
