## Welcome to MongoMongo

MongoMongo is an Object-Document-Mapper (ODM) for MongoDB written in Java.

The philosophy of MongoMongo is to provide a familiar API to Java developers who have been using [ActiveORM](https://github.com/allwefantasy/active_orm) or Hibernate, while leveraging the power of MongoDB's schemaless and performant document-based design,dynamic queries, and atomic modifier operations.


##Sample code

```
public class Blog extends Document {
    static {
        
        storeIn("blogs");                
        hasManyEmbedded("articles", new Options(map(
                Options.n_kclass, Article.class
        )));
        
        //create index
        index(map("blogTitle", -1), map(unique,true));

        //validate uerName field
        validate("userName",map(length,map(
                minimum,5
        )));

    }
    
    //association related
    public AssociationEmbedded articles() {
        throw new AutoGeneration();
    }

    private String userName;
    private String blogTitle;

    //setter/getter methods,of cource , they are not required
    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getBlogTitle() {
        return blogTitle;
    }

    public void setBlogTitle(String blogTitle) {
        this.blogTitle = blogTitle;
    }



}


public class Article extends Document {
    static {        
        belongsToEmbedded("blog", new Options(map(
                Options.n_kclass, Blog.class
        )));
    }

    public AssociationEmbedded blog() {
        throw new AutoGeneration();
    }

    private String title;
    private String body;
}

public class Usage{
  public static void main(String[] args){

     Blog blog = Blog.where(map("userName","sexy java")).in(map("id",list(1,2,3))).singleFetch();
     blog.articles().build(map("title","i am title","body","i am body"));
     blog.save();
  }

}
```

## Installation

### Prerequisites
There are few things you need to have in your toolbox before tackling a web application using MongoMongo.

* A good to advanced knowledge of Java.
* Have good knowledge of your web framework if using one.
* A thorough understanding of MongoDB.

Anyway ,you also  should notice that MongoMongo now is at version 1.0.There are a lot features to develop in order
to take advantage of MongoDB.
If you application is simple, i recommend you just use MongoMongo since it can reduce a lot of jobs from operating
MongoDB.

### Installation

I suppose you use it in a standard Servlet Container like Tomcat,Jetty. In order to make MongoMongo work properly,
you will write a filter like follows(the core code is in init.The name of filter as yourself)


For example:

```
public class StartUpMongoMongoFilter implements Filter {

   
    public void init(FilterConfig config) throws ServletException {
            // Actually this means you should put your mongo configuration in a yaml file.And then load it.
            InputStream inputStream = FirstFilter.class.getResourceAsStream("application_for_test.yml");
            Settings settings = InternalSettingsPreparer.simplePrepareSettings(ImmutableSettings.Builder.EMPTY_SETTINGS,
                    inputStream);

            //when settings have been build ,now we can configure MongoMongo
            try {
                MongoMongo.CSDNMongoConfiguration csdnMongoConfiguration = new MongoMongo.CSDNMongoConfiguration("development", settings, FirstFilter.class);
                MongoMongo.configure(csdnMongoConfiguration);
            } catch (Exception e) {
                e.printStackTrace();
            }

    }
    
}

```

and then modify your web.xml file,add your filter to web.xml.

```xml
<filter>
    <filter-name>StartUpMongoMongoFilter</filter-name>
    <filter-class>
        com.example.filters.StartUpMongoMongoFilter
    </filter-class>
</filter>
<filter-mapping>
    <filter-name>StartUpMongoMongoFilter</filter-name>
    <url-pattern>/*</url-pattern>
</filter-mapping>
```

#### Configuration

MongoMongo configuration can be done through a yaml file that specifies your options and database sessions.
The normal configuration is as follows,
which sets the default session to "127.0.0.1:27017" , provides a single database in that session named "wow",
put documents in  `com.example.document` package.

```yaml
#mode
mode:
  development
#mode=production

###############datasource config##################
#mysql,mongodb,redis configuration
development:
    datasources:
        mongodb:
           host: 127.0.0.1
           port: 27017
           database: wow
           disable: false


production:
    datasources:
        mongodb:
           host: 127.0.0.1
           port: 27017
           database: wow



###############application config##################
#tell MongoMongo where is your document classes


application:
   document:   com.example.document
```

## MongoDB 5.x upgrade notes

MongoMongo now uses `org.mongodb:mongodb-driver-legacy:5.13.0`. Maven resolves the required `mongodb-driver-core`, `mongodb-driver-sync`, and `bson` artifacts with it; do not copy only the legacy jar. `org.mongodb:bson-record-codec` is excluded: it is the driver-core transitive Java 17 Record codec (class major 61), which the legacy Document API does not distribute and the driver tolerates being absent. The minimum Java level was **raised to 8** — the project compiles to Java 8 bytecode (class major 52), and the same jar runs on JDK 8 and JDK 17. Because the 5.x driver removed `com.mongodb.Mongo` (the client type is now `MongoClient`, and `DB.getMongo()` is `DB.getMongoClient()`), code built against the old driver API must be adjusted and **recompiled**; a jar built on `mongo-java-driver` 2.x/3.x call sites is not binary compatible.

`csdn-common`'s old `javassist:javassist` dependency is excluded and replaced by `org.javassist:javassist:3.33.0-GA`. On JDK 9+, document classes are defined through a same-package `ServiceFrameworkPackageAnchor` class (or a configuration marker already inside the document package) supplied by the application/test loader. JDK 8 keeps the old `CtClass#toClass` path and does not need that anchor. No `--add-opens` is required.

Connection settings support either a full URI or discrete keys:

```yaml
mode: development
development:
  datasources:
    mongodb:
      uri: mongodb://${MONGO_USER}:${MONGO_PASSWORD}@${MONGO_HOST}:${MONGO_PORT}/${MONGO_DATABASE}?replicaSet=${MONGO_REPLSET}&authSource=${MONGO_AUTH_DB}
      # or: host/port/database/username/password/authenticationDatabase/replicaSet
      disable: false
```

When `uri` is present it wins over discrete host, port, username, password, and authentication database. A database path in the URI wins over `database`; the discrete database is only the fallback for a URI without a path. Do not put real credentials in source control; use environment variables, a private env file, or your configuration loader's placeholders. URI parse errors do not echo the URI or password.

The standalone lifecycle is intentionally single-configuration. `MongoMongo.configure(...)` registers one active shared client; a second enabled or disabled `configure` is rejected until `MongoMongo.disconnect()` runs. `disconnect()` closes that registered client once and clears `Document.mongoMongo`. A directly constructed `new MongoMongo(config)` remains caller-owned, is not registered as the shared client, and must be closed by the caller. Configure/connect failures propagate to the caller and clean up the partially opened client.

This is still the standalone ODM, not the ServiceFramework integration: the enhancer replaces accessor bodies, callbacks still use `attr`, and there is no framework `EnhancementContext` or multi-application context isolation. Build and test this repository's own jar separately from `serviceframework-mongo`.

Version evidence: MongoDB 8.3.x is the current stable series in <https://www.mongodb.com/docs/manual/release-notes/>; driver 5.13.0 was selected from <https://repo.maven.apache.org/maven2/org/mongodb/mongodb-driver-legacy/maven-metadata.xml>.

Focused live tests use the isolated ServiceFramework runner and must be run from this repository's own directory. They read `SF_COMPAT_ENV_FILE` supplied by the runner and never print credentials. `SF_ROOT` assumes a sibling ServiceFramework checkout under `$HOME/projects` (adjust if yours lives elsewhere); `JDK17_HOME`/`JDK8_HOME` point at your own JDKs. `mvn` uses your default settings — add `-s <your-settings.xml>` only if you need a custom mirror:

```bash
SF_ROOT="$HOME/projects/ServiceFramework"
JDK17_HOME=/path/to/your/jdk17
JDK8_HOME=/path/to/your/jdk8

cd "$HOME/projects/mongomongo"
"$SF_ROOT/dev/mongo-modern-services.sh" run -- \
  env SF_COMPAT_MONGO=true \
  JAVA_HOME="$JDK17_HOME" PATH="$JDK17_HOME/bin:$PATH" \
  mvn -Dtest=MongoStandaloneLiveTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Swap `JAVA_HOME`/`PATH` to `$JDK8_HOME` for the JDK 8 run. Final independent verification passed `MongoStandaloneLiveTest` 6/6 on both JDK 8 and JDK 17 against MongoDB 8.3.11, plus an out-of-tree packaged-jar probe on both JDKs; earlier worker self-test counts (5 tests, then 6/6 on JDK 17) are kept only as history.

##Documents

Documents are the core objects in MongoMongo and any object that is to be persisted to the database must extends `net.csdn.mongo.Document`.
The representation of a Document in MongoDB is a BSON object that is very similar to a Java Map or JSON object.
But cause of the rigid grammar in Java,it's a really tough thing to operate MongoDB using MongoDB Java Driver.  Extends
`net.csdn.mongo.Document` will make your document more powerful and just like you are using a ORM.
Documents can be stored in their own collections in the database, or can be embedded in other Documents n levels deep.



###Storage

You can configure your document in 'static block'.As a Javaer maybe you prefer using Annotation,but 'static block' is more flexible.
Annotation have too much limitation,for example,can not hold a complex object.

```java
public class Blog extends Document {
    static {
        storeIn("blogs");
    }
 }
```

storeIn("blogs") means you store you data in 'blogs' collection when using Blog.


###Index 

You can configure your document in 'static block'.As a Javaer maybe you prefer using Annotation,but 'static block' is more flexible.
Annotation have too much limitation,for example,can not hold a complex object.

```java
public class Blog extends Document {
    static {
	    index(map("name", -1), map("unique", true, "name", "ssn_index"));	    
    }
 }
```
then MongoMongo will create a index for `name` field called ssh_index.

###Alias Example

```java
public class Blog extends Document {
    static {      
	    alias("_id", "userName");	   
    }
 }
```

MongoMongo will save userName as _id in MongoDB.


###Fields
Even though MongoDB is a schemaless database, most usage will be with web applications where form parameters always come to the server as strings.
MongoMongo provides an easy mechanism for transforming these strings into their appropriate types through the definition of fields in your document. And the other benefit is ,you can manipulate a object field instead of a Map key. As we know,it's hard to operate Map in Java.

 ```
 public class Blog extends Document {

     //properties and their access methods
     public String getUserName() {
         return userName;
     }

     public void setUserName(String userName) {
         this.userName = userName;
     }

     public String getBlogTitle() {
         return blogTitle;
     }

     public void setBlogTitle(String blogTitle) {
         this.blogTitle = blogTitle;
     }

     private String userName;
     private String blogTitle;

 }
 ```
 however,fields definition is optional. If you do not have any fields or acess methods in document,you can access them as follows:

 ```
 public class Blog extends Document {

 }
 
 //get a field
 String userName = blog.attr("userName",String.class);
 //set a field
 blog.attr("userName","gangNan Style")
 
 ```

 ### Persistence

####create Example

```java
Person person = Person.create(map(
     first_name, "Heinrich",
     last_name, "Heine"
     ));

```
Remember,create just return a Person object,if you wanna persist it in MongoDB,you should invoke 'save' method manually.

```java
 person.save();
```

####remove Example

```java
  Person person = Person.findById(10);
  person.remove();
```

###Querying

All queries in MongoMongo are Criteria,
which is a chainable and lazily evaluated wrapper to a MongoDB dynamic query.
Criteria only touch the database when you manually invoke `fetch()` or  `sinleFetch()`.

##Queryable DSL

MongoMongo's main query DSL is provided by `net.csdn.mongo.Criteria` class.
Most method that is available on `net.csdn.mongo.Criteria` as well as off the model's class.

```java
Band.where(map(name,"Depeche Mode"))
Band.
  where(map("founded.gte" ,"1980-1-1")).
  in(map("name", list("Tool", "Deftones" )))
```

With each chained method on a criteria, a newly cloned criteria is returned with the new query added.
This is so that with scoping or exposures, for example, the original queries are not modified and reusable.

for now ,Methods  supported by MongoMongo as follows:

 * count
 * where
 * findById
 * find
 * not
 * all
 * and
 * in
 * notIn
 * select
 * order
 * skip
 * limit
 * first
 * last
 
###Validate Example

```
public class TTUser extends Document {
    static {
        validate("userName",map(length,map(
                minimum,5
        )));

    }
 }
```
here we use validate method to define rule like this:
userName's lenght should exceed 5.


###Callback Example

```
public class TTUser extends Document {
    @BeforeSave
    private void beforeSave() {
        System.out.println("i am saved");
    }
 }
```

when you invoke TTUser.save(),beforeSave method will be invoked.















