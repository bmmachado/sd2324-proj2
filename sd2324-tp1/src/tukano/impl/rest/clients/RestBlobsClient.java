package tukano.impl.rest.clients;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tukano.api.java.Result;
import tukano.api.rest.RestBlobs;
import tukano.impl.api.java.ExtendedBlobs;
import tukano.impl.api.rest.RestExtendedBlobs;

import java.net.URI;

public class RestBlobsClient extends RestClient implements ExtendedBlobs {

    public RestBlobsClient(String serverURI) {
        super(serverURI, RestBlobs.PATH);
    }

    private Result<Void> _upload(String blobId, String timestamp, String verifier, byte[] bytes) {
        /*String blobID = blobURL.substring(blobURL.lastIndexOf("/") + 1, blobURL.lastIndexOf("?"));
        String timestamp = blobURL.substring(blobURL.indexOf("=") + 1, blobURL.lastIndexOf("&"));
        String verifier = blobURL.substring(blobURL.lastIndexOf("=") + 1);*/
        return super.toJavaResult(
                target.path(blobId)
                        .path(RestExtendedBlobs.BLOBS)
                        .queryParam("timestamp", timestamp)
                        .queryParam("verifier", verifier)
                        .request()
                        .post(Entity.entity(bytes, MediaType.APPLICATION_OCTET_STREAM)));

    }

    private Result<byte[]> _download(String blobId, String timestamp, String verifier) {
       /* String blobID = blobURL.substring(blobURL.lastIndexOf("/") + 1, blobURL.lastIndexOf("?"));
        String timestamp = blobURL.substring(blobURL.indexOf("=") + 1, blobURL.lastIndexOf("&"));
        String verifier = blobURL.substring(blobURL.lastIndexOf("=") + 1);*/
        return super.toJavaResult(
                target.path(blobId)
                        .path(RestExtendedBlobs.BLOBS)
                        .queryParam("timestamp", timestamp)
                        .queryParam("verifier", verifier)
                        .request()
                        .accept(MediaType.APPLICATION_OCTET_STREAM)
                        .get(), byte[].class);
    }

    private Result<Void> _delete(String blobId, String token) {
        return super.toJavaResult(
                client.target(blobId)
                        .queryParam(RestExtendedBlobs.TOKEN, token)
                        .request()
                        .delete());
    }

    private Result<Void> _deleteAllBlobs(String userId, String token) {
        return super.toJavaResult(
                target.path(userId)
                        .path(RestExtendedBlobs.BLOBS)
                        .queryParam(RestExtendedBlobs.TOKEN, token)
                        .request()
                        .delete());
    }

    @Override
    public Result<Void> upload(String blobId, String timestamp, String verifier, byte[] bytes) {
        return super.reTry(() -> _upload(blobId, timestamp, verifier, bytes));
    }

    @Override
    public Result<byte[]> download(String blobId, String timestamp, String verifier) {
        return super.reTry(() -> _download(blobId, timestamp, verifier));
    }

    @Override
    public Result<Void> delete(String blobId, String token) {
        return super.reTry(() -> _delete(blobId, token));
    }

    @Override
    public Result<Void> deleteAllBlobs(String userId, String password) {
        return super.reTry(() -> _deleteAllBlobs(userId, password));
    }
}
