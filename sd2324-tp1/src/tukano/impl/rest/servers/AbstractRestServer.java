package tukano.impl.rest.servers;

import java.net.URI;
import java.util.logging.Logger;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import javax.net.ssl.SSLContext;

import tukano.impl.discovery.Discovery;
import tukano.impl.java.servers.AbstractServer;
import utils.IP;


public abstract class AbstractRestServer extends AbstractServer {

	private static final String SERVER_BASE_URI = "https://%s:%s%s";
	private static final String REST_CTX = "/rest";

	protected AbstractRestServer(Logger log, String service, int port) {
		super(log, service, String.format(SERVER_BASE_URI, IP.hostAddress(), port, REST_CTX));

	}

	protected void start() {
		
		ResourceConfig config = new ResourceConfig();
		
		registerResources( config );

    try {    
  		JdkHttpServerFactory.createHttpServer( URI.create(serverURI.replace(IP.hostAddress(), INETADDR_ANY)), config, SSLContext.getDefault());
		
  		Discovery.getInstance().announce(service, super.serverURI);
		
  		Log.info(String.format("%s Server ready @ %s\n",  service, serverURI));
    } catch (Exception e) {
      Log.info(String.format("%s Server NOT ready. Failed to start server\n"));
    }
	}
	
	abstract void registerResources( ResourceConfig config );
}
