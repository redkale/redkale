<h1>项目介绍</h1>
<p>
   &nbsp;&nbsp;&nbsp;&nbsp;RedKale 是基于Java 8的微服务框架， 包含HTTP协议、WebSocket、TCP/UDP服务、数据序列化、数据缓存、依赖注入等功能。 
   <br/>其功能相当于 Tomcat + Mina + Struts + Spring + Hibernate + RMI + Json + Memcached 的综合体。
</p>
<strong>RedKale 有如下主要特点：</strong>
<ol>
<li>大量使用Java 8新特性（接口默认值、Stream、Lambda、JDk8内置的ASM包）</li>
<li>网络层使用Java 7里的NIO.2</li>
<li>分布式与集中式零成本的切换</li>
<li>功能强大 但体积不到1.5M，且不依赖任何第三方包</li>
</ol>
<br/>
<h4>亮点一. 轻量级HTTP</h4>
<p>
   &nbsp;&nbsp;&nbsp;&nbsp;RedKale 的HTTP是基于异步NIO.2实现的，所提供的HttpResponse的输出接口也是异步的，因此并不遵循JSR 340规范(Servlet 3.1)且也没有实现Jsp规范。 HTTP只提供四个实体：HttpContext、HttpRequest、HttpResponse、HttpServlet。 传统的Session则由数据层实现。 <br/>
   &nbsp;&nbsp;&nbsp;&nbsp;RedKale提倡http+json接口， 因此HTTP层内置了json解析与序列化接口。
</p>
