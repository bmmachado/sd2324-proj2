package tukano.impl.java.clients.input;

import tukano.impl.api.java.ExtendedShorts;
import tukano.impl.java.clients.ClientFactory;
import tukano.impl.java.clients.Clients;

import java.io.IOException;
import java.util.logging.Logger;

public class Like {
    private static Logger Log = Logger.getLogger(Like.class.getName());

    public static void main(String[] args) throws IOException {

        if (args.length != 4) {
            System.err.println("Use: Like shortId userId isLike password");
            return;
        }

        String shortId = args[0];
        String userId = args[1];
        Boolean isLike = Boolean.parseBoolean(args[2]);
        String password = args[3];

        ClientFactory<ExtendedShorts> shortClientFactory = Clients.ShortsClients;
        ExtendedShorts shortsClient = shortClientFactory.get();

        var result = shortsClient.like(shortId, userId, isLike, password);
        if (result.isOK())
            Log.info("Like:" + shortId + " " + result.value());
        else
            Log.info("Like " + shortId + " failed with error: " + result.error());
    }
}
