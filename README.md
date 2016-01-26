<h1>项目介绍</h1>
<p>
   &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;RedKale (中文名: 红菜苔，湖北武汉的一种特产蔬菜) 是基于Java 8全新的微服务框架， 包含HTTP、WebSocket、TCP/UDP、数据序列化、数据缓存、依赖注入等功能。 本框架致力于简化集中式和微服务架构的开发，在增强开发敏捷性的同时保持高性能。
</p>
<strong>RedKale 有如下主要特点：</strong>
<ol>
<li>大量使用Java 8新特性（接口默认值、Stream、Lambda、JDk8内置的ASM等）</li>
<li>提供HTTP服务，同时内置JSON功能与限时缓存功能</li>
<li>TCP层完全使用NIO.2，并统一TCP与UDP的接口换</li>
<li>提供分布式与集中式部署的无缝切换</li>
<li>提供类似JPA功能，并包含数据缓存自动同步与简洁的数据层操作接口</li>
<li>可以动态修改已依赖注入的资源</li>
</ol>

&nbsp;&nbsp;&nbsp;由于RedKale使用了JDK 8 内置的ASM包，所以需要在源码工程中的编译器选项中加入： <b>-XDignore.symbol.file=true</b>

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<h5>详情请访问:&nbsp;&nbsp;&nbsp;&nbsp;<a href='http://www.redkale.org' target='_blank'>http://www.redkale.org</a></h5>
