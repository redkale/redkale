
## 简介
&nbsp;&nbsp;&nbsp;&nbsp;Redkale (中文名: 红菜苔，一种湖北特产蔬菜) 是基于Java 11全新的微服务框架， 包含HTTP、WebSocket、TCP/UDP、数据序列化、数据缓存、依赖注入等功能。 本框架致力于简化集中式和微服务架构的开发，在增强开发敏捷性的同时保持高性能。

主要特点：:
* 大量使用Java 8+新特性（接口默认值、Stream、Lambda、内置的ASM、HttpClient等）
* 提供HTTP服务，同时内置JSON功能与限时缓存功能
* TCP层完全使用NIO，并统一TCP与UDP的接口换
* 提供分布式与集中式部署的无缝切换
* 提供类似JPA功能，包含数据缓存自动同步、分表分库与简洁的数据层操作接口
* 可以动态修改已依赖注入的资源

## 设计理念
&nbsp;&nbsp;&nbsp;&nbsp;作为一个全新的微服务框架，Redkale在接口定义上使用了Java 8以上版本的大量新特性，接口有默认实现、接口带静态方法、重复注解等特性，同时在设计上与主流框架有很大不同。Redkale是按组件形式设计的，而非以容器为主，几乎每个子包都是能提供独立功能的组件。如Tomcat是按容器设计的，所有web资源/配置由Tomcat控制，开发者很能难控制到Tomcat内部，而Redkale的HTTP服务只是个组件，开发者既可以自己启动和配置HttpServer，也可以把Redkale当成容器通过Redkale进程来初始化服务。Spring的Ioc容器也是如此，Redkale提供的依赖注入仅通过ResouceFactory一个类来控制，非常轻量，并且可动态更改已注入的资源。Spring提倡控制反转思想，而自身的容器却让开发者很难控制。Redkale是一个既能以组件形式也能以容器形式存在的框架。从整体上看，Redkale的架构分两层：接口和默认实现。开发者若想替换掉Redkale内置的HTTP服务而使用符合JavaEE规范的HttpServlet, 可以采用自定义协议基于JSR 340(Servlet 3.1)来实现自己的HTTP服务；若想使用Hibernate作为数据库操作，可以写一个自己的DataSource实现类；JSON的序列化和反序列化也可以使用第三方的实现；Memcached或Redis也可以作为另一个CacheSource的实现替换Redkale的默认实现。这其实包含了控制反转的思想，让框架里的各个组件均可让开发者控制。
&nbsp;&nbsp;&nbsp;&nbsp;与主流框架比，功能上Redkale显得很简单，这体现了Redkale的简易性，而并非是不足，从一个良好的设计习惯或架构上来看，有些常用功能是不需要提供的，如Redkale的HTTP服务不支持JSP, JSP其实算是一个落后的技术，现在是一个多样化终端的时代，终端不只局限于桌面程序和PC浏览器，还有原生App、混合式App、微信端、移动H5、提供第三方接口等各种形式的终端，这些都不是JSP能方便兼顾的，而HTTP+JSON作为通用性接口可以避免重复开发，模版引擎的功能加上各种强大的JS框架足以取代JSP。Redkale在功能上做了筛选，不会为了迎合主流而提供，而是以良好的设计思想为指导。这是Redkale的主导思维。

## 文档目录
* [快速入门](docs/quick-start.md)
* [基本Cache API](docs/CacheAPI.md)
* [通过CacheManager注解创建```Cache```实例](docs/CreateCache.md)
* [通过注解实现方法缓存](docs/MethodCache.md)
* [配置详解](docs/Config.md)
* [高级Cache API](docs/AdvancedCacheAPI.md)
* Redis支持(redis客户端任选一即可)
  * [使用jedis客户端连接redis](docs/RedisWithJedis.md)
  * [使用lettuce客户端连接redis](docs/RedisWithLettuce.md)
  * Spring data redis (文档待写)
* [内存缓存```LinkedHashMapCache```和```CaffeineCache```](docs/Embedded.md)
* [统计](docs/Stat.md)
* [Builder](Builder.md)：未使用Spring4（或未使用Spring）的时候，或通过Builder手工构造```Cache```
* [开发者文档](docs/DevNote.md)
* [FAQ](docs/FAQ.md)

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<b>详情请访问:&nbsp;&nbsp;&nbsp;&nbsp;<a href='https://redkale.org' target='_blank'>https://redkale.org</a></b>

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<b>基本文档:&nbsp;&nbsp;&nbsp;&nbsp;<a href='https://redkale.org/articles.html' target='_blank'>https://redkale.org/articles.html</a></b>

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<b>欢迎加入Redkale QQ群:  527523235</b>
&nbsp;
