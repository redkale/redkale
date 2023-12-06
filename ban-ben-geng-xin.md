# 版本更新

## v2.7.0 - 2022-07-07

### Fixed

* 修复 HttpResponse.finish 结果 status=404 时按 200 输出的 bug&#x20;
* 修复 HttpMessageLocalClient 创建 request 时没有赋值给 currentUserid 值&#x20;
* 修复 Rest.createRestServlet 带特定泛型问题&#x20;
* 修复 Convert 模块中父类含 public field，subclass 不传父类会导致 NoSuchFieldError 的 bug&#x20;
* 修复 ApiDocCommand 在没有运行时不能生成 doc 的 bug&#x20;
* 修复 JsonWriter.writeWrapper 按 latin1 编码写的 bug&#x20;
* 修复 JsonDynEncoder 在定制字段情况下会被全量字段的动态类覆盖的 bug

### Changed

* 优化 PrepareServlet 中 HttpRender 的初始化顺序&#x20;
* 日志支持 java.util.logging.ConsoleHandler.denyreg 配置&#x20;
* FilterColumn 支持 least=0 时空字符串也参与过滤&#x20;
* HttpRequest 兼容参数名为空字符串&#x20;
* 移除 CryptColumn、CryptHandler 功能&#x20;
* PrepareServlet 更名为 DispatcherServlet&#x20;
* @WebServlet 合并 url

### Added

* 增加 ConvertCoder 功能，可以自定义字段的序列化&#x20;
* 增加 JsonMultiDecoder、JsonMultiObjectDecoder、OneOrList 功能&#x20;
* JsonConvert 全面兼容 JSON5&#x20;
* 增加 redkale 命令行&#x20;
* 增加 HttpRpcAuthenticator 功能&#x20;
* MessageAgent 增加配置 MessageCoder 功能&#x20;
* 实现 LoggingSearchHandler 功能&#x20;
* ConvertFactory 增加 mapFieldFunc、ignoreMapColumns 功能&#x20;
* 增加 PropertiesAgent 功能
* 增加链路 ID Traces&#x20;
* 增加 ResourceListener.different 功能&#x20;
* 增加 Environment 类&#x20;
* 增加 RestLocale 功能
