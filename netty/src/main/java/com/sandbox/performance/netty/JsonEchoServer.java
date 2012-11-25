package com.sandbox.performance.netty;

import static com.google.common.base.Charsets.UTF_8;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;

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
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpServerCodec;
import org.jboss.netty.handler.codec.http.HttpVersion;

import com.google.common.base.Strings;


public class JsonEchoServer {
	
//	private static final Logger logger = LoggerFactory.getLogger(JsonEchoServer.class);

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
					res = notFound("{'status':'error', 'message':'Uri [" + strUri + "] not found'}", version);
				else {
					String message = paramsMap.get("message");
					if(message == null)
    					res = badRequest("{'status':'error', 'message':'[message] is a required parameter'}", version);
    				else
    					res = genericResponse(OK, "{'status':'ok', 'message':'Echo: " + message + "'}", version);
				}
			
			} else {
				res = genericResponse(METHOD_NOT_ALLOWED, "{'status':'error', 'message':'Only GET requests are  supported'}", version);
			}

			res.setHeader(CONTENT_TYPE, "application/json");
			// set HTTP keep-alive header to response if it's contained in a request
			HttpHeaders.setKeepAlive(res, HttpHeaders.isKeepAlive(req));

			Channel channel = ctx.getChannel();
			// close channel when write is done
			Channels.write(channel, res).addListener(ChannelFutureListener.CLOSE);
		}

		private Map<String, String> parseParams(String query) {
			if(Strings.isNullOrEmpty(query))
				return Collections.emptyMap();
			
			String[] paramPairs = query.split("&");
			if(paramPairs.length == 1) {
				String[] pairSplit = paramPairs[0].split("=", 2);
				
				if(pairSplit.length < 2)
					return Collections.singletonMap(pairSplit[0], "");
				else
					return Collections.singletonMap(pairSplit[0], pairSplit[1]);
			} 
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
		
		private DefaultHttpResponse genericResponse(HttpResponseStatus status, String message, HttpVersion version) {
			DefaultHttpResponse res = new DefaultHttpResponse(version, status);
			res.setContent(ChannelBuffers.copiedBuffer(message, UTF_8));
			return res;
		}
		
		private DefaultHttpResponse notFound(String message, HttpVersion version) {
			DefaultHttpResponse res = new DefaultHttpResponse(version, NOT_FOUND);
			res.setContent(ChannelBuffers.copiedBuffer(message, UTF_8));
			return res;
		}
		
		private DefaultHttpResponse badRequest(String message, HttpVersion version) {
			DefaultHttpResponse res = new DefaultHttpResponse(version, BAD_REQUEST);
			res.setContent(ChannelBuffers.copiedBuffer(message, UTF_8));
			return res;
		}

		@Override
		public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
			Channel channel = e.getChannel();
			allChannels.add(channel);
//			logger.info("Channel opened, {}", channel);
		}
	}
}
