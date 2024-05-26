package org.redkale.test.source.parser;

import java.util.Set;
import org.redkale.convert.*;
import org.redkale.persistence.*;

/** @author zhangjx */
@Table(name = "forum_section", comment = "论坛小版块信息表")
public class ForumSection extends BaseEntity {

    @Id
    @Column(name = "forum_sectionid", length = 64, comment = "论坛小版块ID")
    private String forumSectionid;

    @Column(length = 64, comment = "论坛ID")
    private String forumid;

    @Column(name = "forum_section_name", length = 128, comment = "论坛小版块的名称")
    private String forumSectionName;

    @Column(name = "forum_section_face_url", length = 255, comment = "论坛小版块的图标url")
    private String forumSectionFaceUrl;

    @Column(name = "forum_section_managerids", length = 1024, comment = "论坛小版块的ID集合")
    private Set<Integer> forumSectionManagerids;

    @Column(name = "forum_section_desc", length = 32, comment = "论坛小版块说明")
    private String forumSectionDesc;

    @Column(name = "forum_section_color", length = 255, comment = "论坛小版块小标题的背景色")
    private String forumSectionColor;

    @Column(name = "forum_section_bar_html", length = 1024, comment = "版块的提示栏")
    private String forumSectionBarHtml;

    @Column(name = "post_count", comment = "帖子数")
    private long postCount;

    @Column(comment = "排序顺序，值小靠前")
    private int display = 1000;

    public void increPostCount() {
        this.postCount++;
    }

    public boolean containsSectionManagerid(int userid) {
        return forumSectionManagerids != null && forumSectionManagerids.contains(userid);
    }

    public void setForumSectionid(String forumSectionid) {
        this.forumSectionid = forumSectionid;
    }

    public String getForumSectionid() {
        return this.forumSectionid;
    }

    public String getForumid() {
        return forumid;
    }

    public void setForumid(String forumid) {
        this.forumid = forumid;
    }

    public void setForumSectionName(String forumSectionName) {
        this.forumSectionName = forumSectionName;
    }

    public String getForumSectionName() {
        return this.forumSectionName;
    }

    public void setForumSectionFaceUrl(String forumSectionFaceUrl) {
        this.forumSectionFaceUrl = forumSectionFaceUrl;
    }

    public String getForumSectionFaceUrl() {
        return this.forumSectionFaceUrl;
    }

    public String getForumSectionColor() {
        return forumSectionColor;
    }

    public void setForumSectionColor(String forumSectionColor) {
        this.forumSectionColor = forumSectionColor;
    }

    public void setForumSectionDesc(String forumSectionDesc) {
        this.forumSectionDesc = forumSectionDesc;
    }

    public Set<Integer> getForumSectionManagerids() {
        return forumSectionManagerids;
    }

    public void setForumSectionManagerids(Set<Integer> forumSectionManagerids) {
        this.forumSectionManagerids = forumSectionManagerids;
    }

    public String getForumSectionDesc() {
        return this.forumSectionDesc;
    }

    public void setPostCount(long postCount) {
        this.postCount = postCount;
    }

    public long getPostCount() {
        return this.postCount;
    }

    public void setDisplay(int display) {
        this.display = display;
    }

    @ConvertColumn(ignore = true, type = ConvertType.PROTOBUF_JSON)
    public int getDisplay() {
        return this.display;
    }

    public String getForumSectionBarHtml() {
        return forumSectionBarHtml;
    }

    public void setForumSectionBarHtml(String forumSectionBarHtml) {
        this.forumSectionBarHtml = forumSectionBarHtml;
    }
}
