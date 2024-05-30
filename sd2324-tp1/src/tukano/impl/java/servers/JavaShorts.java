package tukano.impl.java.servers;

import static java.lang.String.format;
import static tukano.api.java.Result.error;
import static tukano.api.java.Result.errorOrResult;
import static tukano.api.java.Result.errorOrValue;
import static tukano.api.java.Result.errorOrVoid;
import static tukano.api.java.Result.ok;
import static tukano.api.java.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.java.Result.ErrorCode.FORBIDDEN;
import static tukano.api.java.Result.ErrorCode.INTERNAL_ERROR;
import static tukano.api.java.Result.ErrorCode.TIMEOUT;
import static tukano.impl.java.clients.Clients.BlobsClients;
import static tukano.impl.java.clients.Clients.UsersClients;
import static utils.DB.getOne;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import tukano.api.Short;
import tukano.api.User;
import tukano.api.java.Blobs;
import tukano.api.java.Result;
import tukano.impl.api.java.ExtendedBlobs;
import tukano.impl.api.java.ExtendedShorts;
import tukano.impl.discovery.Discovery;
import tukano.impl.java.servers.data.Following;
import tukano.impl.java.servers.data.Likes;
import utils.*;

public class JavaShorts implements ExtendedShorts {
    private static final String BLOB_COUNT = "*";

    private static Logger Log = Logger.getLogger(JavaShorts.class.getName());

    private static final String ADMIN_TOKEN = Props.getValue("SHARED_SECRET");

    AtomicLong counter = new AtomicLong(totalShortsInDatabase());

    ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private boolean startUpdatingRepBlobs = true;
    private static final long USER_CACHE_EXPIRATION = 3000;
    private static final long SHORTS_CACHE_EXPIRATION = 3000;
    private static final long BLOBS_USAGE_CACHE_EXPIRATION = 10000;
    private static final long BLOBS_TRANSFER_TIMEOUT = 10000;
    private static final String BLOBS_URL_STR = "%s/%s/%s";
    private static final String LIMIT_BLOBS_URL_STR = "%s/%s/%s?timestamp=%s&verifier=%s";


    static record Credentials(String userId, String pwd) {
        static Credentials from(String userId, String pwd) {
            return new Credentials(userId, pwd);
        }
    }

    protected final LoadingCache<Credentials, Result<User>> usersCache = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMillis(USER_CACHE_EXPIRATION)).removalListener((e) -> {
            }).build(new CacheLoader<>() {
                @Override
                public Result<User> load(Credentials u) throws Exception {
                    var res = UsersClients.get().getUser(u.userId(), u.pwd());
                    if (res.error() == TIMEOUT)
                        return error(BAD_REQUEST);
                    return res;
                }
            });

    protected final LoadingCache<String, Result<Short>> shortsCache = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMillis(SHORTS_CACHE_EXPIRATION)).removalListener((e) -> {
            }).build(new CacheLoader<>() {
                @Override
                public Result<Short> load(String shortId) throws Exception {

                    var query = format("SELECT count(*) FROM Likes l WHERE l.shortId = '%s'", shortId);
                    var likes = DB.sql(query, Long.class);
                    return errorOrValue(getOne(shortId, Short.class), shrt -> shrt.copyWith(likes.get(0)));
                }
            });

    protected final LoadingCache<String, Map<String, Long>> blobCountCache = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMillis(BLOBS_USAGE_CACHE_EXPIRATION)).removalListener((e) -> {
            }).build(new CacheLoader<>() {
                @Override
                public Map<String, Long> load(String __) throws Exception {

                    final var QUERY = "SELECT REGEXP_SUBSTRING(s.blobUrl, '^(\\w+:\\/\\/)?([^\\/]+)\\/([^\\/]+).*') AS baseURI, count('*') AS usage From Short s GROUP BY baseURI";
                    var hits = DB.sql(QUERY, BlobServerCount.class);

                    var candidates = hits.stream()
                            .collect(Collectors.toMap(BlobServerCount::baseURI, BlobServerCount::count));

                    for (var uri : BlobsClients.all())
                        candidates.putIfAbsent(uri.toString(), 0L);

                    updateBlobsRep();
                    return candidates;
                }
            });


    @Override
    public Result<Short> createShort(String userId, String password) {
        Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

        return errorOrResult(okUser(userId, password), user -> {

            var shortId = format("%s-%d", userId, counter.incrementAndGet());
            Log.info(() -> format("Generated shortId = %s\n", shortId));

            var blobServerURI = getLeastLoadedBlobServerURI();
            Log.info(() -> format("Selected blobServerURI = %s\n", blobServerURI));

            var shrt = DB.insertOne(new Short(shortId, userId, blobServerURI));

            var blobURLs = buildBlobsURLs(shrt.value());
            shrt.value().setBlobUrl(blobURLs);
            Log.info(() -> format("createShort : short = %s\n", shrt.value().toString()));

            return ok(shrt.value());
        });
    }

    @Override
    public Result<Short> getShort(String shortId) {
        Log.info(() -> format("getShort : shortId = %s\n", shortId));

        if (shortId == null)
            return error(BAD_REQUEST);

        var shrt = shortFromCache(shortId);

        if (!shrt.isOK())
            return shrt;

        var blobURLs = buildBlobsURLs(shrt.value());
        Log.info(() -> format("createShort : blobUrl = %s\n", blobURLs));

        shrt.value().setBlobUrl(blobURLs);

        return ok(shrt.value());
    }

    @Override
    public Result<Void> deleteShort(String shortId, String password) {
        Log.info(() -> format("deleteShort : shortId = %s, pwd = %s\n", shortId, password));

        return errorOrResult(getShort(shortId), shrt -> {
            return errorOrResult(okUser(shrt.getOwnerId(), password), user -> {
                return DB.transaction(hibernate -> {
                    shortsCache.invalidate(shortId);
                    hibernate.remove(shrt);

                    var query = format("SELECT * FROM Likes l WHERE l.shortId = '%s'", shortId);
                    hibernate.createNativeQuery(query, Likes.class).list().forEach(hibernate::remove);

                    String[] blobUrls = shrt.getBlobUrl().split("\\|");

                    for (String url : blobUrls)
                        BlobsClients.get().delete(url, Token.get());

                    //BlobsClients.get().delete(shrt.getBlobUrl(), Token.get() );
                });
            });
        });
    }

    @Override
    public Result<List<String>> getShorts(String userId) {
        Log.info(() -> format("getShorts : userId = %s\n", userId));

        var query = format("SELECT s.shortId FROM Short s WHERE s.ownerId = '%s'", userId);
        return errorOrValue(okUser(userId), DB.sql(query, String.class));
    }

    @Override
    public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
        Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2, isFollowing, password));


        return errorOrResult(okUser(userId1, password), user -> {
            var f = new Following(userId1, userId2);
            return errorOrVoid(okUser(userId2), isFollowing ? DB.insertOne(f) : DB.deleteOne(f));
        });
    }

    @Override
    public Result<List<String>> followers(String userId, String password) {
        Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));

        var query = format("SELECT f.follower FROM Following f WHERE f.followee = '%s'", userId);
        return errorOrValue(okUser(userId, password), DB.sql(query, String.class));
    }

    @Override
    public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
        Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked, password));


        return errorOrResult(getShort(shortId), shrt -> {
            shortsCache.invalidate(shortId);

            var l = new Likes(userId, shortId, shrt.getOwnerId());
            return errorOrVoid(okUser(userId, password), isLiked ? DB.insertOne(l) : DB.deleteOne(l));
        });
    }

    @Override
    public Result<List<String>> likes(String shortId, String password) {
        Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

        return errorOrResult(getShort(shortId), shrt -> {

            var query = format("SELECT l.userId FROM Likes l WHERE l.shortId = '%s'", shortId);

            return errorOrValue(okUser(shrt.getOwnerId(), password), DB.sql(query, String.class));
        });
    }

    @Override
    public Result<List<String>> getFeed(String userId, String password) {
        Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

        final var QUERY_FMT = """
                SELECT s.shortId, s.timestamp FROM Short s WHERE	s.ownerId = '%s'				
                UNION			
                SELECT s.shortId, s.timestamp FROM Short s, Following f 
                	WHERE 
                		f.followee = s.ownerId AND f.follower = '%s' 
                ORDER BY s.timestamp DESC""";

        return errorOrValue(okUser(userId, password), DB.sql(format(QUERY_FMT, userId, userId), String.class));
    }

    protected Result<User> okUser(String userId, String pwd) {
        try {
            return usersCache.get(new Credentials(userId, pwd));
        } catch (Exception x) {
            x.printStackTrace();
            return Result.error(INTERNAL_ERROR);
        }
    }

    private Result<Void> okUser(String userId) {
        var res = okUser(userId, "");
        if (res.error() == FORBIDDEN)
            return ok();
        else
            return error(res.error());
    }

    protected Result<Short> shortFromCache(String shortId) {
        try {
            return shortsCache.get(shortId);
        } catch (ExecutionException e) {
            e.printStackTrace();
            return error(INTERNAL_ERROR);
        }
    }

    // Extended API

    @Override
    public Result<Void> deleteAllShorts(String userId, String password, String token) {
        Log.info(() -> format("deleteAllShorts : userId = %s, password = %s, token = %s\n", userId, password, token));

        if (!Token.matches(token))
            return error(FORBIDDEN);

        return DB.transaction((hibernate) -> {

            usersCache.invalidate(new Credentials(userId, password));

            //delete shorts
            var query1 = format("SELECT * FROM Short s WHERE s.ownerId = '%s'", userId);
            hibernate.createNativeQuery(query1, Short.class).list().forEach(s -> {
                shortsCache.invalidate(s.getShortId());
                hibernate.remove(s);
            });

            //delete follows
            var query2 = format("SELECT * FROM Following f WHERE f.follower = '%s' OR f.followee = '%s'", userId, userId);
            hibernate.createNativeQuery(query2, Following.class).list().forEach(hibernate::remove);

            //delete likes
            var query3 = format("SELECT * FROM Likes l WHERE l.ownerId = '%s' OR l.userId = '%s'", userId, userId);
            hibernate.createNativeQuery(query3, Likes.class).list().forEach(l -> {
                shortsCache.invalidate(l.getShortId());
                hibernate.remove(l);
            });
        });
    }


    private String getLeastLoadedBlobServerURI() {
        try {
            Log.info(() -> format("Selected least loaded server\n"));
            var servers = blobCountCache.get(BLOB_COUNT);
            Log.info(() -> format("servers = %s BLOB_COUNT = %s\n", servers.entrySet(), servers.values()));
            var leastLoadedServer = servers.entrySet()
                    .stream()
                    .sorted((e1, e2) -> Long.compare(e1.getValue(), e2.getValue()))
                    .findFirst();
            Log.info(() -> format("leastLoadedServer.isPresent() = %b\n", leastLoadedServer.isPresent()));
            if (leastLoadedServer.isPresent()) {
                var uri = leastLoadedServer.get().getKey();
                servers.compute(uri, (k, v) -> v + 1L);
                Log.info(() -> format("Selected least loaded server URI = %s\n", uri));
                return uri;
            }
        } catch (Exception x) {
            x.printStackTrace();
        }
        return "?";
    }

    static record BlobServerCount(String baseURI, Long count) {
    }

    private long totalShortsInDatabase() {
        var hits = DB.sql("SELECT count('*') FROM Short", Long.class);
        return 1L + (hits.isEmpty() ? 0L : hits.get(0));
    }

    private String getAdminToken(long timelimit, String blobUrl) {
        String ip = blobUrl.substring(blobUrl.indexOf("://") + 3, blobUrl.lastIndexOf(":"));
        return Hash.sha256(ip, String.valueOf(timelimit), ADMIN_TOKEN);
    }

    private String buildBlobsURLs(Short shrt) {

        var uris = Discovery.getInstance().knownUrisOf(Blobs.NAME, 1);

        var timeLimit = System.currentTimeMillis() + BLOBS_TRANSFER_TIMEOUT;
        var blobURLs = new StringBuilder(format(LIMIT_BLOBS_URL_STR, uris[0], Blobs.NAME, shrt.getShortId(),
                timeLimit, getAdminToken(timeLimit, uris[0].toString())));

        for (var uri : uris) {
            if (uri != null && !uri.toString().equals(uris[0].toString())) {
                Log.info(() -> format("buildBlobsURLs : uri = %s\n", uri));
                blobURLs.append(format("|" + LIMIT_BLOBS_URL_STR, uri, Blobs.NAME, shrt.getShortId(), timeLimit,
                        getAdminToken(timeLimit, uri.toString())));
            }
        }
        return blobURLs.toString();
    }

    private void updateBlobsRep() {
        if (startUpdatingRepBlobs) {

            executorService.scheduleAtFixedRate(() -> {

                try {
                    var servers = blobCountCache.get(BLOB_COUNT);

                    Set<String> uris = new HashSet<>();
                    for (ExtendedBlobs blob : BlobsClients.all()) {
                        uris.add(blob.toString());
                    }

                    for (var blobserver : servers.keySet()) {
                        if (!uris.contains(blobserver)) {
                            Log.info(() -> format("Removing blob server %s from the list of candidates\n\n", blobserver));
                            removeFromBlobUrl(blobserver);
                            blobCountCache.invalidate(blobserver);
                        }
                    }
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }, 0, 5, TimeUnit.SECONDS);

            startUpdatingRepBlobs = false;
        }
    }

    private void removeFromBlobUrl(String blobURL) {
        DB.transaction(hibernate -> {

            var query = format("SELECT * FROM Short s WHERE s.blobUrl LIKE '%%%s%%'", blobURL);
            List<Short> shorts = hibernate.createNativeQuery(query, Short.class).list();
            for (Short shrt : shorts) {

                String updatedBlobUrl = Stream.of(shrt.getBlobUrl().split("\\|"))
                        .filter(url -> !url.contains(blobURL))
                        .collect(Collectors.joining("|"));


                var uris = BlobsClients.all();
                Set<String> uris2 = new java.util.HashSet<String>();
                for (ExtendedBlobs u : uris) {
                    var uri = u.toString();
                    if (!uri.equals(blobURL) && !updatedBlobUrl.contains(uri))
                        uris2.add(uri);
                }


                var random = new Random();
                var uri2 = uris2.toArray(new String[uris2.size()])[random.nextInt(uris2.size())];


                String regex = String.format("(.+?)/%s/(.+)", Blobs.NAME);
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(updatedBlobUrl);
                String uri1 = "";
                if (matcher.matches()) {
                    uri1 = matcher.group(1);
                }


                var timeLimit = System.currentTimeMillis() + BLOBS_TRANSFER_TIMEOUT;
                var downl = BlobsClients.get(URI.create(uri1)).download(shrt.getShortId(), String.valueOf(timeLimit), getAdminToken(timeLimit, uri1));
                byte[] bytes = null;
                if (downl.isOK())
                    bytes = downl.value();
                else
                    Log.info(downl.error().toString());


                var res = BlobsClients.get(URI.create(uri2)).upload(shrt.getShortId(), String.valueOf(timeLimit), getAdminToken(timeLimit, uri2), bytes);
                if (!res.isOK())
                    Log.info(res.error().toString());


                var shrtTimeLimit = shrt.getBlobUrl().substring(shrt.getBlobUrl().indexOf("timestamp=") + 10, shrt.getBlobUrl().indexOf("&"));
                updatedBlobUrl += "|" + uri2 + "/" + Blobs.NAME + "/" + shrt.getShortId() + "?timestamp=" + shrtTimeLimit + "&verifier=" + getAdminToken(Long.parseLong(shrtTimeLimit), uri2);

                shrt.setBlobUrl(updatedBlobUrl);

                DB.updateOne(shrt);

                shortsCache.invalidate(shrt.getShortId());
            }
        });
    }

}

