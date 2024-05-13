package tukano.impl.java.clients.input;

import tukano.impl.api.java.ExtendedShorts;
import tukano.impl.java.clients.ClientFactory;
import tukano.impl.java.clients.Clients;

import java.io.IOException;
import java.util.logging.Logger;

public class DeleteShort {
    private static Logger Log = Logger.getLogger(Short.class.getName());

    public static void main(String[] args) throws IOException {

        if (args.length != 2) {
            System.err.println("Use: Delete Short shortID password ");
            return;
        }

        String shortID = args[0];
        String password = args[1];

        ClientFactory<ExtendedShorts> shortClientFactory = Clients.ShortsClients;
        ExtendedShorts shortsClient = shortClientFactory.get();

        var result = shortsClient.deleteShort(shortID, password);
        if (result.isOK())
            Log.info("Deleted short:" + result.value());
        else
            Log.info("Delete short failed with error: " + result.error());
    }
}
