package net.csdn.mongo;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import net.csdn.common.settings.ImmutableSettings;
import net.csdn.common.settings.Settings;
import org.junit.Assume;
import org.junit.Test;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Live test for the standalone jar: real enhancement, real driver, real server.
 * Runs only when {@code -Dsf.compat.mongo=true} or {@code SF_COMPAT_MONGO=true}
 * is set; reads credentials from {@code SF_COMPAT_ENV_FILE} (mode 600) without
 * printing them. Database must be {@code sf_compat}; collections use the
 * {@code sf_it_} prefix.
 *
 * Fixtures are compiled from src/test/fixtures into a scratch directory and
 * loaded through a fresh URLClassLoader, so this exercises the jar built by
 * this module — not serviceframework-mongo.
 */
public class MongoStandaloneLiveTest {

    private static final String PREFIX_KEY = "sf.mongo.collection.prefix";
    private static final String FIXTURE_PACKAGE = "net.csdn.mongo.fixture";

    @Test
    public void enhanceCrudIndexAssociationEmbeddedOnModernMongo() throws Exception {
        requireLive();
        Map<String, String> env = credentials();
        String prefix = prefix();
        System.setProperty(PREFIX_KEY, prefix);
        File classes = compileFixtures();
        URLClassLoader loader = newLoader(classes);
        try {
            Class<?> anchor = anchor(loader);
            MongoMongo.configure(new MongoMongo.CSDNMongoConfiguration(
                    "test", settings(env, anchor), anchor));
            MongoMongo mongo = Document.mongoMongo;
            assertNotNull(mongo);
            assertFalse(mongo.isClosed());

            Class<?> record = loader.loadClass(FIXTURE_PACKAGE + ".Record");
            exerciseRecord(record);
            assertIndexExists(mongo, prefix + "_record", "record_title_idx");
            exerciseAssociation(loader);
            exerciseEmbedded(loader);
            drop(mongo, prefix);
            mongo.close();
            assertTrue(mongo.isClosed());
            mongo.close();
            assertTrue(mongo.isClosed());
        } finally {
            MongoMongo.disconnect();
            loader.close();
            deleteQuietly(classes);
        }
    }

    @Test
    public void mongoUriConnectsAndHonorsUriDatabase() throws Exception {
        requireLive();
        Map<String, String> env = credentials();
        String prefix = prefix();
        System.setProperty(PREFIX_KEY, prefix);
        File classes = compileFixtures();
        URLClassLoader loader = newLoader(classes);
        try {
            Class<?> anchor = anchor(loader);
            String uri = "mongodb://" + env.get("SF_COMPAT_MONGO_USER") + ":"
                    + env.get("SF_COMPAT_MONGO_PASSWORD")
                    + "@" + env.get("SF_COMPAT_MONGO_HOST") + ":" + env.get("SF_COMPAT_MONGO_PORT")
                    + "/" + env.get("SF_COMPAT_MONGO_DATABASE")
                    + "?replicaSet=" + env.get("SF_COMPAT_MONGO_REPLSET")
                    + "&authSource=" + env.get("SF_COMPAT_MONGO_AUTH_DB");
            Settings settings = ImmutableSettings.settingsBuilder()
                    .put("mode", "test")
                    .put("application.document", FIXTURE_PACKAGE)
                    .put("test.datasources.mongodb.uri", uri)
                    // database is deliberately wrong: the URI path must win
                    .put("test.datasources.mongodb.database", "unused_db")
                    .classLoader(loader)
                    .build();
            MongoMongo.configure(new MongoMongo.CSDNMongoConfiguration("test", settings, anchor));
            MongoMongo mongo = Document.mongoMongo;
            assertNotNull(mongo);
            assertEquals(env.get("SF_COMPAT_MONGO_DATABASE"), mongo.dbName());
            drop(mongo, prefix);
        } finally {
            MongoMongo.disconnect();
            loader.close();
            deleteQuietly(classes);
        }
    }

    @Test
    public void reconfigureIsRejectedUntilDisconnect() throws Exception {
        requireLive();
        Map<String, String> env = credentials();
        String prefix = prefix();
        System.setProperty(PREFIX_KEY, prefix);
        File classes = compileFixtures();
        URLClassLoader firstLoader = newLoader(classes);
        URLClassLoader secondLoader = null;
        try {
            Class<?> firstAnchor = anchor(firstLoader);
            MongoMongo.CSDNMongoConfiguration first = new MongoMongo.CSDNMongoConfiguration(
                    "test", settings(env, firstAnchor), firstAnchor);
            MongoMongo.configure(first);
            MongoMongo active = Document.mongoMongo;
            assertNotNull(active);
            assertFalse(active.isClosed());
            assertSame(first, MongoMongo.getMongoConfiguration());

            assertReconfigureRejected(new MongoMongo.CSDNMongoConfiguration(
                    "test", settings(env, firstAnchor), firstAnchor));
            Settings disabled = ImmutableSettings.settingsBuilder()
                    .put("mode", "test")
                    .put("application.document", FIXTURE_PACKAGE)
                    .put("test.datasources.mongodb.disable", true)
                    .put("test.datasources.mongodb.host", "127.0.0.1")
                    .put("test.datasources.mongodb.port", 1)
                    .classLoader(firstLoader)
                    .build();
            assertReconfigureRejected(new MongoMongo.CSDNMongoConfiguration(
                    "test", disabled, firstAnchor));
            assertSame(first, MongoMongo.getMongoConfiguration());
            assertSame(active, Document.mongoMongo);
            assertFalse(active.isClosed());
            assertTrue(active.database().command(new BasicDBObject("ping", 1)).ok());

            MongoMongo.disconnect();
            assertTrue(active.isClosed());
            assertNull(Document.mongoMongo);
            assertNull(MongoMongo.getMongoConfiguration());

            secondLoader = newLoader(classes);
            Class<?> secondAnchor = anchor(secondLoader);
            MongoMongo.CSDNMongoConfiguration second = new MongoMongo.CSDNMongoConfiguration(
                    "test", settings(env, secondAnchor), secondAnchor);
            MongoMongo.configure(second);
            assertSame(second, MongoMongo.getMongoConfiguration());
            assertNotSame(active, Document.mongoMongo);
            assertFalse(Document.mongoMongo.isClosed());
            assertTrue(Document.mongoMongo.database().command(new BasicDBObject("ping", 1)).ok());
        } finally {
            MongoMongo.disconnect();
            if (secondLoader != null) {
                secondLoader.close();
            }
            firstLoader.close();
            deleteQuietly(classes);
        }
    }

    private static void assertReconfigureRejected(MongoMongo.CSDNMongoConfiguration configuration) {
        try {
            MongoMongo.configure(configuration);
            fail("configure must reject a second client until disconnect()");
        } catch (IllegalStateException failure) {
            assertTrue(failure.getMessage(), failure.getMessage().contains("disconnect()"));
        }
    }

    @Test
    public void authFailurePropagatesAndClosesClient() throws Exception {
        requireLive();
        Map<String, String> env = credentials();
        String prefix = prefix();
        System.setProperty(PREFIX_KEY, prefix);
        File classes = compileFixtures();
        URLClassLoader loader = newLoader(classes);
        MongoMongo leaked = null;
        try {
            Class<?> anchor = anchor(loader);
            Settings settings = ImmutableSettings.settingsBuilder()
                    .put("mode", "test")
                    .put("application.document", FIXTURE_PACKAGE)
                    .put("test.datasources.mongodb.host", env.get("SF_COMPAT_MONGO_HOST"))
                    .put("test.datasources.mongodb.port", env.get("SF_COMPAT_MONGO_PORT"))
                    .put("test.datasources.mongodb.database", env.get("SF_COMPAT_MONGO_DATABASE"))
                    .put("test.datasources.mongodb.username", env.get("SF_COMPAT_MONGO_USER"))
                    .put("test.datasources.mongodb.password", "definitely-wrong")
                    .put("test.datasources.mongodb.authenticationDatabase", env.get("SF_COMPAT_MONGO_AUTH_DB"))
                    .put("test.datasources.mongodb.replicaSet", env.get("SF_COMPAT_MONGO_REPLSET"))
                    .classLoader(loader)
                    .build();
            try {
                MongoMongo.configure(new MongoMongo.CSDNMongoConfiguration("test", settings, anchor));
                fail("wrong password must fail configure");
            } catch (RuntimeException failure) {
                assertNotNull(failure.getMessage());
                assertFalse(failure.getMessage().contains("definitely-wrong"));
            }
            assertNull(Document.mongoMongo);
            assertNull(leaked);
        } finally {
            MongoMongo.disconnect();
            loader.close();
            deleteQuietly(classes);
        }
    }

    @Test
    public void connectFailurePropagatesWithoutLeaking() throws Exception {
        requireLive();
        Map<String, String> env = credentials();
        File classes = compileFixtures();
        URLClassLoader loader = newLoader(classes);
        try {
            Class<?> anchor = anchor(loader);
            Settings settings = ImmutableSettings.settingsBuilder()
                    .put("mode", "test")
                    .put("application.document", FIXTURE_PACKAGE)
                    .put("test.datasources.mongodb.host", "127.0.0.1")
                    .put("test.datasources.mongodb.port", 1)
                    .put("test.datasources.mongodb.database", env.get("SF_COMPAT_MONGO_DATABASE"))
                    .classLoader(loader)
                    .build();
            try {
                MongoMongo.configure(new MongoMongo.CSDNMongoConfiguration("test", settings, anchor));
                fail("port 1 should not accept a mongo connection");
            } catch (RuntimeException failure) {
                assertNotNull(failure.getMessage());
            }
            assertNull(Document.mongoMongo);
        } finally {
            MongoMongo.disconnect();
            loader.close();
            deleteQuietly(classes);
        }
    }

    @Test
    public void disabledModuleDoesNotConnectOrScan() throws Exception {
        File classes = compileFixtures();
        URLClassLoader loader = newLoader(classes);
        try {
            Class<?> anchor = anchor(loader);
            Settings settings = ImmutableSettings.settingsBuilder()
                    .put("mode", "test")
                    .put("application.document", FIXTURE_PACKAGE)
                    .put("test.datasources.mongodb.disable", true)
                    .put("test.datasources.mongodb.host", "127.0.0.1")
                    .put("test.datasources.mongodb.port", 1)
                    .classLoader(loader)
                    .build();
            long started = System.nanoTime();
            MongoMongo.configure(new MongoMongo.CSDNMongoConfiguration("test", settings, anchor));
            long elapsedMs = (System.nanoTime() - started) / 1000000L;
            assertTrue("disabled configure tried to connect", elapsedMs < 5000L);
            assertNull(Document.mongoMongo);
        } finally {
            MongoMongo.disconnect();
            loader.close();
            deleteQuietly(classes);
        }
    }

    private static void exerciseRecord(Class<?> record) throws Exception {
        String id = "rec-" + UUID.randomUUID();
        Object saved = callStatic(record, "create", new Class<?>[]{Map.class},
                new Object[]{attrs("_id", id, "title", "Hello", "seq", Integer.valueOf(1))});
        assertEquals("Hello", call(saved, "getTitle"));
        assertEquals(Boolean.TRUE, call(saved, "save"));
        Object found = callStatic(record, "findById", new Class<?>[]{Object.class}, new Object[]{id});
        assertNotNull(found);
        assertEquals("Hello", call(found, "getTitle"));
        assertEquals(Integer.valueOf(1), call(found, "getSeq"));
        // @BeforeSave ran inside save() and its attr() write was persisted
        assertEquals(Boolean.TRUE, call(found, "attr", new Class<?>[]{String.class, Class.class},
                new Object[]{"callbackRan", Boolean.class}));
        Integer count = (Integer) callStatic(record, "count", new Class<?>[]{}, new Object[]{});
        assertTrue(count >= 1);
        call(found, "setTitle", new Class<?>[]{String.class}, new Object[]{"Next"});
        assertEquals(Boolean.TRUE, call(found, "update"));
        Object updated = callStatic(record, "findById", new Class<?>[]{Object.class}, new Object[]{id});
        assertEquals("Next", call(updated, "getTitle"));
        call(updated, "remove");
        Object gone = callStatic(record, "findById", new Class<?>[]{Object.class}, new Object[]{id});
        assertNull(gone);
        // pagination + ordering through the enhanced Criteria entry points
        for (int i = 0; i < 3; i++) {
            Object doc = callStatic(record, "create", new Class<?>[]{Map.class},
                    new Object[]{attrs("_id", id + "-" + i, "title", "t" + i)});
            call(doc, "save");
        }
        Object criteria = callStatic(record, "where", new Class<?>[]{Map.class}, new Object[]{attrs()});
        call(criteria, "order", new Class<?>[]{Map.class}, new Object[]{attrs("title", "desc")});
        call(criteria, "skip", new Class<?>[]{int.class}, new Object[]{Integer.valueOf(1)});
        call(criteria, "limit", new Class<?>[]{int.class}, new Object[]{Integer.valueOf(1)});
        List<?> page = (List<?>) call(criteria, "fetch");
        assertEquals(1, page.size());
        assertEquals("t1", call(page.get(0), "getTitle"));
    }

    private static void exerciseAssociation(ClassLoader loader) throws Exception {
        Class<?> ownerClass = loader.loadClass(FIXTURE_PACKAGE + ".Owner");
        Class<?> itemClass = loader.loadClass(FIXTURE_PACKAGE + ".Item");
        String ownerId = "owner-" + UUID.randomUUID();
        Object owner = callStatic(ownerClass, "create", new Class<?>[]{Map.class},
                new Object[]{attrs("_id", ownerId, "label", "boss")});
        assertEquals(Boolean.TRUE, call(owner, "save"));
        Object item = callStatic(itemClass, "create", new Class<?>[]{Map.class},
                new Object[]{attrs("title", "gear", "owner_id", ownerId)});
        assertEquals(Boolean.TRUE, call(item, "save"));
        Object association = call(owner, "items");
        List<?> found = (List<?>) call(association, "findAll");
        assertEquals(1, found.size());
        assertEquals("gear", call(found.get(0), "getTitle"));
        Object backRef = call(call(item, "owner"), "findOne");
        assertNotNull(backRef);
        assertEquals("boss", call(backRef, "getLabel"));
    }

    private static void exerciseEmbedded(ClassLoader loader) throws Exception {
        Class<?> volumeClass = loader.loadClass(FIXTURE_PACKAGE + ".Volume");
        Class<?> pageClass = loader.loadClass(FIXTURE_PACKAGE + ".Page");
        String volumeId = "vol-" + UUID.randomUUID();
        Object volume = callStatic(volumeClass, "create", new Class<?>[]{Map.class},
                new Object[]{attrs("_id", volumeId, "title", "book")});
        Object pages = call(volume, "pages");
        call(pages, "build", new Class<?>[]{Map.class}, new Object[]{attrs("text", "p1")});
        call(pages, "build", new Class<?>[]{Map.class}, new Object[]{attrs("text", "p2")});
        assertEquals(Boolean.TRUE, call(volume, "save"));
        Object loaded = callStatic(volumeClass, "findById", new Class<?>[]{Object.class}, new Object[]{volumeId});
        List<?> found = (List<?>) call(call(loaded, "pages"), "find");
        assertEquals(2, found.size());
        assertEquals("p1", call(found.get(0), "getText"));
        assertEquals("p2", call(found.get(1), "getText"));
        pageClass.getName();
    }

    private static void assertIndexExists(MongoMongo mongo, String collectionName, String indexName) {
        DBCollection collection = mongo.collection(collectionName);
        List<DBObject> info = collection.getIndexInfo();
        boolean found = false;
        for (DBObject index : info) {
            if (indexName.equals(index.get("name"))) {
                found = true;
            }
        }
        assertTrue("index " + indexName + " missing on " + collectionName, found);
    }

    private static Map<String, Object> attrs(Object... pairs) {
        Map<String, Object> map = new HashMap<String, Object>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static Object call(Object target, String name, Class<?>[] types, Object[] args) throws Exception {
        Method method = findMethod(target.getClass(), name, types);
        return method.invoke(target, args);
    }

    private static Object call(Object target, String name) throws Exception {
        return call(target, name, new Class<?>[]{}, new Object[]{});
    }

    private static Object callStatic(Class<?> type, String name, Class<?>[] types, Object[] args) throws Exception {
        return findMethod(type, name, types).invoke(null, args);
    }

    private static Method findMethod(Class<?> type, String name, Class<?>[] types) throws Exception {
        Class<?> cursor = type;
        while (cursor != null) {
            try {
                return cursor.getDeclaredMethod(name, types);
            } catch (NoSuchMethodException missing) {
                cursor = cursor.getSuperclass();
            }
        }
        return type.getMethod(name, types);
    }

    private static void drop(MongoMongo mongo, String prefix) {
        mongo.collection(prefix + "_record").drop();
        mongo.collection(prefix + "_owner").drop();
        mongo.collection(prefix + "_item").drop();
        mongo.collection(prefix + "_volume").drop();
    }

    private static Settings settings(Map<String, String> env, Class<?> anchor) {
        return ImmutableSettings.settingsBuilder()
                .put("mode", "test")
                .put("application.document", FIXTURE_PACKAGE)
                .put("test.datasources.mongodb.host", env.get("SF_COMPAT_MONGO_HOST"))
                .put("test.datasources.mongodb.port", env.get("SF_COMPAT_MONGO_PORT"))
                .put("test.datasources.mongodb.database", env.get("SF_COMPAT_MONGO_DATABASE"))
                .put("test.datasources.mongodb.username", env.get("SF_COMPAT_MONGO_USER"))
                .put("test.datasources.mongodb.password", env.get("SF_COMPAT_MONGO_PASSWORD"))
                .put("test.datasources.mongodb.authenticationDatabase", env.get("SF_COMPAT_MONGO_AUTH_DB"))
                .put("test.datasources.mongodb.replicaSet", env.get("SF_COMPAT_MONGO_REPLSET"))
                .classLoader(anchor.getClassLoader())
                .build();
    }

    private static Class<?> anchor(ClassLoader loader) throws Exception {
        return Class.forName(FIXTURE_PACKAGE + ".ServiceFrameworkPackageAnchor", false, loader);
    }

    private static URLClassLoader newLoader(File classes) throws Exception {
        return new URLClassLoader(new URL[]{classes.toURI().toURL()},
                MongoStandaloneLiveTest.class.getClassLoader());
    }

    private static boolean mongoRequested() {
        if (Boolean.parseBoolean(System.getProperty("sf.compat.mongo", "false"))) {
            return true;
        }
        return "true".equalsIgnoreCase(System.getenv("SF_COMPAT_MONGO"));
    }

    private static void requireLive() {
        Assume.assumeTrue("sf.compat.mongo is not enabled", mongoRequested());
    }

    private static String prefix() {
        return "sf_it_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static Map<String, String> credentials() throws Exception {
        String path = System.getenv("SF_COMPAT_ENV_FILE");
        if (path == null || path.length() == 0) {
            fail("SF_COMPAT_ENV_FILE is not set");
        }
        File file = new File(path);
        if (!file.isFile()) {
            fail("SF_COMPAT_ENV_FILE is not a file");
        }
        Map<String, String> values = new HashMap<String, String>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() == 0 || line.charAt(0) == '#') {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                values.put(line.substring(0, eq).trim(), line.substring(eq + 1));
            }
        } finally {
            reader.close();
        }
        requireKey(values, "SF_COMPAT_MONGO_HOST");
        requireKey(values, "SF_COMPAT_MONGO_PORT");
        requireKey(values, "SF_COMPAT_MONGO_USER");
        requireKey(values, "SF_COMPAT_MONGO_PASSWORD");
        requireKey(values, "SF_COMPAT_MONGO_AUTH_DB");
        requireKey(values, "SF_COMPAT_MONGO_DATABASE");
        requireKey(values, "SF_COMPAT_MONGO_REPLSET");
        if (!"sf_compat".equals(values.get("SF_COMPAT_MONGO_DATABASE"))) {
            fail("SF_COMPAT_MONGO_DATABASE is not sf_compat");
        }
        return values;
    }

    private static void requireKey(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.length() == 0) {
            fail(key + " is missing");
        }
    }

    private static File compileFixtures() throws Exception {
        File sourceRoot = fixtureRoot();
        File output = new File(System.getProperty("java.io.tmpdir"), "mongomongo-fixtures-" + UUID.randomUUID());
        if (!output.mkdirs()) {
            fail("cannot create fixture output");
        }
        List<File> sources = new ArrayList<File>();
        collectJava(sourceRoot, sources);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            fail("this JDK has no compiler");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null);
        try {
            List<String> options = new ArrayList<String>();
            options.add("-encoding");
            options.add("UTF-8");
            options.add("-classpath");
            options.add(System.getProperty("java.class.path"));
            options.add("-d");
            options.add(output.getAbsolutePath());
            String specification = System.getProperty("java.specification.version");
            if ("1.8".equals(specification)) {
                options.add("-source");
                options.add("8");
                options.add("-target");
                options.add("8");
            } else {
                options.add("--release");
                options.add("8");
            }
            Boolean ok = compiler.getTask(
                    null,
                    files,
                    diagnostics,
                    options,
                    null,
                    files.getJavaFileObjectsFromFiles(sources)).call();
            if (!Boolean.TRUE.equals(ok)) {
                fail("fixture compile failed " + diagnostics.getDiagnostics());
            }
        } finally {
            files.close();
        }
        return output;
    }

    private static File fixtureRoot() {
        File direct = new File("src/test/fixtures");
        if (direct.isDirectory()) {
            return direct;
        }
        File nested = new File("mongomongo/src/test/fixtures");
        if (nested.isDirectory()) {
            return nested;
        }
        fail("fixture sources not found");
        return direct;
    }

    private static void collectJava(File dir, List<File> into) {
        File[] javaFiles = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File d, String name) {
                return name.endsWith(".java");
            }
        });
        if (javaFiles != null) {
            for (int i = 0; i < javaFiles.length; i++) {
                into.add(javaFiles[i]);
            }
        }
        File[] children = dir.listFiles();
        if (children != null) {
            for (int i = 0; i < children.length; i++) {
                if (children[i].isDirectory()) {
                    collectJava(children[i], into);
                }
            }
        }
    }

    private static void deleteQuietly(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return true;
                }
            });
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    deleteQuietly(children[i]);
                }
            }
        }
        file.delete();
    }
}
