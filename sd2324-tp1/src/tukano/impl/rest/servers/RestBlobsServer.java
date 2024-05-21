package tukano.impl.rest.servers;

import java.util.logging.Logger;

import org.glassfish.jersey.server.ResourceConfig;

import tukano.api.java.Blobs;
import tukano.impl.rest.servers.utils.CustomLoggingFilter;
import tukano.impl.rest.servers.utils.GenericExceptionMapper;
import utils.Args;

import static utils.ExternalServices.cleanDropbox;


public class RestBlobsServer extends AbstractRestServer {
    public static final int PORT = 5678;
    public static String ignoreDropboxState;
    private static final Logger Log = Logger.getLogger(RestBlobsServer.class.getName());



    RestBlobsServer(int port, String dropboxState) {
        super(Log, Blobs.NAME, port);
        ignoreDropboxState = dropboxState;
    }


    @Override
    void registerResources(ResourceConfig config) {
        config.register(RestBlobsResource.class);
        config.register(new GenericExceptionMapper());
        config.register(new CustomLoggingFilter());
    }

    public static void main(String[] args) throws Exception {
        Args.use(args);
        if (args.length > 0 && Args.isBoolean(args[0])) {
            if (args[0].equalsIgnoreCase("true"))
                cleanDropbox();

            new RestBlobsServer(Args.valueOf("-port", PORT), args[0]).start();
        }
        else new RestBlobsServer(Args.valueOf("-port", PORT), null).start();
    }
}