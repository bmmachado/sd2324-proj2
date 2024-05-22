package tukano.impl.java.clients.input;

import java.io.IOException;
import java.util.logging.Logger;

import tukano.impl.api.java.ExtendedShorts;
import tukano.impl.java.clients.ClientFactory;
import tukano.impl.java.clients.Clients;


public class GetShort {
	private static Logger Log = Logger.getLogger(GetShort.class.getName());

	public static void main(String[] args) throws IOException {
		
		if( args.length != 1) {
			System.err.println( "Use: GetShort shortId");
			return;
		}

		String shortId = args[0];

		ClientFactory<ExtendedShorts> shortClientFactory = Clients.ShortsClients;
		ExtendedShorts shortsClient = shortClientFactory.get();

		var result = shortsClient.getShort(shortId);
		if( result.isOK()  )
			Log.info("Get short:" + result.value() );
		else
			Log.info("Get short failed with error: " + result.error());
	}
	
}
