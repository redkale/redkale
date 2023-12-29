# DB数据源

## 配置数据源
```properties
redkale.datasource.platf.url = jdbc:mysql://127.0.0.1:3306/platf?serverTimezone=UTC&characterEncoding=utf8
redkale.datasource.platf.user = root
redkale.datasource.platf.password = pwd123
```

## 增删改
```java
@Data
public class Account {
    //男
    public static final short GENDER_MALE = 1;

    //女
    public static final short GENDER_FEMALE = 2;
    
    @Id
    @Column(name = "account_id")
    private String accountid;

    @Column(name = "account_name")
    private String accountName;

    private int age;
    
    private short gender;

    private String remark;

    @Column(name = "create_time", updatable = false)
    private long createTime;
}
```

&emsp;&emsp;新增实体对象：
```java
    //新增单个
    Account account = new Account();
    account.setAccountid("account1");
    account.setAccountName("Hello");
    account.setCreateTime(System.currentTimeMillis());
    source.insert(account);

    //异步新增多个
    Account a1 = new Account();
    a1.setAccountid("account1");
    a1.setAccountName("Hello1");
    a1.setCreateTime(System.currentTimeMillis());
    Account a2 = new Account();
    a2.setAccountid("account2");
    a2.setAccountName("Hello2");
    a2.setCreateTime(System.currentTimeMillis());
    source.insertAsync(a1, a2);
```

&emsp;&emsp;修改实体对象：
```java
    //更新单个字段
    source.updateColumn(Account.class, "account1", Account::getRemark, "新备注");

    //更新多个字段
    source.updateColumn(Account.class, "account1",
        ColumnValue.set(Account::getAccountName, "新名称"),
        ColumnValue.set(Account::getRemark, "新备注"),
        ColumnValue.inc(Account::getAge, 2)); //年龄+2

    //更新多个字段
    Account account = new Account();
    account.setAccountid("account1");
    account.setAccountName("新名称");
    account.setRemark("新备注");
    source.updateColumn(account, "accountName", "remark");
    //或者
    source.updateColumn(account, Account::getAccountName, Account::getRemark);

    //更新整个对象
    Account one = new Account();
    one.setAccountid("account1");
    one.setAccountName("Hello1");
    one.setCreateTime(System.currentTimeMillis());
    source.update(one); //createTime不会被更新，因字段设置了@Column(updatable=false)

    //过滤更新
    source.updateColumn(Account.class, FilterNodes.lt(Account::getAge, 16),
        ColumnValue.set(Account::getRemark, "不满16岁是青少年"));
```

&emsp;&emsp;删除实体：
```java
    //根据主键值删除
    Account account = new Account();
    account.setAccountid("account1");
    source.delete(account);

    //过滤删除， 删除16以下男生
    source.delete(Account.class, FilterNodes.lt(Account::getAge, 16).and("gender", GENDER_MALE));
```