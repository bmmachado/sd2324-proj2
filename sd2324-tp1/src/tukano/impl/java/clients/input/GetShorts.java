package tukano.impl.java.clients.input;

import java.io.IOException;
import java.util.logging.Logger;

import tukano.impl.api.java.ExtendedShorts;
import tukano.impl.java.clients.ClientFactory;
import tukano.impl.java.clients.Clients;


public class GetShorts {
	private static Logger Log = Logger.getLogger(GetShorts.class.getName());

	public static void main(String[] args) throws IOException {
		
		if( args.length != 1) {
			System.err.println( "Use: GetShorts userId");
			return;
		}

		String userId = args[0];

		ClientFactory<ExtendedShorts> shortClientFactory = Clients.ShortsClients;
		ExtendedShorts shortsClient = shortClientFactory.get();

		var result = shortsClient.getShorts(userId);
		if( result.isOK()  )
			Log.info("Get shorts:" + result.value() );
		else
			Log.info("Get shorts failed with error: " + result.error());
	}
	
}
