package tukano.impl.java.clients.input;

import tukano.impl.api.java.ExtendedShorts;
import tukano.impl.java.clients.ClientFactory;
import tukano.impl.java.clients.Clients;

import java.io.IOException;
import java.util.logging.Logger;

public class CreateShort {
    private static Logger Log = Logger.getLogger(Short.class.getName());

    public static void main(String[] args) throws IOException {

        if (args.length != 2) {
            System.err.println("Use: CreateShort userId password ");
            return;
        }

        String userId = args[0];
        String password = args[1];

        ClientFactory<ExtendedShorts> shortClientFactory = Clients.ShortsClients;
        ExtendedShorts shortsClient = shortClientFactory.get();

        var result = shortsClient.createShort(userId, password);
        if (result.isOK())
            Log.info("Created short:" + result.value());
        else
            Log.info("Create short failed with error: " + result.error());
    }

}
