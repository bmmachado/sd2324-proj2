package tukano.impl.java.clients;

import tukano.api.java.Blobs;
import tukano.api.java.Shorts;
import tukano.api.java.Users;
import tukano.impl.api.java.ExtendedBlobs;
import tukano.impl.api.java.ExtendedShorts;
import tukano.impl.grpc.clients.GrpcBlobsClient;
import tukano.impl.grpc.clients.GrpcShortsClient;
import tukano.impl.grpc.clients.GrpcUsersClient;
import tukano.impl.rest.clients.RestBlobsClient;
import tukano.impl.rest.clients.RestShortsClient;
import tukano.impl.rest.clients.RestUsersClient;

public class Clients {
	public static final ClientFactory<Users> UsersClients = new ClientFactory<>(
			Users.NAME,
			serverURI -> {
				try {
					return new RestUsersClient(serverURI);
				} catch (Exception e) {
					throw new RuntimeException("Error creating RestUsersClient", e);
				}
			},
			serverURI -> {
				try {
					return new GrpcUsersClient(serverURI);
				} catch (Exception e) {
					throw new RuntimeException("Error creating GrpcUsersClient", e);
				}
			}
	);
	
	public static final ClientFactory<ExtendedBlobs> BlobsClients = new ClientFactory<>(
			Blobs.NAME,
			serverURI -> {
				try {
					return new RestBlobsClient(serverURI);
				} catch (Exception e) {
					throw new RuntimeException("Error creating RestBlobsClient", e);
				}
			},
			serverURI -> {
				try {
					return new GrpcBlobsClient(serverURI);
				} catch (Exception e) {
					throw new RuntimeException("Error creating GrpcBlobsClient", e);
				}
			}
	);

	public static final ClientFactory<ExtendedShorts> ShortsClients = new ClientFactory<>(
			Shorts.NAME,
			serverURI -> {
				try {
					return new RestShortsClient(serverURI);
				} catch (Exception e) {
					throw new RuntimeException("Error creating RestShortsClient", e);
				}
			},
			serverURI -> {
				try {
					return new GrpcShortsClient(serverURI);
				} catch (Exception e) {
					throw new RuntimeException("Error creating GrpcShortsClient", e);
				}
			}
	);
}
