package tukano.impl.java.clients.input;

import java.io.IOException;
import java.util.logging.Logger;

import tukano.api.java.Users;
import tukano.impl.java.clients.ClientFactory;
import tukano.impl.java.clients.Clients;


public class GetUser {
	private static Logger Log = Logger.getLogger(GetUser.class.getName());

	public static void main(String[] args) throws IOException {
		
		if( args.length != 2) {
			System.err.println( "Use: java tukano.clients.GetUser userId password");
			return;
		}

		//String serverUrl = args[0];
		String userId = args[0];
		String pwd = args[1];

		ClientFactory<Users> usersClientFactory = Clients.UsersClients;
		Users usersClient = usersClientFactory.get();

		var result = usersClient.getUser(userId, pwd);
		if( result.isOK()  )
			Log.info("Get user:" + result.value() );
		else
			Log.info("Get user failed with error: " + result.error());
	}
	
}
