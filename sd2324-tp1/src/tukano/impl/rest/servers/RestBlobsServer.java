package tukano.impl.rest.servers;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import com.github.scribejava.core.model.Response;
import org.glassfish.jersey.server.ResourceConfig;

import tukano.api.java.Blobs;
import tukano.impl.api.dropbox.DeleteAllArgs;
import tukano.impl.rest.servers.utils.CustomLoggingFilter;
import tukano.impl.rest.servers.utils.GenericExceptionMapper;
import utils.Args;
import utils.ExternalServices;


public class RestBlobsServer extends AbstractRestServer {
    public static final int PORT = 5678;

    private static final String DROPBOX_ROOT = "/tukano";
    private static Logger Log = Logger.getLogger(RestBlobsServer.class.getName());


    RestBlobsServer(int port) {
        super(Log, Blobs.NAME, port);
    }


    @Override
    void registerResources(ResourceConfig config) {
        config.register(RestBlobsResource.class);
        config.register(new GenericExceptionMapper());
        config.register(new CustomLoggingFilter());
    }

    public static void main(String[] args) throws Exception {
        Args.use(args);
        if (Args.contains(args, "-dropbox") && Args.valueOf("-dropbox", 1) == 0){

            ExternalServices newService = new ExternalServices();

            var all = new DeleteAllArgs(new LinkedList<>());
            all.addEntries(DROPBOX_ROOT);
            Response response = newService.delete(all.getEntries());
            if (response.getCode() == 200)
                Log.info("Deleting all from Dropbox\n " + response.getCode());
            if (response.getCode() == 404)
                Log.info("Nothing to delete\n " + response.getCode());
        }

        new RestBlobsServer(Args.valueOf("-port", PORT)).start();
    }
}