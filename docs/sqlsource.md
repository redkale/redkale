# DataSqlSource数据源
&emsp;&emsp;```DataSqlSource```是```DataSource```的SQL扩展类，增强了对原生SQL的操作。

## SQL模板
&emsp;&emsp;Redkale中的SQL模板与Mybatis里的SQL模板用法类似， 最大区别在于不需要写很多```if/else```判断语句,也不用写xml文件， 框架会根据参数存在与否动态生成sql语句， 查询结果时带下划线的sql字段名和类中的驼峰字段名会自动匹配。
```sql
    SELECT * FROM user WHERE user_id IN ${bean.userIds} OR user_name = ${bean.userName}
```
&emsp;&emsp;当bean.userIds=null，bean.userName='hello'时，sql语句转换成
```sql
    SELECT * FROM user WHERE user_name = 'hello'
```
&emsp;&emsp;当bean.userIds=[1,2,3]，bean.userName=null时，sql语句转换成
```sql
    SELECT * FROM user WHERE user_id IN (1,2,3)
```
&emsp;&emsp;IN或者NOT IN语句当参数不为null而是空数组或者空List会进行特殊处理，IN语句会转化成```1=1```, NOT IN语句会转化成```1=2```。 当bean.userIds=空数组，bean.userName='hello'时，sql语句转换成
```sql
    SELECT * FROM user WHERE 1=2 OR user_name = 'hello'
```
&emsp;&emsp;有些场景要求参数是必需的，就需要使用$${}来校验参数是否必需。
```sql
    DELETE FROM user WHERE user_name = $${bean.userName}
```
&emsp;&emsp;当bean.userName=null时，执行sql会报错 ```Missing parameter bean.userName```

&emsp;&emsp;Service调用原生SQL模板示例：
```java
public class ForumInfoService extends AbstractService {

    //查询单个记录的sql
    private static final String findOneSql = "SELECT f.forum_groupid, s.forum_section_color "
            + "FROM forum_info f, forum_section s "
            + " WHERE f.forumid = s.forumid AND "
            + "s.forum_sectionid = ${bean.forumSectionid} AND "
            + "f.forumid = ${bean.forumid} AND s.forum_section_color = ${bean.forumSectionColor}";

    //查询列表记录的sql
    private static final String queryListSql = "SELECT f.forum_groupid, s.forum_section_color "
            + "FROM forum_info f, forum_section s "
            + " WHERE f.forumid = s.forumid AND "
            + "s.forum_sectionid = ${bean.forumSectionid} AND "
            + "f.forumid = ${bean.forumid} AND s.forum_section_color = ${bean.forumSectionColor}";

    @Resource
    private DataSqlSource source;

    public ForumResult findForumResultOne(ForumBean bean) {
        return source.nativeQueryOne(ForumResult.class, findOneSql, Map.of("bean", bean));
    }

    public CompletableFuture<List<ForumResult>> queryForumResultListAsync(ForumBean bean) {
        return source.nativeQueryListAsync(ForumResult.class, queryListSql, Map.of("bean", bean));
    }
}
```

## DataSqlMapper
&emsp;&emsp;DataSqlMapper与MyBatis里的Mapper用法类似。
```java
public interface ForumInfoMapper extends BaseMapper<ForumInfo> {

    @Sql("SELECT f.forum_groupid, s.forum_section_color "
            + "FROM forum_info f, forum_section s "
            + "WHERE f.forumid = s.forumid "
            + "AND s.forum_sectionid = ${bean.forumSectionid} "
            + "AND f.forumid = ${bean.forumid} "
            + "AND s.forum_section_color = ${bean.forumSectionColor}")
    public ForumResult findForumResultOne(ForumBean bean);

    @Sql("SELECT f.forum_groupid, s.forum_section_color "
            + "FROM forum_info f, forum_section s "
            + "WHERE f.forumid = s.forumid AND "
            + "s.forum_sectionid = ${bean.forumSectionid} "
            + "AND f.forumid = ${bean.forumid} "
            + "AND s.forum_section_color = ${bean.forumSectionColor}")
    public CompletableFuture<ForumResult> findForumResultOneAsync(ForumBean bean);

    @Sql("UPDATE forum_section s "
            + " SET s.forum_sectionid = '' "
            + " WHERE s.forum_section_color = $${bean.forumSectionColor}")
    public int updateForumResult(@Param("bean") ForumBean bean0);
}
```