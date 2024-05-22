package tukano.impl.java.clients.input;

import tukano.impl.api.java.ExtendedShorts;
import tukano.impl.java.clients.ClientFactory;
import tukano.impl.java.clients.Clients;

import java.io.IOException;
import java.util.logging.Logger;

public class Follow {
    private static Logger Log = Logger.getLogger(Follow.class.getName());

    public static void main(String[] args) throws IOException {

        if (args.length != 4) {
            System.err.println("Use: Follow userId1 userId2 isFollow password");
            return;
        }

        String userId1 = args[0];
        String userId2 = args[1];
        Boolean isFollow = Boolean.parseBoolean(args[2]);
        String password = args[3];

        ClientFactory<ExtendedShorts> shortClientFactory = Clients.ShortsClients;
        ExtendedShorts shortsClient = shortClientFactory.get();

        var result = shortsClient.follow(userId1, userId2, isFollow, password);
        if (result.isOK())
            Log.info("Follow:" + userId2 + " " + result.value());
        else
            Log.info("follow " + userId2 + " failed with error: " + result.error());
    }

}
