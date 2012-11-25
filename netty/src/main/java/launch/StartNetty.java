package launch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sandbox.performance.netty.JsonEchoServer;


public class StartNetty {
	
	private static final Logger logger = LoggerFactory.getLogger(StartNetty.class);
	
	public static void main(String[] args) {

		// The port that we should run on can be set into an environment variable
        // Look for that variable and default to 5100 if it isn't there.
        String webPort = System.getenv("PORT");
        if (webPort == null || webPort.isEmpty()) {
            webPort = "5100";
        }
        logger.info("Starting netty on port {}", webPort);

		JsonEchoServer server = new JsonEchoServer(Integer.parseInt(webPort));

		server.bind();
		logger.info("Netty is ready to accept connections");
	}

}
