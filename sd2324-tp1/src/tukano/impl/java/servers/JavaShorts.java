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
import tukano.impl.java.servers.monitoring.ServiceMonitor;
import utils.*;

import tukano.impl.kafka.lib.KafkaPublisher;
import tukano.impl.kafka.lib.KafkaSubscriber;
import tukano.impl.kafka.lib.RecordProcessor;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public class JavaShorts implements ExtendedShorts {
    public static final String BLOB_COUNT = "*";

    private static Logger Log = Logger.getLogger(JavaShorts.class.getName());

    private static final String ADMIN_TOKEN = Props.getValue("SHARED_SECRET");

    AtomicLong counter = new AtomicLong(totalShortsInDatabase());
    private static final long USER_CACHE_EXPIRATION = 3000;
    private static final long SHORTS_CACHE_EXPIRATION = 3000;
    private static final long BLOBS_USAGE_CACHE_EXPIRATION = 10000;
    private static final long BLOBS_TRANSFER_TIMEOUT = 10000;
    private static final String LIMIT_BLOBS_URL_STR = "%s/%s/%s?timestamp=%s&verifier=%s";
    private static final String state = "1 0 0";
    private static final String TOPIC_NAME = "shorts-topic";
    private static final String KAFKA_BROKER = "kafka:9092";

//    private static final KafkaPublisher kafkaPublisher = KafkaPublisher.createPublisher(KAFKA_BROKER);
//    private static final KafkaSubscriber kafkaSubscriber = KafkaSubscriber.createSubscriber(KAFKA_BROKER, List.of(TOPIC_NAME), "earliest");

    private KafkaPublisher kafkaPublisher;
    private KafkaSubscriber kafkaSubscriber;

    public JavaShorts() {

        // start thread for Kafka Subscriber
        new Thread(() -> {
            this.kafkaPublisher = KafkaPublisher.createPublisher(KAFKA_BROKER);
            this.kafkaSubscriber = KafkaSubscriber.createSubscriber(KAFKA_BROKER, List.of(TOPIC_NAME), "earliest");
            kafkaSubscriber.start(true, this::processRecord);
        }).start();

    }

    private void processRecord(ConsumerRecord<String, String> record) {
        // Deserialize the record and update the state accordingly
        String key = record.key();
        String value = record.value();
        // Handle the event based on key and value
        switch (key) {
            case "createShort":
                Log.info("KAFKA: Got request for createShort\n");
                handleCreateShortEvent(value);
                break;
            case "getShort":
                Log.info("KAFKA: Got request for getShort\n");
                handleGetShortEvent(value);
                break;
            case "deleteShort":
                Log.info("KAFKA: Got request for deleteShort\n");
                handleDeleteShortEvent(value);
                break;
            case "getShorts":
                Log.info("KAFKA: Got request for getShorts\n");
                handleGetShortsEvent(value);
                break;
            case "follow":
                Log.info("KAFKA: Got request for follow\n");
                handleFollowEvent(value);
                break;
            case "followers":
                Log.info("KAFKA: Got request for followers\n");
                handleFollowersEvent(value);
                break;
            case "like":
                Log.info("KAFKA: Got request for like\n");
                handleLikeEvent(value);
                break;
            case "likes":
                Log.info("KAFKA: Got request for likes\n");
                handlelikesEvent(value);
                break;
            case "getFeed":
                Log.info("KAFKA: Got request for getFeed\n");
                handleGetFeedEvent(value);
            default:
                Log.warning(() -> format("Unknown event key: %s", key));
        }
    }

    private Map<String, String> parseValue(String value) {
        Map<String, String> map = new HashMap<>();
        String[] pairs = value.split(" ");
        for (String pair : pairs) {
            String[] keyValue = pair.split(":");
            if (keyValue.length == 2) {
                map.put(keyValue[0], keyValue[1]);
            }
        }
        return map;
    }

    private void handleCreateShortEvent(String value) {

        Map<String, String> parsedValue = parseValue(value);
        String origin = parsedValue.get("origin");
        String userId = parsedValue.get("userId");
        String shortId = parsedValue.get("shortId");
        String blobServerURI = parsedValue.get("blobsURLs");

        Short shrt = new Short(format("%s-rep", shortId), userId, blobServerURI);
        shrt.setBlobUrl(buildBlobsURLs(shrt));

        // Update database and cache
        DB.insertOne(shrt);
        shortsCache.put(shortId, Result.ok(shrt));
    }

    private void handleGetShortEvent(String value) {

        Map<String, String> parsedValue = parseValue(value);
        String origin = parsedValue.get("origin");
        String userId = parsedValue.get("userId");

        Result<List<String>> result = getShorts(userId);

        if (result.isOK()) {
            Log.info(() -> format("Short %s retrieved successfully\n", value));
        } else {
            Log.warning(() -> format("Failed to retrieve short %s: %s\n", value, result.error()));
        }
    }

    private void handleDeleteShortEvent(String value) {
        Map<String, String> parsedValue = parseValue(value);
        String origin = parsedValue.get("origin");
        String shortId = parsedValue.get("shortId");

        var shrt = DB.getOne(value, Short.class);
        if (shrt != null) {
            Log.info("Remove short " + value + " after Kafka notification\n");
            // Remove short from database and cache
            DB.deleteOne(shrt);
            shortsCache.invalidate(value);
        } else {
            Log.warning("Short " + value + " not found in database for deletion\n");
        }
    }

    private void handleGetShortsEvent(String value) {
        Map<String, String> parsedValue = parseValue(value);
        String origin = parsedValue.get("origin");
        String userId = parsedValue.get("userId");

        Result<List<String>> result = getShorts(value);

        if (result.isOK()) {
            Log.info(() -> format("Shorts for user %s retrieved successfully\n", value));
        } else {
            Log.warning(() -> format("Failed to retrieve shorts for user %s: %s\n", value, result.error()));
        }
    }

    private void handleFollowEvent(String value) {
        Map<String, String> parsedValue = parseValue(value);
        String origin = parsedValue.get("origin");
        String userId1 = parsedValue.get("userId1");
        String userId2 = parsedValue.get("userId2");
        boolean isFollowing = Boolean.parseBoolean(parsedValue.get("isFollowing"));
        String password = parsedValue.get("pwd");

        Result<Void> result = follow(userId1, userId2, isFollowing, password);

        if (result.isOK()) {
            Log.info(() -> format("User %s followed/unfollowed %s successfully\n", userId1, userId2));
        } else {
            Log.warning(() -> format("Failed to follow/unfollow user %s to %s: %s\n", userId1, userId2, result.error()));
        }
    }

    private void handleFollowersEvent(String value) {
        String[] parts = value.split(",");
        String userId = parts[0];
        String password = parts[1];

        Result<List<String>> result = followers(userId, password);

        if (result.isOK()) {
            Log.info(() -> format("Followers for user %s retrieved successfully\n", userId));
        } else {
            Log.warning(() -> format("Failed to retrieve followers for user %s: %s\n", userId, result.error()));
        }
    }

    private void handleLikeEvent(String value) {
        String[] parts = value.split(",");
        String shortId = parts[0];
        String userId = parts[1];
        boolean isLiked = Boolean.parseBoolean(parts[2]);
        String password = parts[3];

        Result<Void> result = like(shortId, userId, isLiked, password);

        if (result.isOK()) {
            Log.info(() -> format("Short %s liked/unliked by %s successfully\n", shortId, userId));
        } else {
            Log.warning(() -> format("Failed to like/unlike short %s by %s: %s\n", shortId, userId, result.error()));
        }
    }

    private void handlelikesEvent(String value) {
        String[] parts = value.split(",");
        String shortId = parts[0];
        String password = parts[1];

        Result<List<String>> result = likes(shortId, password);

        if (result.isOK()) {
            Log.info(() -> format("Likes for short %s retrieved successfully\n", shortId));
        } else {
            Log.warning(() -> format("Failed to retrieve likes for short %s: %s\n", shortId, result.error()));
        }
    }

    private void handleGetFeedEvent(String value) {
        String[] parts = value.split(",");
        String userId = parts[0];
        String password = parts[1];

        Result<List<String>> result = getFeed(userId, password);

        if (result.isOK()) {
            Log.info(() -> format("Feed for user %s retrieved successfully\n", userId));
        } else {
            Log.warning(() -> format("Failed to retrieve feed for user %s: %s\n", userId, result.error()));
        }
    }

    protected void publishToKafka(String topic, String key, String value) {
        long offset = kafkaPublisher.publish(topic, key, value);
        Log.info("Published message to topic " + topic + " with offset " + offset);
    }

    protected void subscribeToKafka(boolean block, RecordProcessor processor) {
        kafkaSubscriber.start(block, processor);
    }

    protected void closeKafka() {
        kafkaPublisher.close();
        kafkaSubscriber.close();
    }

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

    protected static final LoadingCache<String, Result<Short>> shortsCache = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMillis(SHORTS_CACHE_EXPIRATION)).removalListener((e) -> {
            }).build(new CacheLoader<>() {
                @Override
                public Result<Short> load(String shortId) throws Exception {

                    var query = format("SELECT count(*) FROM Likes l WHERE l.shortId = '%s'", shortId);
                    var likes = DB.sql(query, Long.class);
                    return errorOrValue(getOne(shortId, Short.class), shrt -> shrt.copyWith(likes.get(0)));
                }
            });

    public static final LoadingCache<String, Map<String, Long>> blobCountCache = CacheBuilder.newBuilder()
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

                    ServiceMonitor.getInstance();
                    return candidates;
                }
            });


    @Override
    public Result<Short> createShort(String userId, String password) {
        Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

        return errorOrResult(okUser(userId, password), user -> {

            var shortId = format("%s-%d", userId, counter.incrementAndGet());
            var blobServerURI = getLeastLoadedBlobServerURI();

            var shrt = DB.insertOne(new Short(shortId, userId, blobServerURI));

            var blobURLs = buildBlobsURLs(shrt.value());
            shrt.value().setBlobUrl(blobURLs);

            // Publish event to Kafka
            String key = "createShort";
            String value = String.format("origin:%s shortId:%s userId:%s blobsURLs:%s",IP.hostName(), shortId, userId, blobURLs);
            Log.info(() -> format("About to publish to Kafka : Action = %s, Info = %s\n", key, value));
            kafkaPublisher.publish(TOPIC_NAME, key, value);

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

        shrt.value().setBlobUrl(blobURLs);

      // Publish event to Kafka
        String key = "getShort";
        String value = String.format("origin:%s shortId:%s", IP.hostName(), shortId);
        kafkaPublisher.publish(TOPIC_NAME, key, value);

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

                    String key = "deleteShort";
                    String value = String.format("origin:%s shortId:%s", IP.hostName(), shortId);
                    kafkaPublisher.publish(TOPIC_NAME, key, value);

                });
            });
        });
    }

    @Override
    public Result<List<String>> getShorts(String userId) {
        Log.info(() -> format("getShorts : userId = %s\n", userId));

        var query = format("SELECT s.shortId FROM Short s WHERE s.ownerId = '%s'", userId);

        // Publish event to Kafka
        String key = "getShorts";
        String value = String.format("origin:%s userId:%s", IP.hostName(), userId);
        kafkaPublisher.publish(TOPIC_NAME, key, value);

        return errorOrValue(okUser(userId), DB.sql(query, String.class));
    }

    @Override
    public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
        Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2, isFollowing, password));

        // Publish event to Kafka
        String key = "getShorts";
        String value = String.format("origin:%s userId1:%s userId2:%s isFollowing:%s pwd:%s", IP.hostName(), userId1, userId2, isFollowing, password);
        kafkaPublisher.publish(TOPIC_NAME, key, value);


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

    private static String getAdminToken(long timelimit, String blobUrl) {
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

    public static void removeFromBlobUrl(String blobURL) {
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
                var download = BlobsClients.get(URI.create(uri1)).download(shrt.getShortId(), String.valueOf(timeLimit), getAdminToken(timeLimit, uri1));
                byte[] bytes = null;
                if (download.isOK())
                    bytes = download.value();
                else
                    Log.info(download.error().toString());


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

