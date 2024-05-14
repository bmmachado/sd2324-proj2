package tukano.impl.rest.servers;

import java.util.LinkedList;
import java.util.logging.Logger;

import com.github.scribejava.core.model.Response;
import org.glassfish.jersey.server.ResourceConfig;

import tukano.api.java.Blobs;
import tukano.impl.api.dropbox.DeleteAllArgs;
import tukano.impl.rest.servers.utils.CustomLoggingFilter;
import tukano.impl.rest.servers.utils.GenericExceptionMapper;
import utils.Args;
import utils.ExternalServices;
import utils.IP;


public class RestBlobsServer extends AbstractRestServer {
    public static final int PORT = 5678;

    public static final String BLOBS_DROPBOX = "/tukano/"+ IP.hostName();
   // public static final String DROPBOX_ROOT = "/tukano";
    private static final Logger Log = Logger.getLogger(RestBlobsServer.class.getName());
    public static String ignoreDropboxState;


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

    static void cleanDropbox() {
        ExternalServices newService = new ExternalServices();
        var all = new DeleteAllArgs(new LinkedList<>());
        all.addEntries(BLOBS_DROPBOX);
        try {
            Response response = newService.delete(all.getEntries());
            if (response.getCode() == 200)
                Log.info("Deleting all from Dropbox\n " + response.getCode());
            if (response.getCode() == 404)
                Log.info("Nothing to delete\n " + response.getCode());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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