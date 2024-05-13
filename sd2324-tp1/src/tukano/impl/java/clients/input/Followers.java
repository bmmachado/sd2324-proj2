package tukano.impl.java.clients.input;

import tukano.impl.api.java.ExtendedShorts;
import tukano.impl.java.clients.ClientFactory;
import tukano.impl.java.clients.Clients;

import java.io.IOException;
import java.util.logging.Logger;

public class Followers {
    private static Logger Log = Logger.getLogger(Followers.class.getName());

    public static void main(String[] args) throws IOException {

        if (args.length != 2) {
            System.err.println("Use: Follow userId password");
            return;
        }

        String userId1 = args[0];
        String password = args[1];

        ClientFactory<ExtendedShorts> shortClientFactory = Clients.ShortsClients;
        ExtendedShorts shortsClient = shortClientFactory.get();

        var result = shortsClient.followers(userId1, password);
        if (result.isOK())
            Log.info("Followers: "  + result.value());
        else
            Log.info("followers failed with error: " + result.error());
    }

}
