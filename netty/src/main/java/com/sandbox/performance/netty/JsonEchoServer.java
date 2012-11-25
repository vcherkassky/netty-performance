package com.sandbox.performance.netty;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpServerCodec;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;


public class JsonEchoServer {
	
	private static final Logger logger = LoggerFactory.getLogger(JsonEchoServer.class);

	private final Executor bossExecutor = Executors.newCachedThreadPool();
	private final Executor workerExecutor = Executors.newCachedThreadPool();
	
	private final ChannelFactory channelFactory = new NioServerSocketChannelFactory(bossExecutor, workerExecutor);
	private final ChannelPipelineFactory pipelineFactory = new ChannelPipelineFactory() {

		@Override
		public ChannelPipeline getPipeline() throws Exception {
		
			ChannelPipeline p = Channels.pipeline();
			p.addLast("httpServerCodec", new HttpServerCodec());
			
			p.addLast("handler", new JsonEchoRequestHandler());
			
			return p;
		}
	};
	
	private final ServerBootstrap bootstrap = new ServerBootstrap(channelFactory); 
	
	ChannelGroup allChannels = new DefaultChannelGroup();
	
	public JsonEchoServer(int port) {
		
		bootstrap.setPipelineFactory(pipelineFactory);
		
        // Options for a parent channel
        bootstrap.setOption("localAddress", new InetSocketAddress(port));
        bootstrap.setOption("reuseAddress", true);

        // Options for its children
        bootstrap.setOption("child.tcpNoDelay", true);
        bootstrap.setOption("child.keepAlive", true);
//        b.setOption("child.receiveBufferSize", 1048576);
	}
	
	public void bind() {
		Channel parentChannel = bootstrap.bind();
		// add parent channel to group for easy shutdown
		allChannels.add(parentChannel);
	}
	
	public void shutdown() {
		allChannels.close().awaitUninterruptibly();
		bootstrap.releaseExternalResources();
	}
	
	private class JsonEchoRequestHandler extends SimpleChannelUpstreamHandler {

		@Override
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
//			logger.info("Message received: '{}'", e.getMessage());
			HttpRequest req = (HttpRequest) e.getMessage();
			
			DefaultHttpResponse res = null;
			HttpVersion version = req.getProtocolVersion();
			if(req.getMethod() == HttpMethod.GET) {
				//TODO: find a way not to parse URL parameters by hand
				String strUri = req.getUri();
//				logger.info("URI: '{}'", strUri);
				URI uri = URI.create(strUri);
				
				String path = uri.getPath();
				String query = uri.getQuery();
				Map<String, String> paramsMap = parseParams(query);
//				logger.trace("path: '{}', query: '{}', params: {}", new Object[]{path, query, paramsMap});

				if(!"/echo".equals(path))
					res = notFound(version, "{'status':'error', 'message':'Uri [" + strUri + "] not found'}");
				else if(!paramsMap.containsKey("message"))
					res = badRequest(version, "{'status':'error', 'message':'[message] is a required parameter'}");
				else {
					res = new DefaultHttpResponse(version, HttpResponseStatus.OK);
					res.setContent(ChannelBuffers.copiedBuffer("{'status':'ok', 'message':'Echo: " + paramsMap.get("message") + "'}", Charsets.UTF_8));
				}
			
			} else {
				res = new DefaultHttpResponse(version, HttpResponseStatus.METHOD_NOT_ALLOWED);
				res.setContent(ChannelBuffers.copiedBuffer("{'status':'error', 'message':'Only GET requests are  supported'}", Charsets.UTF_8));
			}

			res.setHeader("Content-Type", "application/json");

			Channel channel = ctx.getChannel();
			channel.write(res).addListener(CLOSE_CHANNEL_ON_COMPLETE);
		}

		private Map<String, String> parseParams(String query) {
			if(Strings.isNullOrEmpty(query))
				return Collections.emptyMap();
			
			String[] paramPairs = query.split("&");
			Map<String, String> paramsMap = new HashMap<String, String>(paramPairs.length * 4/3, 0.75f);
			for(String pair: paramPairs) {
				String[] pairSplit = pair.split("=", 2);
				
				if(pairSplit.length < 2)
					paramsMap.put(pairSplit[0], "");
				else
					paramsMap.put(pairSplit[0], pairSplit[1]);
			}
			return paramsMap;
		}
		
		private DefaultHttpResponse notFound(HttpVersion version, String message) {
			DefaultHttpResponse res = new DefaultHttpResponse(version, HttpResponseStatus.NOT_FOUND);
			res.setContent(ChannelBuffers.copiedBuffer(message, Charsets.UTF_8));
			return res;
		}
		
		private DefaultHttpResponse badRequest(HttpVersion version, String message) {
			DefaultHttpResponse res = new DefaultHttpResponse(version, HttpResponseStatus.BAD_REQUEST);
			res.setContent(ChannelBuffers.copiedBuffer(message, Charsets.UTF_8));
			return res;
		}

		@Override
		public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
			Channel channel = e.getChannel();
			allChannels.add(channel);
//			logger.info("Channel opened, {}", channel);
		}
		
		private final ChannelFutureListener CLOSE_CHANNEL_ON_COMPLETE = new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				future.getChannel().close();
			}
		};
	}
}
