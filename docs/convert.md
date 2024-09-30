# 序列化
&emsp;&emsp;Convert提供Java对象的序列化与反序列化功能。支持JSON(JavaScript Object Notation)、BSON(Binary Stream Object Notation)、PROTOBUF三种格式化。 三种格式使用方式完全一样，其性能都大幅度超过其他JSON框架。同时JSON内置于HTTP服务中，BSON也是SNCP协议数据序列化的基础。
## 基本API
&emsp;&emsp;JSON序列化其操作类主要是JsonConvert，配置类主要是JsonFactory、ConvertColumn。JsonFactory采用同ClassLoader类似的双亲委托方式设计。
 JsonConvert 序列化encode方法：
```java
   public String convertTo(Object value);

    public String convertTo(Type type, Object value);

    public void convertTo(OutputStream out, Object value);

    public void convertTo(OutputStream out, Type type, Object value);

    public ByteBuffer[] convertTo(Supplier<ByteBuffer> supplier, Object value);

    public ByteBuffer[] convertTo(Supplier<ByteBuffer> supplier, Type type, Object value);

    public void convertTo(JsonWriter writer, Object value);

    public void convertTo(JsonWriter writer, Type type, Object value);
```
JsonConvert 反序列化decode方法：
```java
   public <T> T convertFrom(Type type, String text);

    public <T> T convertFrom(Type type, char[] text);

    public <T> T convertFrom(Type type, char[] text, int start, int len);

    public <T> T convertFrom(Type type, InputStream in);

    public <T> T convertFrom(Type type, ByteBuffer... buffers);

    public <T> T convertFrom(Type type, JsonReader reader);
```
## JSON基本用法
```java
    @Data
    public class UserRecord {

        private int userid;

        private String username = "";

        private String password = "";

        public UserRecord() {
        }
    }

    public static void main(String[] args) throws Exception {
        UserRecord user = new UserRecord();
        user.setUserid(100);
        user.setUsername("redkalename");
        user.setPassword("123456");
        final JsonConvert convert = JsonConvert.root();
        String json = convert.convertTo(user);
        System.out.println(json);  //应该是 {"password":"123456","userid":100,"username":"redkalename"}
        UserRecord user2 = convert.convertFrom(UserRecord.class, json);
        //应该也是 {"password":"123456","userid":100,"username":"redkalename"}
        System.out.println(convert.convertTo(user2)); 
        
        /**
         * 以下功能是为了屏蔽password字段。
         * 等价于 public String getPassword() 加上 @ConvertColumn ：
         *
         *      @ConvertColumn(ignore = true, type = ConvertType.JSON)
         *      public String getPassword() {
         *          return password;
         *      }
         **/
        final JsonFactory childFactory = JsonFactory.root().createChild();
        childFactory.register(UserRecord.class, true, "password"); //屏蔽掉password字段使其不输出
        childFactory.reloadCoder(UserRecord.class); //重新加载Coder使之覆盖父Factory的配置
        final JsonConvert childConvert = childFactory.getConvert();
        json = childConvert.convertTo(user);
        System.out.println(json);  //应该是 {"userid":100,"username":"redkalename"}
        user2 = childConvert.convertFrom(UserRecord.class, json);
        //应该也是 {"userid":100,"username":"redkalename"}
        System.out.println(childConvert.convertTo(user2)); 
    }
```
&emsp;&emsp;在Redkale里存在默认的JsonConvert、BsonConvert、ProtobufConvert对象。 只需在所有Service、Servlet中增加依赖注入资源。
```java
public class XXXService implements Service {

    @Resource
    private JsonConvert jsonConvert;

    @Resource
    private BsonConvert bsonConvert;

    @Resource
    private ProtobufConvert protobufConvert;
}

public class XXXServlet extends HttpServlet {

    @Resource
    private JsonConvert jsonConvert;

    @Resource
    private BsonConvert bsonConvert;

    @Resource
    private ProtobufConvert protobufConvert;
    
}
```
&emsp;&emsp; 同一类型数据通过设置新的JsonFactory可以有不同的输出。
```java
@Data
public class UserSimpleInfo {

    private int userid;

    private String username = "";

    @ConvertColumn(ignore = true, type = ConvertType.JSON)
    private long regtime; //注册时间

    @ConvertColumn(ignore = true, type = ConvertType.JSON)
    private String regaddr = ""; //注册IP

}

public class UserInfoServlet extends HttpBaseServlet {

    @Resource
    private UserSerice service;

    @Resource
    private JsonFactory jsonRootFactory;

    @Resource
    private JsonConvert detailConvert;

    @Override
    public void init(HttpContext context, AnyValue config) {
        final JsonFactory childFactory = jsonRootFactory.createChild();
        childFactory.register(UserSimpleInfo.class, false, "regtime", "regaddr"); //允许输出注册时间与注册地址
        childFactory.reloadCoder(UserSimpleInfo.class); //重新加载Coder使之覆盖父Factory的配置
        this.detailConvert = childFactory.getConvert();
    }

    // 获取他人基本信息
    @HttpMapping(url = "/user/info/")
    public void info(HttpRequest req, HttpResponse resp) throws IOException {
        int userid = Integer.parseInt(req.getRequstURILastPath());
        UserSimpleInfo user = service.findUserInfo(userid);
        resp.finishJson(user);  // 不包含用户的注册时间和注册地址字段信息
    }

    //获取用户自己的信息
    @HttpMapping(url = "/user/myinfo")
    public void mydetail(HttpRequest req, HttpResponse resp) throws IOException {
        int userid = req.currentUser().getUserid(); //获取当前用户ID
        UserSimpleInfo user = service.findUserInfo(userid);
        resp.finishJson(detailConvert, user);  // 包含用户的注册时间和注册地址字段信息
    }
}
```

&emsp;&emsp;Convert 支持带参数构造函数。

&emsp;&emsp;&emsp;&emsp;1. public 带参数的构造函数加上 @ConstructorParameters 注解：
```java
public class UserRecord {

    private int userid;

    private String username = "";

    private String password = "";

    @ConstructorParameters({"userid", "username", "password"})
    public UserRecord(int userid, String username, String password) {
        this.userid = userid;
        this.username = username;
        this.password = password;
    }

    public int getUserid() {
        return userid;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
```
&emsp;&emsp;&emsp;&emsp;2. 自定义Creator：
```java
public class UserRecord {

    private int userid;

    private String username = "";

    private String password = "";

    UserRecord(int userid, String username, String password) {
        this.userid = userid;
        this.username = username;
        this.password = password;
    }

    public int getUserid() {
        return userid;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * 自定义Creator方法。
     * 1) 方法名可以随意。
     * 2) 方法必须是static。
     * 3）方法的参数必须为空。
     * 4）方法的返回类型必须是Creator。
     *
     * @return
     */
    private static Creator<UserRecord> creator() {
        return new Creator<UserRecord>() {
            @Override
            @ConstructorParameters({"userid", "username", "password"}) //带参数的构造函数必须有ConstructorParameters注解
            public UserRecord create(Object... params) {
                int userid = (params[0] == null ? 0 : (Integer) params[0]);
                return new UserRecord(userid, (String) params[1], (String) params[2]);
            }
        };
    }
}
  
```
&emsp;&emsp;通常JavaBean类默认有个public空参数的构造函数，因此大部分情况下不要自定义Creator，其实只要不是private的空参数构造函数Convert都能自动支持(其他的框架都仅支持public的构造函数)，只有仅含private的构造函数才需要自定义Creator。带参数的构造函数就需要标记@ConstructorParameters，常见于Immutable Object。

## 自定义
&emsp;&emsp;Convert 支持自定义Decode、Encode。

&emsp;&emsp;&emsp;&emsp;1. 通过ConvertFactory显式的注册：
```java
public class FileSimpleCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, File> {

    public static final FileSimpleCoder instance = new FileSimpleCoder();

    @Override
    public void convertTo(W out, File value) {
        out.writeString(value == null ? null : value.getPath());
    }

    @Override
    public File convertFrom(R in) {
        String path = in.readString();
        return path == null ? null : new File(path);
    }
}

JsonFactory.root().register(File.class, FileSimpleCoder.instance);

BsonFactory.root().register(File.class, FileSimpleCoder.instance);
```
&emsp;&emsp;&emsp;&emsp;2. 通过JavaBean类自定义静态方法自动加载：
```java
public class InnerCoderEntity {

    private final String val;

    private final int id;

    private InnerCoderEntity(int id, String value) {
        this.id = id;
        this.val = value;
    }

    public static InnerCoderEntity create(int id, String value) {
        return new InnerCoderEntity(id, value);
    }

    /**
     * 该方法提供给Convert组件自动加载。
     * 1) 方法名可以随意。
     * 2) 方法必须是static
     * 3）方法的参数有且只能有一个， 且必须是org.redkale.convert.ConvertFactory或子类。
     * —3.1) 参数类型为org.redkale.convert.ConvertFactory 表示适合JSON,BSON,PROTOBUF。
     * —3.2) 参数类型为org.redkale.convert.json.JsonFactory 表示仅适合JSON。
     * —3.3) 参数类型为org.redkale.convert.bson.BsonFactory 表示仅适合BSON。
     * —3.3) 参数类型为org.redkale.convert.pb.ProtobufFactory 表示仅适合PROTOBUF。
     * 4）方法的返回类型必须是 Decodeable/Encodeable/SimpledCoder
     * 若返回类型不是SimpledCoder, 就必须提供两个方法： 一个返回Decodeable 一个返回 Encodeable。
     *
     * @param factory
     * @return
     */
    private static SimpledCoder<Reader, Writer, InnerCoderEntity> createConvertCoder(final ConvertFactory factory) {
        return new SimpledCoder<Reader, Writer, InnerCoderEntity>() {

            //必须与EnMember[] 顺序一致
            private final DeMember[] deMembers = new DeMember[]{
                DeMember.create(factory, InnerCoderEntity.class, "id"),
                DeMember.create(factory, InnerCoderEntity.class, "val")};

            //必须与DeMember[] 顺序一致
            private final EnMember[] enMembers = new EnMember[]{
                EnMember.create(factory, InnerCoderEntity.class, "id"),
                EnMember.create(factory, InnerCoderEntity.class, "val")};

            @Override
            public void convertTo(Writer out, InnerCoderEntity value) {
                if (value == null) {
                    out.writeObjectNull(InnerCoderEntity.class);
                    return;
                }
                out.writeObjectB(value);
                for (EnMember member : enMembers) {
                    out.writeObjectField(member, value);
                }
                out.writeObjectE(value);
            }

            @Override
            public InnerCoderEntity convertFrom(Reader in) {
                if (in.readObjectB(InnerCoderEntity.class) == null) return null;
                int index = 0;
                final Object[] params = new Object[deMembers.length];
                while (in.hasNext()) {
                    DeMember member = in.readFieldName(deMembers); //读取字段名
                    in.readBlank(); //读取字段名与字段值之间的间隔符，JSON则是跳过冒号:
                    if (member == null) {
                        in.skipValue(); //跳过不存在的字段的值, 一般不会发生
                    } else {
                        params[index++] = member.read(in);
                    }
                }
                in.readObjectE(InnerCoderEntity.class);
                return InnerCoderEntity.create(params[0] == null ? 0 : (Integer) params[0], (String) params[1]);
            }
        };
    }

    public int getId() {
        return id;
    }

    public String getVal() {
        return val;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
```

## RestConvert
&emsp;&emsp;```@RestConvert```、```@RestConvertCoder```是针对```@RestService```接口进行自定义序列化注解
```java
@Data
public class RestConvertItem {

    private long createTime;

    @ConvertColumn(ignore = true)
    private String aesKey;
}

@Data
public class RestConvertBean {

    private int id;

    private boolean enable;

    private String name;

    private RestConvertItem content;
}

//将boolean类型值转换成0/1的int值
public class RestConvertBoolCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, Boolean> {

    @Override
    public void convertTo(W out, Boolean value) {
        out.writeInt(value == null || !value ? 0 : 1);
    }

    @Override
    public Boolean convertFrom(R in) {
        return in.readInt() == 1;
    }
}

@RestService(name = "test", autoMapping = true)
public class RestConvertService extends AbstractService {

    //输出: {"content":{"createTime":100},"enable":true,"id":123,"name":"haha"}
    public RestConvertBean load1() {
        return createBean();
    }
    
    //输出: {"content":{"aesKey":"keykey","createTime":100},"enable":true,"id":123,"name":"haha"}
    //aesKey字段也会被输出
    @RestConvert(type = RestConvertItem.class, skipIgnore = true)
    public RestConvertBean load2() {
        return createBean();
    }

    //输出: {"id":123}
    //只输出id字段
    @RestConvert(type = RestConvertBean.class, onlyColumns = "id")
    public RestConvertBean load3() {
        return createBean();
    }

    //输出: {"content":{"createTime":100},"enable":1,"id":123,"name":"haha"}
    //enable字段输出1，而不是true
    @RestConvertCoder(type = RestConvertBean.class, field = "enable", coder = RestConvertBoolCoder.class)
    public RestConvertBean load4() {
        return createBean();
    }

    private RestConvertBean createBean() {
        RestConvertBean bean = new RestConvertBean();
        bean.setId(123);
        bean.setName("haha");
        bean.setEnable(true);
        RestConvertItem item = new RestConvertItem();
        item.setCreateTime(100);
        item.setAesKey("keykey");
        bean.setContent(item);
        return bean;
    }
}
```

## BSON数据格式
&emsp;&emsp;BSON类似Java自带的Serializable， 其格式如下: 

&emsp;&emsp;&emsp;&emsp;1). 基本数据类型: 直接转换成byte[]

&emsp;&emsp;&emsp;&emsp;2). StandardString(无特殊字符且长度小于256的字符串): length(1 byte) + byte[](utf8); 通常用于类名、字段名、枚举。

&emsp;&emsp;&emsp;&emsp;3). String: length(4 bytes) + byte[](utf8);

&emsp;&emsp;&emsp;&emsp;4). 数组: length(4 bytes) + byte[]...

&emsp;&emsp;&emsp;&emsp;5). Object:

&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;1. realclass (StandardString) (如果指定格式化的class与实体对象的class不一致才会有该值, 该值可以使用@ConvertEntity给其取个别名)

&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;2. 空字符串(StandardString)

&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;3. SIGN_OBJECTB 标记位，值固定为0xBB (short)

&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;4. 循环字段值:

&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;4.1 SIGN_HASNEXT 标记位，值固定为1 (byte)

&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;4.2 字段类型; 11-19为基本类型&字符串; 21-29为基本类型&字符串的数组; 127为Object

&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;4.3 字段名 (StandardString)

&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;4.4 字段的值Object

&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;5. SIGN_NONEXT 标记位，值固定为0 (byte)

&emsp;&emsp;&emsp;&emsp;&emsp;&emsp; 6. SIGN_OBJECTE 标记位，值固定为0xEE (short)

