package net.csdn.mongo;

import com.google.inject.Injector;
import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientURI;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.LoaderClassPath;
import net.csdn.common.scan.DefaultScanService;
import net.csdn.common.scan.ScanService;
import net.csdn.common.settings.Settings;
import net.csdn.mongo.enhancer.Enhancer;
import net.csdn.mongo.enhancer.MongoEnhancer;

import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * User: WilliamZhu
 * Date: 12-10-17
 * Time: 下午3:21
 * <p/>
 * <p/>
 * The +MongoMongo+ class is the wrapper of the MongoDB driver in order to
 * get +DB+ Object.So we can use +DB+ to get collection
 * </p>
 *
 * <p>
 * Driver notes for the 5.x upgrade: {@code com.mongodb.Mongo} no longer exists;
 * this class holds a {@link MongoClient}. Connection settings are the same as
 * serviceframework-mongo:
 * <ul>
 *   <li>{@code <mode>.datasources.mongodb.uri} — full {@code mongodb://} /
 *       {@code mongodb+srv://} connection string. When set it wins over
 *       host/port/username/password/authenticationDatabase; a database named
 *       in the URI wins over {@code ...mongodb.database}.</li>
 *   <li>{@code <mode>.datasources.mongodb.host} (default 127.0.0.1),
 *       {@code .port} (27017), {@code .database} (csdn_data_center)</li>
 *   <li>{@code .username} / {@code .password} /
 *       {@code .authenticationDatabase} (or {@code .authdb}, default admin) —
 *       SCRAM-SHA-256</li>
 *   <li>{@code .replicaSet} — requiredReplicaSetName</li>
 *   <li>{@code .disable}=true — configure() records the configuration but does
 *       not connect and does not scan or define documents.</li>
 * </ul>
 * The URI is never echoed into exceptions or logs.
 */
public class MongoMongo {


    private MongoClient mongo;
    private String dbName;
    private final CSDNMongoConfiguration configuration;
    private final AtomicBoolean clientClosed = new AtomicBoolean();

    private static volatile CSDNMongoConfiguration mongoConfiguration;
    private static volatile MongoMongo current;
    private static final Object LIFECYCLE_LOCK = new Object();

    public static CSDNMongoConfiguration getMongoConfiguration() {
        return mongoConfiguration;
    }

    private static final String defaultHostName = "127.0.0.1";
    private static final int defaultHostPort = 27017;
    private static final String defaultDBName = "csdn_data_center";


    /**
     * Opens a caller-owned client. This constructor deliberately does not
     * register {@link #current} or {@link Document#mongoMongo}; close the
     * returned instance directly. It is rejected while a configure()-created
     * client is still registered, so it cannot overwrite the active global
     * configuration.
     */
    public MongoMongo(CSDNMongoConfiguration csdnMongoConfiguration) throws Exception {
        if (csdnMongoConfiguration == null) {
            throw new IllegalArgumentException("mongo configuration is required");
        }
        this.configuration = csdnMongoConfiguration;
        synchronized (LIFECYCLE_LOCK) {
            rejectConfiguredClient();
            CSDNMongoConfiguration previous = mongoConfiguration;
            mongoConfiguration = csdnMongoConfiguration;
            try {
                this.mongo = connect();
            } catch (RuntimeException e) {
                mongoConfiguration = previous;
                throw e;
            } catch (Error e) {
                mongoConfiguration = previous;
                throw e;
            }
        }
    }

    private MongoClient connect() {
        String prefix = mode() + ".datasources.mongodb.";
        String uri = settings().get(prefix + "uri");
        MongoClientOptions.Builder options = MongoClientOptions.builder()
                .serverSelectionTimeout(8000)
                .connectTimeout(8000)
                .socketTimeout(20000);
        String replicaSet = settings().get(prefix + "replicaSet", "");
        if (replicaSet != null && replicaSet.length() > 0) {
            options.requiredReplicaSetName(replicaSet);
        }
        MongoClient client;
        if (uri != null && uri.trim().length() > 0) {
            MongoClientURI mongoURI;
            try {
                mongoURI = new MongoClientURI(uri.trim(), options);
            } catch (RuntimeException e) {
                // The URI can carry credentials; do not echo it or the
                // parser message (which may embed it) into the failure.
                throw new IllegalArgumentException(
                        "invalid mongodb uri for " + prefix + "uri (" + e.getClass().getName() + ")",
                        null);
            }
            client = new MongoClient(mongoURI);
            String uriDatabase = mongoURI.getDatabase();
            this.dbName = (uriDatabase == null || uriDatabase.length() == 0)
                    ? settings().get(prefix + "database", defaultDBName)
                    : uriDatabase;
        } else {
            String host = settings().get(prefix + "host", defaultHostName);
            int port = settings().getAsInt(prefix + "port", defaultHostPort);
            String username = settings().get(prefix + "username", "");
            String password = settings().get(prefix + "password");
            String authDatabase = settings().get(prefix + "authenticationDatabase",
                    settings().get(prefix + "authdb", "admin"));
            if (username != null && username.length() > 0 && password == null) {
                throw new IllegalArgumentException(
                        prefix + "password is required when username is set");
            }
            ServerAddress address = new ServerAddress(host, port);
            if (username == null || username.length() == 0) {
                client = new MongoClient(address, options.build());
            } else {
                MongoCredential credential = MongoCredential.createScramSha256Credential(
                        username, authDatabase, password.toCharArray());
                client = new MongoClient(address, credential, options.build());
            }
            this.dbName = settings().get(prefix + "database", defaultDBName);
        }
        try {
            CommandResult ping = client.getDB(this.dbName).command(new BasicDBObject("ping", 1));
            if (!ping.ok()) {
                throw new IllegalStateException(
                        "mongo ping was not ok: " +
                                (ping.getErrorMessage() == null ? "not ok" : ping.getErrorMessage()));
            }
        } catch (RuntimeException pingFailure) {
            try {
                client.close();
            } catch (RuntimeException closeFailure) {
                pingFailure.addSuppressed(closeFailure);
            }
            throw pingFailure;
        }
        return client;
    }

    private static void rejectConfiguredClient() {
        if (current != null) {
            throw new IllegalStateException(
                    "mongo is already configured; call disconnect() before configuring another client");
        }
    }

    /**
     * Configures the one shared client and enhances documents. The standalone
     * jar supports one registered configuration at a time: any second
     * {@code configure} call, including a disabled one, is rejected before it
     * can replace the global configuration until {@link #disconnect()} runs.
     * Failures propagate; nothing stays half-connected and no client is leaked.
     * When {@code <mode>.datasources.mongodb.disable} is true the configuration
     * is remembered but no connection or scan happens.
     */
    public static void configure(CSDNMongoConfiguration csdnMongoConfiguration) {
        if (csdnMongoConfiguration == null) {
            throw new IllegalArgumentException("mongo configuration is required");
        }
        synchronized (LIFECYCLE_LOCK) {
            rejectConfiguredClient();
            CSDNMongoConfiguration previous = mongoConfiguration;
            mongoConfiguration = csdnMongoConfiguration;
            if (Boolean.TRUE.equals(csdnMongoConfiguration.settings.getAsBoolean(
                    csdnMongoConfiguration.mode + ".datasources.mongodb.disable", Boolean.FALSE))) {
                return;
            }
            MongoMongo mongoMongo = null;
            try {
                mongoMongo = new MongoMongo(csdnMongoConfiguration);
                Document.mongoMongo = mongoMongo;
                current = mongoMongo;
                mongoMongo.loadDocuments();
            } catch (Throwable e) {
                Document.mongoMongo = null;
                current = null;
                mongoConfiguration = previous;
                if (mongoMongo != null) {
                    try {
                        mongoMongo.close();
                    } catch (Throwable closeFailure) {
                        e.addSuppressed(closeFailure);
                    }
                }
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                if (e instanceof Error) {
                    throw (Error) e;
                }
                throw new IllegalStateException("mongo configure failed: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Closes the driver client at most once. Repeated calls are no-ops.
     */
    public void close() {
        if (clientClosed.compareAndSet(false, true)) {
            mongo.close();
        }
    }

    public boolean isClosed() {
        return clientClosed.get();
    }

    /**
     * Closes the registered configure() client and clears the shared
     * references. Caller-owned instances made with {@code new MongoMongo(...)}
     * are not registered here and are not closed by this method.
     */
    public static void disconnect() {
        synchronized (LIFECYCLE_LOCK) {
            MongoMongo active = current;
            if (active != null) {
                active.close();
                if (mongoConfiguration == active.configuration) {
                    mongoConfiguration = null;
                }
            }
            Document.mongoMongo = null;
            current = null;
        }
    }

    public static String mode() {
        return mongoConfiguration.mode;
    }


    public MongoClient mongo() {
        return mongo;
    }

    public String dbName() {
        return dbName;
    }

    public static Settings settings() {
        return mongoConfiguration.settings;
    }

    public static void injector(Injector injector) {
        mongoConfiguration.injector = injector;
    }

    public static class CSDNMongoConfiguration {
        private Settings settings;
        private Injector injector;
        private ClassPool classPool;

        public Class getClassLoader() {
            return classLoader;
        }

        private Class classLoader;

        public Settings getSettings() {
            return settings;
        }

        public void setSettings(Settings settings) {
            this.settings = settings;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        private String mode;

        public CSDNMongoConfiguration(String mode, Settings settings, Class classLoader) {
            this.mode = mode;
            this.classLoader = classLoader;
            this.settings = settings;
            buildClassPool();
        }

        public CSDNMongoConfiguration(String mode, Settings settings, Class classLoader, ClassPool classPool) {
            this.mode = mode;
            this.settings = settings;
            this.classLoader = classLoader;
            this.classPool = classPool;
        }

        public void buildClassPool() {
            classPool = new ClassPool();
            classPool.appendSystemPath();
            classPool.appendClassPath(new LoaderClassPath(classLoader.getClassLoader()));
        }

        public Injector getInjector() {
            return injector;
        }

        public void setInjector(Injector injector) {
            this.injector = injector;
        }

        public ClassPool getClassPool() {
            return classPool;
        }

        public void setClassPool(ClassPool classPool) {
            this.classPool = classPool;
        }
    }

    public static Injector injector() {
        return mongoConfiguration.injector;
    }

    public static ClassPool classPool() {
        return mongoConfiguration.classPool;
    }


    public DBCollection collection(String tableName) {
        return database().getCollection(tableName);
    }

    public DB database() {
        return mongo.getDB(dbName);
    }

    public static void loadDocuments() {
        try {
            new MongoDocumentLoader().load();
            new MongoValidatorLoader().load();
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new IllegalStateException("document loading failed: " + e.getMessage(), e);
        }
    }

    public static class MongoDocumentLoader {

        public void load() throws Exception {
            final Enhancer enhancer = new MongoEnhancer(settings());
            final List<CtClass> classList = new ArrayList<CtClass>();
            ScanService scanService = new DefaultScanService();
            scanService.setLoader(mongoConfiguration.classLoader);
            scanService.scanArchives(settings().get("application.document"), new ScanService.LoadClassEnhanceCallBack() {

                public Class loaded(DataInputStream classFile) {
                    try {
                        CtClass enhanced = enhancer.enhanceThisClass(classFile);
                        if (enhanced != null) {
                            classList.add(enhanced);
                        }
                    } catch (Exception e) {
                        throw new IllegalStateException("document enhancement failed: " + e.getMessage(), e);
                    }
                    return null;
                }
            });

            enhancer.enhanceThisClass2(classList);

        }
    }

    public static class MongoValidatorLoader {

        public void load() throws Exception {
            Map<String, String> defaultMaps = new HashMap<String, String>();
            defaultMaps.put("format", "net.csdn.mongo.validate.impl.Format");
            defaultMaps.put("numericality", "net.csdn.mongo.validate.impl.Numericality");
            defaultMaps.put("presence", "net.csdn.mongo.validate.impl.Presence");
            defaultMaps.put("uniqueness", "net.csdn.mongo.validate.impl.Uniqueness");
            defaultMaps.put("length", "net.csdn.mongo.validate.impl.Length");
            defaultMaps.put("associated", "net.csdn.mongo.validate.impl.Associated");

            for (Map.Entry<String, String> entry : defaultMaps.entrySet()) {
                Document.validateParses.add(Class.forName(entry.getValue()).newInstance());
            }

        }
    }


}
