## Welcome to MongoMongo

MongoMongo is an Object-Document-Mapper (ODM) for MongoDB written in Java.

The philosophy of MongoMongo is to provide a familiar API to Java developers who have been using [ActiveORM](https://github.com/allwefantasy/active_orm) or hibernate,
while leveraging the power of MongoDB's schemaless and performant document-based design,
dynamic queries, and atomic modifier operations.


##Sample code

```java
public class Blog extends Document {
    static {
        storeIn("blogs");
        //the only diffrence bettween related and embedded  is here.Using *Embedded Suffix,and without ForeighKey declaired
        hasManyEmbedded("articles", new Options(map(
                Options.n_kclass, Article.class
        )));

    }

    public AssociationEmbedded articles() {
        throw new AutoGeneration();
    }


    //属性啦
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
        storeIn("articles");
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

```java
public class StartUpMongoMongoFilter implements Filter {

    public void doFilter(ServletRequest req, ServletResponse res,
            FilterChain chain) throws IOException, ServletException {
        chain.doFilter(req, res);
    }
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
    public void destroy() {

    }
}

```

and then modify your web.xml file

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

###Index And Alias

You can configure your document in 'static block'.As a Javaer maybe you prefer using Annotation,but 'static block' is more flexible.
Annotation have too much limitation,for example,can not hold a complex object.

```java
public class Blog extends Document {
    static {
        storeIn("blogs");
	    alias("_id", "userName");
	    index(map("name", -1), map());
	    index(map("tags.count", -1), map());
    }
 }
```
when userName will be saved in db called _id;
System will create a separate index for name and tags.count.



###Fields
Even though MongoDB is a schemaless database, most uses will be with web applications where form parameters always come to the server as strings.
MongoMongo provides an easy mechanism for transforming these strings into their appropriate types through the definition of fields
 in your document. And the other benefit is ,you can manipulate a object field instead of a Map key .

 ```java
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

####create

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

####remove

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













