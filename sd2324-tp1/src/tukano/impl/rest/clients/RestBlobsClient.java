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

    private Result<Void> _upload(String blobURL, byte[] bytes) {
        String verifier = blobURL.substring(blobURL.lastIndexOf("/") + 1);
        return super.toJavaResult(
                target.path(verifier)
                        .request()
                        .post(Entity.entity(bytes, MediaType.APPLICATION_OCTET_STREAM)));

    }

    private Result<byte[]> _download(String blobURL) {
        String verifier = blobURL.substring(blobURL.lastIndexOf("/") + 1);
        return super.toJavaResult(
                target.path(verifier)
                        .request()
                        .accept(MediaType.APPLICATION_OCTET_STREAM)
                        .get(), byte[].class);
    }

    private Result<Void> _delete(String blobURL, String token) {
        return super.toJavaResult(
                client.target(blobURL)
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
    public Result<Void> upload(String blobURL, byte[] bytes) {
        return super.reTry(() -> _upload(blobURL, bytes));
    }

    @Override
    public Result<byte[]> download(String blobURL) {
        return super.reTry(() -> _download(blobURL));
    }

    @Override
    public Result<Void> delete(String blobURL, String token) {
        return super.reTry(() -> _delete(blobURL, token));
    }

    @Override
    public Result<Void> deleteAllBlobs(String userId, String password) {
        return super.reTry(() -> _deleteAllBlobs(userId, password));
    }
}
