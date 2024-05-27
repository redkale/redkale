/*
 *
 */
package org.redkale.test.source.parser;

import java.io.Serializable;

/** @author zhangjx */
public class ForumBean implements Serializable {

	private String forumSectionid;

	private String forumSectionColor;

	private String forumid;

	public String getForumSectionid() {
		return forumSectionid;
	}

	public void setForumSectionid(String forumSectionid) {
		this.forumSectionid = forumSectionid;
	}

	public String getForumSectionColor() {
		return forumSectionColor;
	}

	public void setForumSectionColor(String forumSectionColor) {
		this.forumSectionColor = forumSectionColor;
	}

	public String getForumid() {
		return forumid;
	}

	public void setForumid(String forumid) {
		this.forumid = forumid;
	}
}
