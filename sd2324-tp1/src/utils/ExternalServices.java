package utils;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.gson.Gson;
import org.pac4j.scribe.builder.api.DropboxApi20;
import tukano.impl.api.dropbox.*;
import tukano.impl.java.servers.JavaBlobs;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

final public class ExternalServices {
    private static Logger Log = Logger.getLogger(ExternalServices.class.getName());
    public static final int HTTP_SUCCESS = 200;
    protected static final int HTTP_CONFLIT = 409;
    private static final String CONTENT_TYPE_HDR = "Content-Type";
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
    private static final String JSON_CONTENT_TYPE_OCTET = "application/octet-stream";
    private static OAuth2AccessToken accessToken;
    private static Gson json;
    private static OAuth20Service service = null;

    public ExternalServices() {
        String apiKey = Props.getValue("APP_KEY");
        String apiSecret = Props.getValue("APP_SECRET");
        json = new Gson();
        accessToken = new OAuth2AccessToken(Props.getValue("APP_ACCESS_TOKEN"));
        service = new ServiceBuilder(apiKey).apiSecret(apiSecret).build(DropboxApi20.INSTANCE);
    }

    public byte[] read(String path) throws Exception {
        //path = "/tukano/blobsUserId/blobId.mpeg
        var downloadFile = new OAuthRequest(Verb.POST, Props.getValue("APP_URL_DOWNLOAD"));
        downloadFile.addHeader(CONTENT_TYPE_HDR, JSON_CONTENT_TYPE_OCTET);
        downloadFile.addHeader("Dropbox-API-Arg", json.toJson(new DownloadArgs(path)));
        service.signRequest(accessToken, downloadFile);

        Response r = service.execute(downloadFile);

        if (r.getCode() != HTTP_SUCCESS)
            throw new RuntimeException(String.format("Failed to download file: %s, Status: %d, \nReason: %s\n", path, r.getCode(), r.getBody()));

        return r.getBody().getBytes();
    }

    public void write(String path, byte[] bytes) throws Exception {
        //path = "/tukano/blobsUserId/blobId.mpeg

        var uploadFile = new OAuthRequest(Verb.POST, Props.getValue("APP_URL_UPLOAD"));
        uploadFile.addHeader(CONTENT_TYPE_HDR, JSON_CONTENT_TYPE_OCTET);
        uploadFile.addHeader("Dropbox-API-Arg", json.toJson(new UploadArgs(path, false, "add", false, false)));
        uploadFile.setPayload(bytes);

        service.signRequest(accessToken, uploadFile);

        Response r = service.execute(uploadFile);

        if (r.getCode() != HTTP_SUCCESS)
            throw new RuntimeException(String.format("Failed to create directory: %s, Status: %d, \nReason: %s\n", path, r.getCode(), r.getBody()));

    }

    public void delete(String path) throws Exception {
        //path = "/tukano/blobsUserId/blobId.mpeg

        var deleteFile = new OAuthRequest(Verb.POST, Props.getValue("APP_URL_DELETE"));
        deleteFile.addHeader(CONTENT_TYPE_HDR, JSON_CONTENT_TYPE);
        deleteFile.setPayload(json.toJson(new DeleteV2Args(path)));

        service.signRequest(accessToken, deleteFile);

        Response r = service.execute(deleteFile);

        if (r.getCode() != HTTP_SUCCESS)
            throw new RuntimeException(String.format("Failed to delete file: %s, Status: %d, \nReason: %s\n", path, r.getCode(), r.getBody()));

    }

    public Response delete(List<DeleteAllArgs.Entry> entries) throws Exception {
        //path = "/tukano/blobsUserId/blobId.mpeg

        var deleteAll = new OAuthRequest(Verb.POST, Props.getValue("APP_URL_DELETE_BATCH"));
        deleteAll.addHeader(CONTENT_TYPE_HDR, JSON_CONTENT_TYPE);
        deleteAll.setPayload(json.toJson(new DeleteAllArgs(entries)));

        service.signRequest(accessToken, deleteAll);

        return service.execute(deleteAll);
    }

    public boolean createDirectory( String directoryName ) throws Exception {

        var createFolder = new OAuthRequest(Verb.POST, Props.getValue("APP_URL_CREATE_FOLDER"));
        createFolder.addHeader(CONTENT_TYPE_HDR, JSON_CONTENT_TYPE);

        createFolder.setPayload(json.toJson(new CreateFolderV2Args(directoryName, false)));

        service.signRequest(accessToken, createFolder);

        Response r = service.execute(createFolder);

        return r.getCode() == HTTP_SUCCESS || r.getCode() == HTTP_CONFLIT;
    }

    public List<String> listDirectory(String directoryName) throws Exception {
        var directoryContents = new ArrayList<String>();

        var listDirectory = new OAuthRequest(Verb.POST, Props.getValue("APP_URL_LIST_FOLDER"));
        listDirectory.addHeader(CONTENT_TYPE_HDR, JSON_CONTENT_TYPE);
        listDirectory.setPayload(json.toJson(new ListFolderArgs(directoryName)));

        service.signRequest(accessToken, listDirectory);

        Response r = service.execute(listDirectory);
        if (r.getCode() != HTTP_SUCCESS)
            throw new RuntimeException(String.format("Failed to list directory: %s, Status: %d, \nReason: %s\n", directoryName, r.getCode(), r.getBody()));

        var reply = json.fromJson(r.getBody(), ListFolderReturn.class);
        reply.getEntries().forEach(e -> directoryContents.add(e.toString()));

        while (reply.has_more()) {
            listDirectory = new OAuthRequest(Verb.POST, Props.getValue("APP_URL_FOLDER_CONTINUE"));
            listDirectory.addHeader(CONTENT_TYPE_HDR, JSON_CONTENT_TYPE);

            // In this case the arguments is just an object containing the cursor that was
            // returned in the previous reply.
            listDirectory.setPayload(json.toJson(new ListFolderContinueArgs(reply.getCursor())));
            service.signRequest(accessToken, listDirectory);

            r = service.execute(listDirectory);

            if (r.getCode() != HTTP_SUCCESS)
                throw new RuntimeException(String.format("Failed to list directory: %s, Status: %d, \nReason: %s\n", directoryName, r.getCode(), r.getBody()));

            reply = json.fromJson(r.getBody(), ListFolderReturn.class);
            reply.getEntries().forEach(e -> directoryContents.add(e.toString()));
        }

        return directoryContents;
    }

}
