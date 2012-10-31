## Welcome to MongoMongo

MongoMongo is an Object-Document-Mapper (ODM) for MongoDB written in Java.

The philosophy of MongoMongo is to provide a familiar API to Java developers who have been using [ActiveORM](https://github.com/allwefantasy/active_orm) or hibernate,
while leveraging the power of MongoDB's schemaless and performant document-based design,
dynamic queries, and atomic modifier operations.


##Sample code

```java
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String title;
    public String body;
}

public class Usage{
  public static void main(String[] args){

     Blog blog = Blog.where(map("userName","sexy java")).in(map("id",list(1,2,3))).singleFetch();
     blog.articles().create(map("title","i am title","body","i am body"));
     blog.save();

  }

}
```

## Getting Started

#### Integrate following code  to your application.

When you write a  web application,you should create a filter
For example:

```java

public class FirstFilter implements Filter {

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
    <filter-name>FirstFilter</filter-name>
    <filter-class>
        com.example.filters.FirstFilter
    </filter-class>
</filter>
<filter-mapping>
    <filter-name>FirstFilter</filter-name>
    <url-pattern>/*</url-pattern>
</filter-mapping>
```

When you write Normal Application,just put the code in filter to your main method.


#### Configuration file demo

```java
development:
    datasources:
       mongodb:
           host: 127.0.0.1
           port: 27017
           database: data_center
           disable: false

application:
    document:   com.example.document
    
       
```


#### Demo
MongoDB storage is  json-style.So there are two kind of relationship.
Traditonal relationship like ORM,and Embedded Relationship.


let us begin with Traditonal relationship. Each Model have his owe collection in MongoDB.

```java
public class Person extends Document {
    //configure your model
    static {
        //store Person in MongoDB collection named "persons"
        storeIn("persons");
        
        //build the relationship bettween Person and Address.
        //just tell me Class and ForeignKey name.
        
        hasMany("addresses", new Options(map(
                Options.n_kclass, Address.class,
                Options.n_foreignKey, "person_id"
        )));
        
        hasOne("idcard", new Options(map(
                Options.n_kclass, IdCard.class,
                Options.n_foreignKey, "person_id"
        )));
    }

    //declare this method without implementation,keep method name according to convension of association.
    //the purpose of this method is to active IDE code assist.
    public Association addresses() {
        throw new AutoGeneration();
    }

    public Association idcard() {
        throw new AutoGeneration();
    }

    //fied names.no special. it is optinal
    private String name;
    private Integer bodyLength;


    //let them auto generated by IDE.then leave them,
    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getBodyLength() {
        return bodyLength;
    }

    public void setBodyLength(Integer bodyLength) {
        this.bodyLength = bodyLength;
    }
}


public class Address extends Document {
    static {
         
        storeIn("addresses");

        belongsTo("person", new Options(
                map(
                        Options.n_kclass, Person.class,
                        Options.n_foreignKey, "person_id"
                )

        ));
    }

    public Association person() {
        throw new AutoGeneration();
    }


    private String location;

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}
```

Since you have deine models,now you can use them.

```java
//create a person from Hash
Person person = Person.create(map(
                "_id", 100,
                "name", "google",
                "bodyLength", 10
        ));

//save it
person.save();

//add a new address to person's address collection,then save the relation
person.addresses().build(map("_id", 77, "location", "天国的世界")).save();

//query critiria
List<Person> persons = Person.where(map("name","google")).fetch();

//find by id
Person person = Person.findById(100);

//you can find many id at one time
List<Person> persons = Person.find(list(100,1000));

person.addresses().filter().findById(77);

person.addresses().filter().where(map("_id",77)).singleFetch();

//delete the person
person.remove();

```


Now let us check embedded documents relationship.


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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String title;
    public String body;
}
```

The Usage of embedded Relationship is the same.




