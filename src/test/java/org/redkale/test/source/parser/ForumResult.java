/*
 *
 */
package org.redkale.test.source.parser;

import org.redkale.convert.json.JsonConvert;

/** @author zhangjx */
public class ForumResult {

    private String forumGroupid;

    private String forumSectionColor;

    public String getForumGroupid() {
        return forumGroupid;
    }

    public void setForumGroupid(String forumGroupid) {
        this.forumGroupid = forumGroupid;
    }

    public String getForumSectionColor() {
        return forumSectionColor;
    }

    public void setForumSectionColor(String forumSectionColor) {
        this.forumSectionColor = forumSectionColor;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
