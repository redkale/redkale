/*
 *
 */
package org.redkale.test.source.parser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.redkale.source.DataSqlSource;

/**
 *
 * @author zhangjx
 */
public class DynForumInfoMapperImpl implements ForumInfoMapper {

    private DataSqlSource _source;

    private Class _type;

    @Override
    public ForumResult findForumResult(ForumBean bean) {
        String sql = "SELECT f.forum_groupid, s.forum_section_color FROM forum_info f, forum_section s WHERE f.forumid = s.forumid";
        Map<String, Object> params = new HashMap<>();
        params.put("bean", bean);
        return dataSource().nativeQueryOne(ForumResult.class, sql, params);
    }

    public CompletableFuture<ForumResult> findForumResultAsync(ForumBean bean) {
        String sql = "SELECT f.forum_groupid, s.forum_section_color FROM forum_info f, forum_section s WHERE f.forumid = s.forumid";
        Map<String, Object> params = new HashMap<>();
        params.put("bean", bean);
        return dataSource().nativeQueryOneAsync(ForumResult.class, sql, params);
    }

    @Override
    public List<ForumResult> queryForumResult(ForumBean bean) {
        String sql = "SELECT f.forum_groupid, s.forum_section_color FROM forum_info f, forum_section s WHERE f.forumid = s.forumid";
        Map<String, Object> params = new HashMap<>();
        params.put("bean", bean);
        return dataSource().nativeQueryList(ForumResult.class, sql, params);
    }

    public CompletableFuture<List<ForumResult>> queryForumResultAsync(ForumBean bean) {
        String sql = "SELECT f.forum_groupid, s.forum_section_color FROM forum_info f, forum_section s WHERE f.forumid = s.forumid";
        Map<String, Object> params = new HashMap<>();
        params.put("bean", bean);
        return dataSource().nativeQueryListAsync(ForumResult.class, sql, params);
    }

    @Override
    public DataSqlSource dataSource() {
        return _source;
    }

    @Override
    public Class<ForumInfo> entityType() {
        return _type;
    }

}
