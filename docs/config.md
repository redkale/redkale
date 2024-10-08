# 配置说明
# application.xml 配置：
```xml
<!-- 
    文件说明:
        ${APP_HOME} 指当前程序的根目录APP_HOME
        没注明唯一的节点可多个存在
        required： 被声明required的属性值不能为空 
-->
<!--     
    nodeid: int     进程的节点ID，用于分布式环境，一个系统中节点ID必须全局唯一，使用cluster时框架会进行唯一性校验
    name:           进程的名称，用于监控识别，命名规则: 字母、数字、下划线、短横、点
    address:        本地局域网的IP地址， 默认值为默认网卡的ip，当不使用默认值需要指定值，如192.168.1.22
    port: required  程序的管理Server的端口，用于关闭或者与监管系统进行数据交互
    lib:            加上额外的lib路径,多个路径用分号;隔开； 默认为空。  例如: ${APP_HOME}/lib/a.jar;${APP_HOME}/lib2/b.jar;
-->
<application nodeid="1000" port="6560" lib="">  
        
    <!--
       【节点全局唯一】 @since 2.3.0
        全局Serivce执行的线程池， Application.workExecutor, 没配置该节点将自动创建一个。
        threads： 线程数，默认值: CPU核数*10, 核数=2的情况下默认值为20。值为0表示不启用workExecutor，都在IO线程中运行。
        clients： client回调函数运行的线程池大小， 默认值: CPU核数*4
    -->
    <executor threads="4"/>

    <!--
    【节点全局唯一】 @since 2.8.0
        全局Serivce的定时任务设置，没配置该节点将自动创建一个。
        enabled： 是否开启缓存功能。默认: true
    -->
    <scheduled enabled="true"/>

    <!--
        @since 2.8.0
        全局Serivce的缓存设置，没配置该节点将自动创建一个。
        name:  缓存管理器的名称， 默认: ""
        enabled： 是否开启缓存功能。默认: true
        remote: 远程CacheSource的资源名
        broadcastable: 存在远程CacheSource时修改数据是否进行广播到其他集群服务中。默认: true
    -->
    <cached name="" enabled="true" remote="xxx" broadcastable="true"/>
    
    <!--
       【节点全局唯一】
        第三方服务发现管理接口
        type：      类名，必须是org.redkale.cluster.ClusterAgent的子类
        waits:      注销服务后是否需要等待检查周期时间后再进行Service销毁，默认值为：false
                    当一个Service进行服务注销后，不能立刻销毁Service，因为健康检测是有间隔时间差的，
                    需要等待一个健康检测周期时间，让其他进程都更新完服务列表。
                    如果使用MQ，可以设置为false，如果对服务健壮性要求高，建议设置为true
        protocols:  服务发现可以处理的协议， 默认值为: SNCP, 多个协议用分号;隔开
        ports:      服务发现可以处理的端口， 多个端口用分号;隔开
        ttls:       心跳频率，多少秒一次
        xxxx:       自定义的字段属性，例如：CacheClusterAgent有source字段; ConsulClusterAgent有apiurl字段;
    -->
    <cluster type="org.redkalex.cluster.consul.ConsulClusterAgent" waits="false" protocols="SNCP" ports="7070;7071" xxx="xxx" />

    <!--
        MQ管理接口配置
        不同MQ节点所配置的MQ集群不能重复。
        MQ跟着协议走，所以mq的属性值需要被赋值在rest节点上, 由于SncpServlet是自动生成的，故SNCP协议下，mq属性值被赋值在service/services节点上
        name:     服务的名称，用于监控识别，多个mq节点时只能有一个name为空的节点，mq.name不能重复,命名规则: 字母、数字、下划线
        type：    实现类名，必须是org.redkale.mq.MessageAgent的子类
        threads： 线程数，为0表示使用workExecutor。默认: CPU核数, 核数=1的情况下默认值为2，JDK 21以上版本默认使用虚拟线程池
        rpcfirst：cluster和mq同名组件时，HttpRpcClient优先使用MQ，默认不优先走MQ。
        coder:    MessageRecord的解析器类，必须是org.redkale.mq.MessageCoder<MessageRecord>的实现类, 
             可对数据包进行加密解密，默认值：org.redkale.mq.MessageRecordCoder
        MQ节点下的子节点配置没有固定格式, 根据MessageAgent实现方的定义来配置
    -->
    <mq name="" type="org.redkalex.mq.kafka.KafkaMessageAgent" rpcfirst="false" threads="4">
        <servers value="127.0.0.1:9101"/>
        <!--        
           加载所有的MessageConsumer实例;
           autoload="true"  默认值. 自动加载classpath下所有的MessageConsumer类  
           autoload="false" 需要显著的指定MessageConsumer类
           includes： 当autoload="true"， 拉取类名与includes中的正则表达式匹配的类, 多个正则表达式用分号;隔开
           excludes： 当autoload="true"， 排除类名与excludes中的正则表达式匹配的类, 多个正则表达式用分号;隔开           
        -->
        <consumer autoload="true" includes="" excludes=""/>
        <!--
            MQ实现方的配置项
          type:  配置项类型，值只能是consumer或producer
        -->
        <config type="consumer">
            <property name="xxxxxx" value="XXXXXXXX"/>
        </config>
        <config type="producer">
            <property name="xxxxxx" value="XXXXXXXX"/>
        </config>
    </mq>
        
    <!--
        一个组包含多个node， 同一Service服务可以由多个进程提供，这些进程称为一个GROUP，且同一GROUP内的进程必须在同一机房或局域网内
        name:       服务组ID，长度不能超过11个字节. 默认为空字符串。 注意: name不能包含$符号。
        protocol：  值范围：UDP TCP， 默认TCP
        nodes:      多个node节点值； 例如:192.168.0.1:6060,192.168.0.2:6060
        注意: 一个node只能所属一个group。只要存在protocol=SNCP的Server节点信息， 就必须有group节点信息。
    -->
    <group name="" protocol="TCP" nodes="192.168.0.1:6060,192.168.0.2:6060">
        <!--
            需要将本地node的addr与port列在此处, 也可以直接用nodes属性。
            同一个<node>节点值只能存在一个<group>节点内，即同一个addr+port只能属于一个group。
            addr: required IP地址
            port: required 端口
        -->
        <node addr="127.0.0.1" port="7070"/>
    </group>

    <!--
       Application启动的监听事件,可配置多个节点
       value: 类名，必须是ApplicationListener的子类
    -->
    <listener value="org.redkalex.xxx.XXXApplicationListener"/>
        
    <!-- 
        【节点全局唯一】
        全局的参数配置, 可以通过@Resource(name="property.xxxxxx") 进行注入<property>的信息, 被注解的字段类型只能是String、primitive class
        如果name是system.property.开头的值将会在进程启动时进行System.setProperty("yyyy", "YYYYYY")操作。
        如果name是mimetype.property.开头的值将会在进程启动时进行MimeType.add("yyyy", "YYYYYY")操作。
        先加载子节点property，再加载load文件， 最后加载agent的实现子类。
        load:  加载文件，多个用;隔开。
        其他属性: 供org.redkale.boot.PropertiesAgentProvider使用判断
        默认置入的system.property.的有：
           System.setProperty("redkale.convert.pool.size", "128");
           System.setProperty("redkale.convert.writer.buffer.defsize", "4096");
           
        <properties>节点下也可包含非<property>节点.
        非<property>其节点可以通过@Resource(name="properties.xxxxxx")进行注入, 被注解的字段类型只能是AnyValue、AnyValue[]
    -->
    <properties load="config.properties">
        <property name="system.property.yyyy" value="YYYYYY"/>
        <property name="xxxxxx" value="XXXXXXXX"/>
        <property name="xxxxxx" value="XXXXXXXX"/>
        <property name="xxxxxx" value="XXXXXXXX"/>
    </properties>
        
    <!--
        protocol: required   server所启动的协议，Redkale内置的有HTTP、SNCP、WATCH。协议均使用TCP实现; WATCH服务只能存在一个。
        name:                服务的名称，用于监控识别，一个配置文件中的server.name不能重复,命名规则: 字母、数字、下划线
        host:                服务所占address ， 默认: 0.0.0.0
        port:                required 服务所占端口 
        root:                如果是web类型服务，则包含页面  默认:{APP_HOME}/root
        lib:                 server额外的class目录， 默认为${APP_HOME}/libs/*; 
        charset:             文本编码， 默认: UTF-8
        backlog:             默认10K
        maxconns：           最大连接数, 小于1表示无限制， 默认: 0
        maxbody:             request.body最大值， 默认: 256K
        bufferCapacity:      ByteBuffer的初始化大小， TCP默认: 32K;  (HTTP 2.0、WebSocket，必须要16k以上); UDP默认: 8K
        bufferPoolSize：     ByteBuffer池的大小，默认: 线程数*4
        responsePoolSize：   Response池的大小，默认: 1024
        aliveTimeoutSeconds: KeepAlive读操作超时秒数， 默认30， 0表示永久不超时; -1表示禁止KeepAlive
        readTimeoutSeconds:  读操作超时秒数， 默认0， 0表示永久不超时
        writeTimeoutSeconds: 写操作超时秒数， 默认0， 0表示永久不超时
        interceptor:         启动/关闭NodeServer时被调用的拦截器实现类，必须是org.redkale.boot.NodeInterceptor的子类，默认为null
    -->
    <server protocol="HTTP" host="127.0.0.1" port="6060" root="root" lib=""> 
        
        <!-- 
           【节点在<server>中唯一】
           builder:             创建SSLContext的实现类, 可自定义，必须是org.redkale.net.SSLBuilder的子类
           sslProvider:         java.security.Provider自定义的实现类，如第三方: org.conscrypt.OpenSSLProvider、org.bouncycastle.jce.provider.BouncyCastleProvider
           jsseProvider:        java.security.Provider自定义的实现类，如第三方: org.conscrypt.JSSEProvider、   org.bouncycastle.jce.provider.BouncyCastleJsseProvider
           protocol:            TLS版本，默认值: TLS
           protocols:           设置setEnabledProtocols, 多个用,隔开 如: TLSv1.2,TLSv1.3
           clientAuth:          WANT/NEED/NONE, 默认值: NONE
           ciphers:             设置setEnabledCipherSuites, 多个用,隔开 如: TLS_RSA_WITH_AES_128_CBC_SHA256,TLS_RSA_WITH_AES_256_CBC_SHA256
           keystorePass:        KEY密码
           keystoreFile:        KEY文件 .jks
           keystoreType:        KEY类型， 默认值为JKS
           keystoreAlgorithm:   KEY文件的algorithm， 默认值为SunX509
           truststorePass:      TRUST密码
           truststoreFile:      TRUST文件
           truststoreType:      TRUST类型， 默认值为JKS
           truststoreAlgorithm: TRUST文件的algorithm， 默认值为SunX509
        -->
        <ssl builder=""/>
        
        <!-- 
           加载所有的Service服务;
           在同一个进程中同一个name同一类型的Service将共用同一个实例
           autoload="true"  默认值. 自动加载classpath下所有的Service类  
           autoload="false" 需要显著的指定Service类
           mq:        所属的MQ管理器，当 protocol == SNCP 时该值才有效, 存在该属性表示Service的SNCP协议采用消息总线代理模式
           includes： 当autoload="true"， 拉取类名与includes中的正则表达式匹配的类, 多个正则表达式用分号;隔开
           excludes： 当autoload="true"， 排除类名与excludes中的正则表达式匹配的类, 多个正则表达式用分号;隔开           
           group:     所属组的节点, 不能指定多个group, 如果配置文件中存在多个SNCP协议的Server节点，需要显式指定group属性.
                         当 protocol == SNCP 时 group表示当前Server与哪些节点组关联。
                         当 protocol != SNCP 时 group只能是空或者一个group的节点值。
                         特殊值"$remote", 视为通过第三方服务注册发现管理工具来获取远程模式的ip端口信息
        -->
        <services autoload="true" includes="" excludes="">

            <!-- 显著加载指定的Service的接口类 -->
            <service value="com.xxx.XXX1Service"/>
            <!-- 
               name:   显式指定name，覆盖默认的空字符串值。 注意: name不能包含$符号。
               mq:     所属的MQ管理器，当 protocol == SNCP 时该值才有效, 存在该属性表示Service的SNCP协议采用消息总线代理模式
               group:  显式指定group，覆盖<services>节点的group默认值。
               ignore: 是否禁用， 默认为false。
            -->
            <service value="com.xxx.XXX2Service" name="" group="xxx"/>
            <!--   给Service增加配置属性 -->
            <service value="com.xxx.XXX1Service"> 
                <!-- property值在public void init(AnyValue conf)方法中可以通过AnyValue properties=conf.getAnyValue("properties")获取 -->
                <property name="xxxxxx" value="XXXXXXXX"/>  
                <property name="xxxxxx" value="XXXXXXXX"/>
            </service>
        </services>
        
        <!-- 
           加载所有的Filter服务;
           autoload="true"  默认值.  
           autoload="false" 需要显著的指定Filter类
           includes：       当autoload="true"， 拉取类名与includes中的正则表达式匹配的类, 多个正则表达式用分号;隔开
           excludes：       当autoload="true"， 排除类名与excludes中的正则表达式匹配的类, 多个正则表达式用分号;隔开    
        -->
        <filters autoload="true" includes="" excludes="">
            
            <!-- 
               显著加载指定的Filter类
               value=: Filter类名。必须与Server的协议层相同，HTTP必须是HttpFilter
               ignore: 是否禁用， 默认为false。
            -->
            <!-- 显著加载指定的Filter类 -->
            <filter value="com.xxx.XXX1Filter"/>
            
            <!--   给Filter增加配置属性 -->
            <filter value="com.xxx.XXX12Filter"> 
                <!-- property值在public void init(AnyValue conf)方法中可以通过AnyValue properties=conf.getAnyValue("properties")获取 -->
                <property name="xxxxxx" value="XXXXXXXX"/>  
                <property name="xxxxxx" value="XXXXXXXX"/>
            </filter>
        </filters>
        
        <!-- 
           REST的核心配置项
           当Server为HTTP协议时, rest节点才有效。存在[rest]节点则Server启动时会加载REST服务, 节点可以多个,(WATCH协议不需要设置，系统会自动生成)
           path:     servlet的ContextPath前缀 默认为空 【注: 开启MQ时,该字段失效】
           base:     REST服务的BaseServlet，必须是 org.redkale.net.http.HttpServlet 的子类，且子类必须标记@HttpUserType。
           mq:       所属的MQ管理器, 存在该属性表示RestService的请求来自于消息总线 【注: 开启MQ时,path字段失效】
           autoload：默认值"true"  默认值. 加载当前server所能使用的Servce对象;    
           includes：当autoload="true"， 拉取类名与includes中的正则表达式匹配的类, 多个正则表达式用分号;隔开
           excludes：当autoload="true"， 排除类名与excludes中的正则表达式匹配的类, 多个正则表达式用分号;隔开
        -->
        <rest path="/pipes" base="org.redkale.net.http.HttpServlet" autoload="true" includes="" excludes="">
            <!-- 
               value:  Service类名，列出的表示必须被加载的Service对象
               ignore: 是否忽略，设置为true则不会加载该Service对象，默认值为false
            -->
            <service value="com.xxx.XXXXService"/>
            <!-- 
               value:  WebSocket类名，列出的表示必须被加载且标记为@RestWebSocket的WebSocket对象
               ignore: 是否忽略，设置为true则不会加载该RestWebSocket对象，默认值为false
            -->
            <websocket value="com.xxx.XXXXRestWebSocket"/>
        </rest>
        
        <!--
           【节点在<server>中唯一】
           当Server为HTTP协议时, request节点才有效。
           remoteaddr 节点: 替换请求方节点的IP地址， 通常请求方是由nginx等web静态服务器转发过的则需要配置该节点。
           且value值只能是以request.headers.开头，表示从request.headers中获取对应的header值。
           locale value值必须是request.headers.或request.parameters.开头。
           例如下面例子获取request.getRemoteAddr()值，如果header存在X-RemoteAddress值则返回X-RemoteAddress值，不存在返回getRemoteAddress()。
        -->
        <request>
            <remoteaddr value="request.headers.X-RemoteAddress"/>
            <locale value="request.headers.locale" /> 
            <rpc authenticator="org.redkale.net.http.HttpRpcAuthenticator的实现类"/>
        </request>
        
        <!--
           【节点在<server>中唯一】
           当Server为HTTP协议时, response节点才有效。
           contenttype: plain值为调用finish时的ContentType; 默认值: text/plain; charset=utf-8
                        json值为调用finishJson时的ContentType; 默认值: application/json; charset=utf-8
           defcookie 节点: 当response里输出的cookie没有指定domain 和path时，使用该节点的默认值。
           addheader、setheader 的value值以request.parameters.开头则表示从request.parameters中获取对应的parameter值
           addheader、setheader 的value值以request.headers.开头则表示从request.headers中获取对应的header值
           例如下面例子是在Response输出header时添加两个header（一个addHeader， 一个setHeader）。
           options 节点: 设置了该节点且auto=true，当request的method=OPTIONS自动设置addheader、setheader并返回200状态码
           date 节点: 设置了该节点且period有值(单位：毫秒);返回response会包含Date头信息，默认为period=0
                      period=0表示实时获取当前时间;
                      period<0表示不设置date;
                      period>0表示定时获取时间; 设置1000表示每秒刷新Date时间
        -->
        <response>
            <content-type plain="text/plain; charset=utf-8" json="application/json; charset=utf-8"/>            
            <defcookie domain="" path=""/>  
            <addheader name="Access-Control-Allow-Origin" value="request.headers.Origin" />  <!-- 可多节点 -->
            <setheader name="Access-Control-Allow-Headers" value="request.headers.Access-Control-Request-Headers"/>  <!-- 可多节点 -->
            <setheader name="Access-Control-Allow-Credentials" value="true"/>
            <options auto="true" />
            <date period="0" />
        </response>
        <!-- 
           【节点在<server>中唯一】
           当Server为HTTP协议时，render才有效. 指定输出引擎的实现类
           value:   输出引擎的实现类, 必须是org.redkale.net.http.HttpRender的子类
           suffixs: 引擎文件名后缀，多个用;隔开，默认值为: .htel
        -->
        <render value="org.redkalex.htel.HttpTemplateRender" suffixs=".htel"/>
        <!-- 
           【节点在<server>中唯一】
           当Server为HTTP协议时，ResourceServlet才有效. 默认存在一个有默认属性的resource-servlet节点
           webroot: web资源的根目录, 默认取server节点中的root值
           servlet: 静态资源HttpServlet的实现，默认使用HttpResourceServlet
           index :  启始页，默认值：index.html
        -->
        <resource-servlet webroot="root" index="index.html">
            <!--
                【节点在<resource-servlet>中唯一】
                资源缓存的配置, 默认存在一个含默认属性的caches节点
                limit:     资源缓存最大容量， 默认: 0, 为0表示不缓存， 单位可以是B、K、M、G，不区分大小写
                lengthmax: 可缓存的文件大小上限， 默认: 1M（超过1M的文件不会被缓存）
                watch:     是否监控缓存文件的变化， 默认为false，不监控
            -->
            <cache  limit="0M" lengthmax="1M" watch="false"/>
            <!--
               支持类似nginx中的rewrite， 目前只支持静态资源对静态资源的跳转。
               type:    匹配的类型, 目前只支持location(匹配path), 默认: location
               match:   匹配的正则表达式
               forward: 需跳转后的资源链接
               例如下面例子是将/xxx-yyy.html的页面全部跳转到/xxx.html
            -->
            <rewrite type="location" match="^/([^-]+)-[^-\.]+\.html(.*)" forward="/$1.html"/>
        </resource-servlet>
        <!-- 
           加载所有的Servlet服务;
           path:            servlet的ContextPath前缀 默认为空
           autoload="true"  默认值. 自动加载classpath下所有的Servlet类 
           autoload="false" 需要显著的指定Service类
           includes：       当autoload="true"， 拉取类名与includes中的正则表达式匹配的类, 多个正则表达式用分号;隔开
           excludes：       当autoload="true"， 排除类名与excludes中的正则表达式匹配的类, 多个正则表达式用分号;隔开
        -->
        <servlets path="/pipes" autoload="true" includes="" excludes="">
            <!-- 
               显著加载指定的Servlet类
               value=: Servlet类名。必须与Server的协议层相同，HTTP必须是HttpServlet
               ignore: 是否禁用， 默认为false。
            -->
            <servlet value="com.xxx.XXX1Servlet" />
            <servlet value="com.xxx.XXX2Servlet" />
            <servlet value="com.xxx.XXX3Servlet" >
                <property name="xxxxxx" value="XXXXXXXX"/>
                <property name="yyyyyy" value="YYYYYYYY"/>
            </servlet>
        </servlets>
    </server>
    
    <server protocol="SNCP" host="127.0.0.1" port="7070" root="root" lib=""> 
        <!-- 参数完全同上 -->
        <services autoload="true" includes="" excludes="" />
    </server>
    
</application>

```

# source.properties 配置：
```properties

# CacheSource   @Resource(name="usersession")
# type可以不用设置，框架会根据url判断使用哪个CacheSource实现类
redkale.cachesource.usersession.type = org.redkalex.cache.redis.RedisCacheSource
# 最大连接数
redkale.cachesource.usersession.maxconns = 16
# 节点地址
redkale.cachesource.usersession.nodes = redis://127.0.0.1:6363
# 节点密码
redkale.cachesource.usersession.password = 12345678
# 节点db
redkale.cachesource.usersession.db = 0

#简化写法: 可以不用.node[0], 将参数都合并到url中
redkale.cachesource.usersession.url = redis://user:123456@127.0.0.1:6363?db=0


# DataSource   @Resource(name="platf")
# type可以不用设置，框架会根据url判断使用哪个DataSource实现类，默认值: org.redkale.source.DataJdbcSource
redkale.datasource.platf.type = org.redkale.source.DataJdbcSource
# 是否开启缓存(标记为@Cacheable的Entity类)，值目前只支持两种： ALL: 所有开启缓存。 NONE: 关闭所有缓存， 非NONE字样统一视为ALL
redkale.datasource.platf.cachemode = ALL
# 是否自动建表当表不存在的时候， 目前只支持mysql、postgres， 默认为false
redkale.datasource.platf.table-autoddl = false
# 用户
redkale.datasource.platf.user = root
# 密码
redkale.datasource.platf.password = 12345678
# 多个URL用;隔开，如分布式SearchSource需要配多个URL
redkale.datasource.platf.url = jdbc:mysql://127.0.0.1:3306/platf?allowPublicKeyRetrieval=true&rewriteBatchedStatements=true&serverTimezone=UTC&characterEncoding=utf8
# 最大连接数，默认值：CPU数
redkale.datasource.platf.maxconns = 16
# 包含的SQL模板，相当于反向LIKE，不同的JDBC驱动的SQL语句不一样，Redkale内置了MySQL的语句
redkale.datasource.platf.contain-sqltemplate = LOCATE(#{keystr}, #{column}) > 0
# 包含的SQL模板，相当于反向LIKE，不同的JDBC驱动的SQL语句不一样，Redkale内置了MySQL的语句
redkale.datasource.platf.notcontain-sqltemplate = LOCATE(#{keystr}, #{column}) = 0
# 复制表结构的SQL模板，Redkale内置了MySQL的语句
redkale.datasource.platf.tablenotexist-sqlstates = 42000;42S02
# 复制表结构的SQL模板，Redkale内置了MySQL的语句
redkale.datasource.platf.tablecopy-sqltemplate = CREATE TABLE IF NOT EXISTS #{newtable} LIKE #{oldtable}


# DataSource 读写分离
redkale.datasource.platf.read.url = jdbc:mysql://127.0.0.1:3306/platf_r?allowPublicKeyRetrieval=true&rewriteBatchedStatements=true&serverTimezone=UTC&characterEncoding=utf8
redkale.datasource.platf.read.user = root
redkale.datasource.platf.read.password = 12345678

redkale.datasource.platf.write.url = jdbc:mysql://127.0.0.1:3306/platf_w?allowPublicKeyRetrieval=true&rewriteBatchedStatements=true&serverTimezone=UTC&characterEncoding=utf8
redkale.datasource.platf.write.user = root
redkale.datasource.platf.write.password = 12345678
```

# logging.properties 配置：
```properties

handlers = java.util.logging.ConsoleHandler,java.util.logging.FileHandler
.handlers = java.util.logging.ConsoleHandler,java.util.logging.FileHandler

############################################################
.level = FINE

sun.level = INFO
java.level = INFO
javax.level = INFO
com.sun.level = INFO

#java.util.logging.FileHandler.level = FINE

java.util.logging.FileHandler.limit = 20M
java.util.logging.FileHandler.count = 100
java.util.logging.FileHandler.encoding = UTF-8
java.util.logging.FileHandler.pattern = ${APP_HOME}/logs-%tY%tm/log-%tY%tm%td.log
#java.util.logging.FileHandler.unusual 属性表示将 WARNING、SEVERE 级别的日志复制写入单独的文件中
java.util.logging.FileHandler.unusual = ${APP_HOME}/logs-%tY%tm/log-warnerr-%tY%tm%td.log
#需要屏蔽消息内容的正则表达式
java.util.logging.FileHandler.denyregx = 
java.util.logging.FileHandler.append = true

#java.util.logging.ConsoleHandler.level = FINE

#将日志写进SearchSource, 必须指定source资源名，在source.properties中定义
#java.util.logging.SearchHandler.source = platfsearch
#指定写进SearchSource的表名，默认值为log-record
#java.util.logging.SearchHandler.tag = log-${APP_NAME}-%tY%tm%td
```

# yaml配置：
&emsp;&emsp;application和source的配置文件支持yaml格式，需要依赖第三方包， 默认的yaml配置文件名为 application.yml、source.yml。
```xml
<dependency>
    <groupId>org.redkalex</groupId>
    <artifactId>redkale-plugins</artifactId>
    <version>2.8.0</version>
</dependency>

<dependency>
    <groupId>org.yaml</groupId>
    <artifactId>snakeyaml</artifactId>
    <version>2.2</version>
</dependency>
```
