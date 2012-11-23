package launch;

import java.io.File;

import javax.servlet.ServletException;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;


public class StartTomcat {
	
	public static void main(String[] args) throws ServletException, LifecycleException {

        Tomcat tomcat = new Tomcat();

        // The port that we should run on can be set into an environment variable
        // Look for that variable and default to 5000 if it isn't there.
        String webPort = System.getenv("PORT");
        if (webPort == null || webPort.isEmpty()) {
            webPort = "5000";
        }

        String webappDirLocation = "src/main/webapp/";

        tomcat.setPort(Integer.valueOf(webPort));

        String path = new File(webappDirLocation).getAbsolutePath();
        tomcat.addWebapp("/", path);

        tomcat.start();
        tomcat.getServer().await();
	}

}
