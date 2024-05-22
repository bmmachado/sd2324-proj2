package tukano.impl.java.clients.input;

import tukano.api.java.Users;
import tukano.impl.java.clients.ClientFactory;
import tukano.impl.java.clients.Clients;

import java.io.IOException;
import java.util.logging.Logger;


public class SearchUsers {
    private static Logger Log = Logger.getLogger(SearchUsers.class.getName());

    public static void main(String[] args) throws IOException {

        String pattern = "";
        if(args.length == 1)
            pattern = args[0];

        ClientFactory<Users> usersClientFactory = Clients.UsersClients;
        Users usersClient = usersClientFactory.get();

        var result = usersClient.searchUsers(pattern);
        if (result.isOK())
            Log.info("Search users:" + result.value());
        else
            Log.info("Search users failed with error: " + result.error());

    }

}
