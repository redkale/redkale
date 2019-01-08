<h1>项目介绍</h1>
<p>
   &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Redkale (中文名: 红菜苔，一种湖北特产蔬菜) 是基于Java 8全新的微服务框架， 包含HTTP、WebSocket、TCP/UDP、数据序列化、数据缓存、依赖注入等功能。 本框架致力于简化集中式和微服务架构的开发，在增强开发敏捷性的同时保持高性能。
</p>
<strong>RedKale 有如下主要特点：</strong>
<ol>
<li>大量使用Java 8新特性（接口默认值、Stream、Lambda、JDk8内置的ASM等）</li>
<li>提供HTTP服务，同时内置JSON功能与限时缓存功能</li>
<li>TCP层完全使用NIO.2，并统一TCP与UDP的接口换</li>
<li>提供分布式与集中式部署的无缝切换</li>
<li>提供类似JPA功能，包含数据缓存自动同步、分表分库与简洁的数据层操作接口</li>
<li>可以动态修改已依赖注入的资源</li>
</ol>

<strong>Redkale 设计理念</strong>
<p>
   &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;作为一个全新的微服务框架，Redkale在接口定义上使用了Java 8大量的新语法，接口有默认实现、接口带静态方法、重复注解等特性，同时在设计上与主流框架有很大不同。Redkale是按组件形式设计的，而非以容器为主，几乎每个子包都是能提供独立功能的组件。如Tomcat是按容器设计的，所有web资源/配置由Tomcat控制，开发者很能难控制到Tomcat内部，而Redkale的HTTP服务只是个组件，开发者既可以自己启动和配置HttpServer，也可以把Redkale当成容器通过Redkale进程来初始化服务。Spring的Ioc容器也是如此，Redkale提供的依赖注入仅通过ResouceFactory一个类来控制，非常轻量，并且可动态更改已注入的资源。Spring提倡控制反转思想，而自身的容器却让开发者很难控制。Redkale是一个既能以组件形式也能以容器形式存在的框架。从整体上看，Redkale的架构分两层：接口和默认实现。开发者若想替换掉Redkale内置的HTTP服务而使用符合JavaEE规范的HttpServlet, 可以采用自定义协议基于JSR 340(Servlet 3.1)来实现自己的HTTP服务；若想使用Hibernate作为数据库操作，可以写一个自己的DataSource实现类；JSON的序列化和反序列化也可以使用第三方的实现；Memcached或Redis也可以作为另一个CacheSource的实现替换Redkale的默认实现。这其实包含了控制反转的思想，让框架里的各个组件均可让开发者控制。<br/>
   &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;与主流框架比，功能上Redkale显得很简单，这体现了Redkale的简易性，而并非是不足，从一个良好的设计习惯或架构上来看，有些常用功能是不需要提供的，如Redkale的HTTP服务不支持HTTPS和JSP，HTTPS比HTTP多了一层加密解密，这种密集型的计算不是Java的专长，通常提供HTTP服务的架构不会将Java动态服务器放在最前端，而是在前方会放nginx或apache，除了负载均衡还能静动分离，因此HTTPS的加解密应交给nginx这样的高性能服务器处理。Redkale再提供HTTPS服务就显得鸡肋。JSP其实算是一个落后的技术，现在是一个多样化终端的时代，终端不只局限于桌面程序和PC浏览器，还有原生App、混合式App、微信端、移动H5、提供第三方接口等各种形式的终端，这些都不是JSP能方便兼顾的，而HTTP+JSON作为通用性接口可以避免重复开发，模版引擎的功能加上各种强大的JS框架足以取代JSP。Redkale在功能上做了筛选，不会为了迎合主流而提供，而是以良好的设计思想为指导。这是Redkale的主导思维。
</p>


&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<h5>详情请访问:&nbsp;&nbsp;&nbsp;&nbsp;<a href='https://redkale.org' target='_blank'>https://redkale.org</a></h5>

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<h5>基本文档:&nbsp;&nbsp;&nbsp;&nbsp;<a href='https://redkale.org/articles.html' target='_blank'>https://redkale.org/articles.html</a></h5>

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<h5>欢迎加入Redkale QQ群:  527523235</h5>
&nbsp;
