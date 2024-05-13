package tukano.impl.java.clients.input;

import tukano.api.User;
import tukano.api.java.Users;
import tukano.impl.java.clients.ClientFactory;
import tukano.impl.java.clients.Clients;

import java.io.IOException;
import java.util.logging.Logger;


public class UpdateUser {
    private static Logger Log = Logger.getLogger(UpdateUser.class.getName());

    public static void main(String[] args) throws IOException {

         if (args.length != 5) {
            System.err.println("Use: java tukano.clients.UpdateUser userId pwd newPwd email displayName");
            return;
        }

        String userId = args[0];
        String pwd = args[1];
        String newPwd = args[2];
        String email = args[3];
        String displayName = args[4];

        ClientFactory<Users> usersClientFactory = Clients.UsersClients;
        Users usersClient = usersClientFactory.get();;

        var user = new User(userId, newPwd, email, displayName);

        var result = usersClient.updateUser(userId, pwd, user);
            if (result.isOK())
                    Log.info("Update user:" + result.value());
            else
                    Log.info("Update user failed with error: " + result.error());

    }
}
