// Copyright (C) king.com Ltd 2015
// https://github.com/king/king-http-client
// Author: Magnus Gustafsson
// License: Apache 2.0, https://raw.github.com/king/king-http-client/LICENSE-APACHE

package com.king.platform.net.http.integration;


import com.king.platform.net.http.ConfKeys;
import com.king.platform.net.http.netty.NettyHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.slf4j.LoggerFactory.getLogger;

public class HttpGetWithRedirect {
	private final Logger logger = getLogger(getClass());

	private IntegrationServer integrationServer;
	private IntegrationServer httpsIntegrationServer;

	private NettyHttpClient httpClient;
	private int port;
	private int httpsPort;


	private String okBody = "EVERYTHING IS OKAY!";
	private RecordingEventBus recordingEventBus;

	@Before
	public void setUp() throws Exception {
		integrationServer = new JettyIntegrationServer();
		integrationServer.start();
		port = integrationServer.getPort();


		httpsIntegrationServer = new JettyIntegrationServer();
		httpsIntegrationServer.startHttps();
		httpsPort = httpsIntegrationServer.getPort();


		TestingHttpClientFactory testingHttpClientFactory = new TestingHttpClientFactory();
		recordingEventBus = testingHttpClientFactory.getRecordingEventBus();
		httpClient = testingHttpClientFactory.create();


		httpClient.setConf(ConfKeys.SSL_ALLOW_ALL_CERTIFICATES, true);
		httpClient.setConf(ConfKeys.IDLE_TIMEOUT_MILLIS, 2000);

		httpClient.start();

	}

	@Test
	public void getWithRedirect() throws Exception {

		integrationServer.addServlet(new HttpServlet() {
			@Override
			protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
				resp.sendRedirect("/test2");
			}
		}, "/test1");


		integrationServer.addServlet(new HttpServlet() {
			@Override
			protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
				resp.getWriter().write(okBody);
				resp.getWriter().flush();
			}
		}, "/test2");

		BlockingHttpCallback httpCallback = new BlockingHttpCallback();
		httpClient.createGet("http://localhost:" + port + "/test1").build().execute(httpCallback);
		httpCallback.waitForCompletion();

		assertEquals(okBody, httpCallback.getBody());
		assertEquals(200, httpCallback.getStatusCode());


	}


	@Test
	public void getWithRedirectWithoutFollowingRedirects() throws Exception {

		integrationServer.addServlet(new HttpServlet() {
			@Override
			protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
				resp.getWriter().write("Please follow my redirect!");

				resp.sendRedirect("/test2");
			}
		}, "/test1");


		integrationServer.addServlet(new HttpServlet() {
			@Override
			protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
				resp.getWriter().write(okBody);
				resp.getWriter().flush();
			}
		}, "/test2");

		BlockingHttpCallback httpCallback = new BlockingHttpCallback();
		httpClient.createGet("http://localhost:" + port + "/test1").followRedirects(false).build().execute(httpCallback);
		httpCallback.waitForCompletion();

		assertEquals("", httpCallback.getBody());
		assertEquals(302, httpCallback.getStatusCode());


	}

	@Test
	public void getWithCircleRedirects() throws Exception {

		integrationServer.addServlet(new HttpServlet() {
			@Override
			protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
				resp.sendRedirect("/test2");
			}
		}, "/test1");


		integrationServer.addServlet(new HttpServlet() {
			@Override
			protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
				resp.sendRedirect("/test1");
			}
		}, "/test2");

		BlockingHttpCallback httpCallback = new BlockingHttpCallback();
		httpClient.createGet("http://localhost:" + port + "/test1").build().execute(httpCallback);
		httpCallback.waitForCompletion(500, TimeUnit.MILLISECONDS);

		assertNotNull(httpCallback.getException());

		assertEquals("Max redirection count has been reached!", httpCallback.getException().getMessage());

	}

	@Test
	public void getRedirectToHttps() throws Exception {


		integrationServer.addServlet(new HttpServlet() {
			@Override
			protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
				resp.sendRedirect("https://localhost:" + httpsPort + "/test2");
			}
		}, "/test1");


		httpsIntegrationServer.addServlet(new HttpServlet() {
			@Override
			protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
				resp.getWriter().write(okBody);
				resp.getWriter().flush();
			}
		}, "/test2");

		BlockingHttpCallback httpCallback = new BlockingHttpCallback();
		httpClient.createGet("http://localhost:" + port + "/test1").build().execute(httpCallback);
		httpCallback.waitForCompletion();


		assertEquals(okBody, httpCallback.getBody());
		assertEquals(200, httpCallback.getStatusCode());


	}

	@After
	public void tearDown() throws Exception {
		integrationServer.shutdown();
		httpClient.shutdown();

	}


}