package com.sandbox.performance.tomcat.servlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.base.Strings;


public class JsonEchoServlet extends HttpServlet {
	private static final long serialVersionUID = -6969497866108892351L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		
		String messageParam = req.getParameter("message");
		
		resp.setContentType("application/json");
		
		if(Strings.isNullOrEmpty(messageParam)) {
			resp.setStatus(HttpServletResponse.SC_OK);
			resp.getWriter().write("{'status':'ok', 'message':'You have not specified any message'}");
		} else {
			resp.setStatus(HttpServletResponse.SC_OK);
			resp.getWriter().write("{'status':'ok', 'message':'Echo: " + messageParam + "'}");
		}
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// do not allow post requests
		resp.setContentType("application/json");
		resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
		resp.getWriter().write("{'status':'error', 'message':'POST requests are not supported'}");
	}

}
