package tukano.api.rest;


import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path(RestBlobs.PATH)
public interface RestBlobs {
	
	String PATH = "/blobs";
	String BLOB_ID = "blobId";
	String TIMESTAMP = "timestamp";
	String VERIFIER = "verifier";
 
 	@POST
 	@Path("/{" + BLOB_ID +"}")
 	@Consumes(MediaType.APPLICATION_OCTET_STREAM)
	void upload(@PathParam(BLOB_ID) String blobId, byte[] bytes, @QueryParam(TIMESTAMP) long timestamp, @QueryParam(VERIFIER) String verifier);

	@GET
	@Path("/{" + BLOB_ID +"}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	byte[] download(@PathParam(BLOB_ID) String blobId, @QueryParam(TIMESTAMP) long timestamp, @QueryParam(VERIFIER) String verifier);
}
