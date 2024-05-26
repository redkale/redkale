package org.redkale.test.source.parser;

import java.util.*;
import org.redkale.convert.*;
import org.redkale.persistence.*;
import org.redkale.util.Utility;

/** @author zhangjx */
@Table(name = "forum_info", comment = "论坛信息表")
public class ForumInfo extends BaseEntity implements Comparable<ForumInfo> {

    @Id
    @Column(name = "forum_id", length = 64, comment = "论坛ID")
    private String forumid;

    @Column(name = "forum_name", length = 128, comment = "论坛的名称")
    private String forumName;

    @Column(name = "forum_groupid", length = 64, comment = "论坛分类的ID")
    private String forumGroupid;

    @Column(name = "forum_sections", length = 1024, comment = "论坛小版块的ID集合")
    private String[] forumSections;

    @Column(name = "forum_managerids", length = 1024, comment = "论坛小版块的ID集合")
    private Set<Integer> forumManagerids;

    @Column(name = "forum_face_url", length = 255, comment = "论坛的图片url")
    private String forumFaceUrl;

    @Column(name = "forum_css_img_url", length = 255, comment = "论坛的背景图url")
    private String forumCssImgUrl;

    @Column(name = "forum_css_url", length = 255, comment = "论坛的样式url")
    private String forumCssUrl;

    @Column(name = "forum_css_content", length = 10240, comment = "论坛的css内容")
    private String forumCssContent;

    @Column(name = "forum_bar_html", length = 1024, comment = "版块的提示栏")
    private String forumBarHtml;

    @Column(name = "forum_desc", length = 255, comment = "论坛说明")
    private String forumDesc;

    @Column(name = "forum_notice", length = 1024, comment = "论坛公告")
    private String forumNotice;

    @Column(comment = "被关注的用户数")
    private long followers;

    @Column(name = "post_count", comment = "帖子数")
    private long postCount;

    @Column(name = "like_count", comment = "关注数")
    private long likeCount;

    @Column(comment = "排序顺序，值小靠前")
    private int display = 1000;

    @Transient
    private Map<String, ForumSection> sections;

    public ForumSection findForumSection(String forumSectionid) {
        return sections == null ? null : sections.get(forumSectionid);
    }

    public synchronized void increLikeCount() {
        this.likeCount++;
    }

    public synchronized void increPostCount(String sectionid) {
        this.postCount++;
        if (sections != null && sectionid != null && !sectionid.isEmpty()) {
            ForumSection section = sections.get(sectionid);
            if (section != null) {
                section.increPostCount();
            }
        }
    }

    public synchronized void increFollowers() {
        this.followers++;
    }

    public synchronized void decreFollowers() {
        this.followers--;
    }

    public boolean containsManagerid(int userid) {
        return forumManagerids != null && forumManagerids.contains(userid);
    }

    public boolean containsSection(String forumSectionid) {
        return forumSections != null && forumSectionid != null && Utility.contains(forumSections, forumSectionid);
    }

    public boolean emptyForumNotice() {
        return this.forumNotice == null || this.forumNotice.isEmpty();
    }

    public boolean emptyCssImgUrl() {
        return this.forumCssImgUrl == null || this.forumCssImgUrl.isEmpty();
    }

    public boolean emptyCssUrl() {
        return this.forumCssUrl == null || this.forumCssUrl.isEmpty();
    }

    public boolean emptyCssContent() {
        return this.forumCssContent == null || this.forumCssContent.isEmpty();
    }

    public void setForumid(String forumid) {
        this.forumid = forumid;
    }

    public String getForumid() {
        return this.forumid;
    }

    public void setForumName(String forumName) {
        this.forumName = forumName;
    }

    public String getForumName() {
        return this.forumName;
    }

    public void setForumFaceUrl(String forumFaceUrl) {
        this.forumFaceUrl = forumFaceUrl;
    }

    public String getForumFaceUrl() {
        return this.forumFaceUrl;
    }

    public String getForumCssImgUrl() {
        return forumCssImgUrl;
    }

    public void setForumCssImgUrl(String forumCssImgUrl) {
        this.forumCssImgUrl = forumCssImgUrl;
    }

    public String getForumCssUrl() {
        return forumCssUrl;
    }

    public void setForumCssUrl(String forumCssUrl) {
        this.forumCssUrl = forumCssUrl;
    }

    public String getForumCssContent() {
        return forumCssContent;
    }

    public void setForumCssContent(String forumCssContent) {
        this.forumCssContent = forumCssContent;
    }

    public String[] getForumSections() {
        return forumSections;
    }

    public void setForumSections(String[] forumSections) {
        this.forumSections = forumSections;
    }

    public Set<Integer> getForumManagerids() {
        return forumManagerids;
    }

    public void setForumManagerids(Set<Integer> forumManagerids) {
        this.forumManagerids = forumManagerids;
    }

    public String getForumGroupid() {
        return forumGroupid;
    }

    public void setForumGroupid(String forumGroupid) {
        this.forumGroupid = forumGroupid;
    }

    public String getForumDesc() {
        return forumDesc;
    }

    public void setForumDesc(String forumDesc) {
        this.forumDesc = forumDesc;
    }

    public String getForumNotice() {
        return forumNotice;
    }

    public void setForumNotice(String forumNotice) {
        this.forumNotice = forumNotice;
    }

    public long getFollowers() {
        return followers;
    }

    public void setFollowers(long followers) {
        this.followers = followers;
    }

    public void setPostCount(long postCount) {
        this.postCount = postCount;
    }

    public long getPostCount() {
        return this.postCount;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(long likeCount) {
        this.likeCount = likeCount;
    }

    public void setDisplay(int display) {
        this.display = display;
    }

    @ConvertColumn(ignore = true, type = ConvertType.PROTOBUF_JSON)
    public int getDisplay() {
        return this.display;
    }

    @Override
    public int compareTo(ForumInfo o) {
        return this.display - o.display;
    }

    public Map<String, ForumSection> getSections() {
        return sections;
    }

    public void setSections(Map<String, ForumSection> sections) {
        this.sections = sections;
    }

    public String getForumBarHtml() {
        return forumBarHtml;
    }

    public void setForumBarHtml(String forumBarHtml) {
        this.forumBarHtml = forumBarHtml;
    }
}
