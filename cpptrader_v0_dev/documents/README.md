# documents 目录说明

## 目录作用

`documents/` 目录存放项目的**文档生成配置**文件，主要用于配置和生成 Doxygen 格式的 API 参考文档。

---

## 文件列表

| 文件名 | 说明 |
|--------|------|
| `Doxyfile` | Doxygen 文档生成工具的配置文件 |

---

## Doxyfile 配置说明

`Doxyfile` 是 Doxygen 工具的主配置文件，定义了如何从头文件和源代码中提取注释并生成 HTML/PDF 格式的 API 文档。

### 主要配置项

| 配置项 | 说明 |
|--------|------|
| `PROJECT_NAME` | 项目名称（显示在文档标题） |
| `INPUT` | 输入文件/目录路径 |
| `RECURSIVE` | 是否递归扫描子目录 |
| `EXTRACT_ALL` | 是否提取所有实体（包括未文档化的） |
| `GENERATE_HTML` | 是否生成 HTML 格式文档 |
| `GENERATE_LATEX` | 是否生成 LaTeX/PDF 格式文档 |
| `OUTPUT_DIRECTORY` | 输出目录路径 |

---

## 使用方法

### 生成文档

```bash
# 进入 documents 目录
cd documents

# 运行 Doxygen
doxygen Doxyfile

# 生成的 HTML 文档位于 documents/html/ 目录
# 用浏览器打开 index.html 查看
```

### CMake 集成生成

```bash
cd build
cmake ..
make doxygen
```

---

## 在线文档

项目已配置自动文档部署，生成的 API 文档可通过以下链接访问：

**https://chronoxor.github.io/CppTrader/index.html**

文档由 GitHub Actions 工作流 `.github/workflows/doxygen.yml` 自动构建和部署到 GitHub Pages。

---

## 代码注释规范

项目中的头文件采用 Doxygen 风格的注释，例如：

```cpp
/*!
    \file market_manager.h
    \brief Market manager definition
    \author Ivan Shynkarenka
    \date 03.08.2017
    \copyright MIT License
*/
```

这种注释格式可被 Doxygen 正确解析并生成结构化的文档页面。
