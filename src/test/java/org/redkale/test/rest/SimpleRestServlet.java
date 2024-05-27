package org.redkale.test.rest;

import java.io.IOException;
import org.redkale.annotation.Resource;
import org.redkale.net.http.*;
import org.redkale.service.RetResult;

public class SimpleRestServlet extends HttpServlet {

	protected static final RetResult RET_UNLOGIN = RetCodes.retResult(RetCodes.RET_USER_UNLOGIN);

	protected static final RetResult RET_AUTHILLEGAL = RetCodes.retResult(RetCodes.RET_USER_AUTH_ILLEGAL);

	@Resource
	private UserService userService = new UserService();

	@Override
	public void preExecute(HttpRequest request, HttpResponse response) throws IOException {
		final String sessionid = request.getSessionid(true);
		if (sessionid != null) {
			UserInfo user = userService.current(sessionid);
			if (user != null) request.setCurrentUserid(user.getUserid());
		}
		response.nextEvent();
	}

	// 普通鉴权
	@Override
	public void authenticate(HttpRequest request, HttpResponse response) throws IOException {
		int userid = request.currentUserid(int.class);
		if (userid < 1) {
			response.finishJson(RET_UNLOGIN);
			return;
		}
		response.nextEvent();
	}
}
