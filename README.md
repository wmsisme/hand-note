# 随手笔记 · Hand Note

一个 Android 个人笔记 App：登录后管理自己的笔记，每条笔记可以带**图片**和**录音**附件，数据存在 Room 数据库里，重启不丢。

## 功能

- **用户系统** —— 注册 / 登录，用 `userId` 做多用户数据隔离
- **笔记 CRUD** —— 列表页 RecyclerView + LiveData 自动刷新；明细页用 `noteId` 区分新建（`-1`）与编辑（`>0`）；长按列表项或点删除按钮删除，**二次确认**
- **图片附件** —— 系统选择器选图后把二进制原样复制进应用内部目录（保留 GIF 等格式），Glide 加载展示；支持查看原图、保存到相册、删除
- **录音附件** —— MediaRecorder 录 AAC/M4A，一条笔记可挂多条；MediaPlayer 播放控制，UI 动态构建；`onPause` 自动停止录音与播放
- **页面组织** —— 全部用 Fragment 实现，可从明细页返回列表页

## 技术栈

Java · Room · MVVM（ViewModel + LiveData）· RecyclerView · Glide · FileProvider · MediaRecorder / MediaPlayer · Gradle Version Catalog

## 结构

```
app/src/main/java/com/caodaming/hand_note/
├── LoginActivity / MainActivity / NoteDetailActivity    三个入口 Activity
├── fragment/    NoteListFragment（列表）、NoteDetailFragment（明细，含录音 UI 动态构建）
├── viewmodel/   NoteViewModel（自定义 Factory 注入 userId）
├── data/        Room：AppDatabase / Note / NoteDao / User / UserDao
└── adapter/     NoteAdapter（列表刷新）
```

路由：`LoginActivity → MainActivity(NoteListFragment) → NoteDetailActivity(NoteDetailFragment)`，
靠 Intent / Bundle 传递 `userId` 与 `noteId`。

## 构建

```bash
./gradlew assembleDebug
```

需要 Android SDK —— 在 `local.properties` 里自行配置 `sdk.dir`（该文件不入库）。

## 已知不足

以下来自开发时的项目总结，如实保留：

| 等级 | 问题 | 说明 |
|------|------|------|
| 严重 | 密码明文存储 | 无哈希 / 加密，数据库泄漏即暴露所有密码 |
| 严重 | `fallbackToDestructiveMigration` | 数据库升级时直接删旧库，生产环境会丢数据 |
| 功能 | 录音文件定位不可靠 | 靠「目录中最新的文件」定位，并发场景可能取错 |
| 功能 | 短录音静默失败 | `RuntimeException` 被静默捕获，用户无任何提示 |
| 功能 | 线程池未关闭 | LoginActivity 和 ViewModel 里的 `ExecutorService` 没有 `shutdown()` |
| 功能 | 路径分隔符不够健壮 | 多条录音用 `\|\|` 拼在一个字段里，宜改用 JSON 数组或 TypeConverter |
| 体验 | 列表无附件标识 | 看不出哪条笔记带图片 / 带录音 |
| 体验 | 编辑页无脏数据确认 | 返回时未提示保存 |

后续可继续做：笔记搜索与标签分类、密码加盐哈希（SHA-256 + Salt）、SQLCipher 加密数据库、DI（Hilt）、迁移到 Kotlin + 协程或 Compose 重构。

## 文档

- [`docs/课程要求.txt`](docs/课程要求.txt) —— 原始需求说明
- [`docs/项目总结.md`](docs/项目总结.md) —— 实现思路、心得与不足
