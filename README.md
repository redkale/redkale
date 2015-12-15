<h1>项目介绍</h1>
<p>
   &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;RedKale 是基于Java 8的微服务框架， 包含HTTP、WebSocket、TCP/UDP、数据序列化、数据缓存、依赖注入等功能。 
   <br/>其功能相当于 Tomcat + Mina + Struts + Spring + Hibernate + RMI + Json + Memcached 的综合体。
</p>
<strong>RedKale 有如下主要特点：</strong>
<ol>
<li>大量使用Java 8新特性（接口默认值、Stream、Lambda、JDk8内置的ASM包）</li>
<li>HTTP层内置json功能与限时缓存功能</li>
<li>网络层使用Java 7里的NIO.2</li>
<li>分布式与集中式可以无缝切换</li>
<li>数据缓存自动同步与简洁的数据层操作接口</li>
<li>功能强大 但体积不到1.5M，且不依赖任何第三方包</li>
</ol>

<h5>亮点一.  轻量级HTTP</h5>
<p>
    &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;RedKale 的HTTP是基于异步NIO.2实现的，所提供的HttpResponse的输出接口也是异步的，因此并不遵循JSR 340规范(Servlet 3.1)且也没有实现Jsp规范。 HTTP只提供四个对象：HttpContext、HttpRequest、HttpResponse、HttpServlet。 传统Session则由数据层实现。RedKale提倡http+json接口， 因此HTTP层内置了json解析与序列化接口，同时内置http缓存机制。
</p>

<h5>亮点二.  SNCP协议</h5>
<p>
    &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;SNCP是RedKale独有的协议层， 类似RMI与WebService的功能结合，主要用于进程间的数据传输。使用者通过配置文件可以轻易的将Service由本地模式变成远程模式。远程模式的Service使用SNCP协议与其他进程的Service通信。<br/>
</p>

<h5>亮点三.  数据层DataSource</h5>
<p>
    &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;RedKale提供DataSource类对数据层进行操作， 其功能类似JPA(或Hibernate)+Memcached。 与JPA相比各有优缺点，优点： 1、Datasource中的内存缓存会自动与其他同服务的进程之间同步。2、简易的查询接口，即时复杂的过滤查询都无需写SQL语句。 缺点： 1、不提供复杂的关联表操作与查询。<br/>
</p>
