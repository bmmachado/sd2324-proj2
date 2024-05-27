package tukano.api.rest;


import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.net.URI;

@Path(RestBlobs.PATH)
public interface RestBlobs {
	
	String PATH = "/blobs";
	String BLOB_ID = "blobId";
 
 	@POST
 	@Path("/{" + BLOB_ID +"}")
 	@Consumes(MediaType.APPLICATION_OCTET_STREAM)
	void upload(@PathParam(BLOB_ID) String blobId, @QueryParam("timestamp") String timestamp, @QueryParam("verifier") String verifier, byte[] bytes);


 	@GET
 	@Path("/{" + BLOB_ID +"}") 	
 	@Produces(MediaType.APPLICATION_OCTET_STREAM)
 	byte[] download(@PathParam(BLOB_ID) String blobId, @QueryParam("timestamp") String timestamp, @QueryParam("verifier") String verifier);
}
