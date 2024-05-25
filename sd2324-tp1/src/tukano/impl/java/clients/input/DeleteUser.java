package tukano.impl.java.clients.input;

import tukano.api.java.Users;
import tukano.impl.java.clients.ClientFactory;
import tukano.impl.java.clients.Clients;

import java.io.IOException;
import java.util.logging.Logger;


public class DeleteUser {
    private static final Logger Log = Logger.getLogger(DeleteUser.class.getName());

    public static void main(String[] args) throws IOException {

        if(args.length != 2){
            System.err.println( "Use: java lab2.clients.GetUser url userId password");
            return;
        }

        String userId = args[0];
        String pwd = args[1];

        ClientFactory<Users> usersClientFactory = Clients.UsersClients;
        Users usersClient = usersClientFactory.get();

        var result = usersClient.deleteUser(userId, pwd);
        if (result.isOK())
            Log.info("Delete user:" + result.value());
        else
            Log.info("Delete user failed with error: " + result.error());

    }

}
