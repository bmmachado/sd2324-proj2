package tukano.api.java;

import java.net.URI;
import java.util.function.Consumer;

/**
 * Interface of blob service for storing short videos media ...
 */
public interface Blobs {
    String NAME = "blobs";

    /**
     * Uploads a short video blob resource. Must validate the blobId to ensure it
     * was generated by the Shorts service.
     *
     * @param blobId    the id of the blob;
     * @param timestamp the time limite to trf the blob;
     * @param verifier  the identifier generated by the Shorts service for this blob
     *                  based on sharedKey, timestamp and blobHostName
     * @param bytes     the contents in bytes of the blob resource
     * @return OK(void) if the upload is new or if the blobId and bytes match an
     * existing blob;
     * CONFLICT if a blobId exists but bytes do not match;
     * FORBIDDEN if the blobId is not valid
     */
    Result<Void> upload(String blobId, String timestamp, String verifier, byte[] bytes);

    /**
     * Downloads a short video blob resource in a single byte chunk of bytes.
     *
     * @param blobId    the id of the blob;
     * @param timestamp the time limite to trf the blob;
     * @param verifier  the identifier generated by the Shorts service for this blob
     *                  based on sharedKey, timestamp and blobHostName
     * @return (OK, bytes), if the blob exists;
     * NOT_FOUND, if no blob matches the provided blobId
     */
    Result<byte[]> download(String blobId, String timestamp, String verifier);

    /**
     * Downloads a short video blob resource as a result suitable for streaming
     * large-sized byte resources
     * <p>
     * The default implementation just sinks a single chunk of bytes taken from download(blobId)
     *
     * @param blobId    the id of the blob
     * @param sink      - the consumer of the chunks of data
     * @return (OK, ), if the blob exists;
     * NOT_FOUND, if no blob matches the provided blobId
     */
    default Result<Void> downloadToSink(String blobId, Consumer<byte[]> sink) {
        var timestamp = blobId.substring(blobId.indexOf("timestamp=") + 10, blobId.indexOf("&"));
        var sharedSecret = blobId.substring(blobId.indexOf("verifier=") + 9);
        var res = download(blobId, timestamp, sharedSecret);
        if (!res.isOK())
            return Result.error(res.error());

        sink.accept(res.value());
        return Result.ok();
    }
}
