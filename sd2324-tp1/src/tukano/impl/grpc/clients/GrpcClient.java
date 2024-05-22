package tukano.impl.grpc.clients;

import static tukano.api.java.Result.error;
import static tukano.api.java.Result.ok;
import static tukano.api.java.Result.ErrorCode.INTERNAL_ERROR;
import static tukano.api.java.Result.ErrorCode.TIMEOUT;

import java.net.URI;
import java.util.function.Supplier;

import io.grpc.Channel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import tukano.api.java.Result;
import tukano.api.java.Result.ErrorCode;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.io.FileInputStream;
import java.security.KeyStore;

import javax.net.ssl.TrustManagerFactory;

import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContextBuilder;

public class GrpcClient {

    final protected URI serverURI;
    final protected Channel channel;

    protected GrpcClient(String serverUrl) throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        this.serverURI = URI.create(serverUrl);

        var trustStore = System.getProperty("javax.net.ssl.trustStore");
        var trustStorePassword = System.getProperty("javax.net.ssl.trustStorePassword");

        var keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        var in = new FileInputStream(trustStore);
        keystore.load(in, trustStorePassword.toCharArray());

        var trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keystore);

        var scb = SslContextBuilder.forClient().trustManager(trustManagerFactory);
        var sslContext = GrpcSslContexts.configure(scb).build();

        this.channel = NettyChannelBuilder.forAddress(serverURI.getHost(), serverURI.getPort()).sslContext(sslContext).build();
    }

    protected <T> Result<T> toJavaResult(Supplier<T> func) {
        try {
            return ok(func.get());
        } catch (StatusRuntimeException sre) {
            return error(statusToErrorCode(sre.getStatus()));
        } catch (Exception x) {
            x.printStackTrace();
            return Result.error(INTERNAL_ERROR);
        }
    }

    protected Result<Void> toJavaResult(Runnable proc) {
        return toJavaResult(() -> {
            proc.run();
            return null;
        });
    }

    protected static ErrorCode statusToErrorCode(Status status) {
        return switch (status.getCode()) {
            case OK -> ErrorCode.OK;
            case NOT_FOUND -> ErrorCode.NOT_FOUND;
            case ALREADY_EXISTS -> ErrorCode.CONFLICT;
            case PERMISSION_DENIED -> ErrorCode.FORBIDDEN;
            case INVALID_ARGUMENT -> ErrorCode.BAD_REQUEST;
            case UNIMPLEMENTED -> ErrorCode.NOT_IMPLEMENTED;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }

    @Override
    public String toString() {
        return serverURI.toString();
    }
}

