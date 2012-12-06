package net.csdn.mongo;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import net.csdn.common.Strings;
import net.csdn.common.collections.WowCollections;
import net.csdn.common.exception.AutoGeneration;
import net.csdn.common.reflect.ReflectHelper;
import net.csdn.common.reflect.WowMethod;
import net.csdn.mongo.annotations.*;
import net.csdn.mongo.association.*;
import net.csdn.mongo.commands.Delete;
import net.csdn.mongo.commands.Insert;
import net.csdn.mongo.commands.Save;
import net.csdn.mongo.commands.Update;
import net.csdn.mongo.embedded.AssociationEmbedded;
import net.csdn.mongo.embedded.BelongsToAssociationEmbedded;
import net.csdn.mongo.embedded.HasManyAssociationEmbedded;
import net.csdn.mongo.embedded.HasOneAssociationEmbedded;
import net.csdn.mongo.validate.ValidateParse;
import net.csdn.mongo.validate.ValidateResult;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.csdn.common.collections.WowCollections.*;
import static net.csdn.common.logging.support.MessageFormat.format;

/**
 * User: WilliamZhu
 * Date: 12-10-16
 * Time: 下午8:11
 * <p/>
 * The +Document+ class is the Parent Class of All MongoDB model,
 * this means any MongoDB model class should extends this class.
 * <p/>
 * Once your class extends Document,you get all ODM(Object Data Mapping )
 * and +net.csdn.mongo.Criteria+ (A convenient DSL) power.
 * <p/>
 * Example setup:
 * <p/>
 * ```java
 * public class Person extends Document{
 * static{
 * storeIn("persons")
 * }
 * }
 * ```
 */
public class Document {

    //instance attributes
    protected DBObject attributes = new BasicDBObject();
    protected boolean new_record = true;
    protected Map<String, Association> associations = map();
    protected Map<String, AssociationEmbedded> associationsEmbedded = map();


    //for embedded association
    public Document _parent;
    public String associationEmbeddedName;


    public <T> T attr(String key, Class<T> clzz) {
        return (T) attributes.get(key);
    }

    public Document attr(String key, Object obj) {
        attributes.put(key, obj);
        return this;
    }

    /*
    *  +Class Methods+ will be copied into subclass when system startup.
    *  you should access them using class methods instead of directly accessing;
    *
    *  Example:
    *
    *  ```java
    *  Person.collection();
    *  ```
    *  instead of
    *
    *  ```java
    *     Person.parent$_collection;//this is a wrong way to access class attributes
    *  ```
    *
    */
    protected static Map parent$_primaryKey;
    protected static boolean parent$_embedded;

    protected static List<String> parent$_fields;

    protected static DBCollection parent$_collection;
    protected static String parent$_collectionName;
    protected static Map<String, Association> parent$_associations;
    protected static Map<String, AssociationEmbedded> parent$_associations_embedded;

    public static MongoMongo mongoMongo;


    /*
     * Warning: all methods  modified by +static+ keyword , you should call them in subclass.
     */
    public static String collectionName() {
        return parent$_collectionName;
    }

    public static boolean embedded() {
        return parent$_embedded;
    }

    public static boolean embedded(boolean isEmbedded) {
        parent$_embedded = isEmbedded;
        return parent$_embedded;
    }


    public static DBCollection collection() {
        return parent$_collection;
    }

    public static Map<String, Association> associationsMetaData() {
        return parent$_associations;
    }

    public static Map<String, AssociationEmbedded> associationsEmbeddedMetaData() {
        return parent$_associations_embedded;
    }

    public static List fields() {
        return parent$_fields;
    }

    public boolean merge(Map item) {
        attributes.putAll(item);
        copyAllAttributesToPojoFields();
        return true;
    }

    /*
     # setting the collection name to store in.
     #
     # Example:
     #
     # <tt>Person.store_in(populdation)</tt>
     #
     # Warning: you should call this Class Method in subclass.
    */
    protected static DBCollection storeIn(String name) {

        parent$_collectionName = name;
        parent$_collection = mongoMongo.database().getCollection(name);
        return parent$_collection;
    }

    public static <T extends Document> T create(Map map) {
        throw new AutoGeneration();
    }

    public static <T extends Document> T create(DBObject object) {
        throw new AutoGeneration();
    }


    public boolean save() {
        if (valid()) {
            return Save.execute(this, false);
        }
        return false;
    }

    public boolean save(boolean validate) {
        if (validate && valid()) {
            return Save.execute(this, false);
        }
        return false;
    }

    public boolean insert() {
        if (valid()) {
            return Insert.execute(this, false);
        }
        return false;
    }

    public boolean insert(boolean validate) {
        if (validate && valid()) {
            return Insert.execute(this, false);
        }
        return false;
    }


    public boolean update() {
        if (valid()) {
            return Update.execute(this, false);
        }
        return false;
    }

    public boolean update(boolean validate) {
        if (validate && valid()) {
            return Update.execute(this, false);
        }
        return false;
    }


    public final List<ValidateResult> validateResults = list();
    public final static List validateParses = list();

    public boolean valid() {
        if (validateResults.size() > 0) return false;
        for (Object validateParse : validateParses) {
            ((ValidateParse) validateParse).parse(this, this.validateResults);
        }
        return validateResults.size() == 0;
    }

    public void remove() {
        Delete.execute(this);
    }


    public void remove(Document child) {
        String name = child.associationEmbeddedName;
        AssociationEmbedded association = this.associationEmbedded().get(name);
        if (association instanceof HasManyAssociationEmbedded) {
            List<Map> children = (List) this.attributes().get(name);
            Map shouldRemove = null;
            for (Map wow : children) {
                if (child.id().equals(wow.get("_id"))) {
                    shouldRemove = wow;
                    break;
                }
            }
            if (shouldRemove != null) {
                children.remove(shouldRemove);
            }

        } else {
            this.attributes().removeField(name);
        }

        this.associationEmbedded().get(name).remove(child);
        this.save();
    }


    /* +copySingleAttributeToPojoField+ and  +copyAllAttributesToPojoFields+
      since all model have attributes property,so we should sync values between
      Pojo fields and  attributes property
    */
    protected void copySingleAttributeToPojoField(String setterMethodName, Object param) {
        ReflectHelper.method(this, setterMethodName, param);
    }

    protected void copyAllAttributesToPojoFields() {
        try {
            BeanUtils.copyProperties(this, this.attributes);
        } catch (Exception e) {
            innerCopyAllAttributesToPojoFields();
        }

    }

    protected void innerCopyAllAttributesToPojoFields() {
        Field[] fields = this.getClass().getDeclaredFields();
        List allFields = list();
        for (Field field : fields) {
            allFields.add(field.getName());
        }

        Set keys = attributes.keySet();
        for (Object key : keys) {
            if (key instanceof String) {
                String strKey = (String) key;
                try {
                    if (allFields.contains(strKey)) {
                        Object obj = attributes.get(strKey);
                        Field target = ReflectHelper.findField(this.getClass(), strKey);
                        Class clzz = target.getType();
                        if (clzz != String.class) {
                            obj = Strings.stringToNumber(obj);
                        } else {
                            obj = Strings.numberToString(obj);
                        }
                        ReflectHelper.field(this, strKey, obj);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

    }

    public Object id() {
        return attributes.get("_id");
    }

    public Object id(Object id) {
        attributes.put("_id", id);
        return id;
    }

    public Document fields(String... names) {

        for (String name : names) {
            fields().add(name);
        }
        return this;
    }

    public Map<String, Association> associations() {
        return associations;
    }

    public Map<String, AssociationEmbedded> associationEmbedded() {
        return associationsEmbedded;
    }

    public void updateAttributes(Map attr) {

    }

    public DBObject reload() {
        attributes = collection().findOne(map("_id", attributes.get("_id")));
        return attributes;
    }

    public DBObject attributes() {
        return attributes;
    }


    //embedded Association methods

    public static HasManyAssociationEmbedded hasManyEmbedded(String name, Options options) {
        HasManyAssociationEmbedded association = new HasManyAssociationEmbedded(name, options);
        if (parent$_associations_embedded == null) parent$_associations_embedded = map();
        parent$_associations_embedded.put(name, association);
        return association;
    }

    public static BelongsToAssociationEmbedded belongsToEmbedded(String name, Options options) {
        BelongsToAssociationEmbedded association = new BelongsToAssociationEmbedded(name, options);
        if (parent$_associations_embedded == null) parent$_associations_embedded = map();
        parent$_associations_embedded.put(name, association);
        return association;

    }

    public static HasOneAssociationEmbedded hasOneEmbedded(String name, Options options) {
        HasOneAssociationEmbedded association = new HasOneAssociationEmbedded(name, options);
        if (parent$_associations_embedded == null) parent$_associations_embedded = map();
        parent$_associations_embedded.put(name, association);
        return association;
    }

    //Association methods
    public static HasManyAssociation hasMany(String name, Options options) {
        HasManyAssociation association = new HasManyAssociation(name, options);
        if (parent$_associations == null) parent$_associations = map();
        parent$_associations.put(name, association);
        return association;
    }

    public static HasOneAssociation hasOne(String name, Options options) {
        HasOneAssociation association = new HasOneAssociation(name, options);
        if (parent$_associations == null) parent$_associations = map();
        parent$_associations.put(name, association);
        return association;

    }

    public static BelongsToAssociation belongsTo(String name, Options options) {
        BelongsToAssociation association = new BelongsToAssociation(name, options);
        if (parent$_associations == null) parent$_associations = map();
        parent$_associations.put(name, association);
        return association;
    }


    public String toString() {
        String attrs = join(iterate_map(attributes.toMap(), new WowCollections.MapIterator<String, Object>() {
            @Override
            public Object iterate(String key, Object value) {
                if (value instanceof String) {
                    value = StringUtils.substring((String) value, 0, 50);
                }
                return format("{}: {}", key, value);
            }
        }), ",");
        return "#<" + this.getClass().getSimpleName() + " _id: " + id() + ", " + attrs + ">";
    }


    /*
    # Return the root +Document+ in the object graph. If the current +Document+
      # is the root object in the graph it will return self.
      def _root
        object = self
        while (object._parent) do object = object._parent; end
        object || self
      end
     */
    public Document _root() {
        Document doc = this;
        while (doc._parent != null) {
            doc = doc._parent;
        }
        return doc == null ? this : doc;
    }

    public boolean newRecord(boolean saved) {
        return new_record = saved;
    }

    //bind Criteria

    public static Criteria where(Map conditions) {
        //return new Criteria(Document.class).where(conditions);
        throw new AutoGeneration();
    }

    public static Criteria select(List fieldNames) {
        throw new AutoGeneration();
    }

    public static Criteria order(Map orderBy) {
        throw new AutoGeneration();
    }

    public static Criteria skip(int skip) {
        throw new AutoGeneration();
    }

    public static Criteria limit(int limit) {
        throw new AutoGeneration();
    }

    public static int count() {
        throw new AutoGeneration();
    }

    public static Criteria in(Map in) {
        throw new AutoGeneration();
    }

    public static Criteria not(Map not) {
        throw new AutoGeneration();
    }

    public static Criteria notIn(Map notIn) {
        throw new AutoGeneration();
    }

    public static <T> T findById(Object id) {
        throw new AutoGeneration();
    }

    public static <T> List<T> find(List list) {
        throw new AutoGeneration();
    }

    public static <T> List<T> findAll() {
        throw new AutoGeneration();
    }

    protected Map<Callbacks.Callback, List<WowMethod>> callbacks = map();

    private void collectCallback() {
        Method[] methods = this.getClass().getDeclaredMethods();
        for (Method method : methods) {
            WowMethod wowMethod = new WowMethod(this, method);

            if (method.isAnnotationPresent(BeforeSave.class)) {
                putAndAdd(Callbacks.Callback.before_save, wowMethod);
            }

            if (method.isAnnotationPresent(BeforeCreate.class)) {
                putAndAdd(Callbacks.Callback.before_create, wowMethod);
            }

            if (method.isAnnotationPresent(BeforeDestroy.class)) {
                putAndAdd(Callbacks.Callback.before_destroy, wowMethod);
            }

            if (method.isAnnotationPresent(BeforeUpdate.class)) {
                putAndAdd(Callbacks.Callback.before_update, wowMethod);
            }

            if (method.isAnnotationPresent(BeforeValidation.class)) {
                putAndAdd(Callbacks.Callback.before_validation, wowMethod);
            }


            if (method.isAnnotationPresent(AfterCreate.class)) {
                putAndAdd(Callbacks.Callback.after_create, wowMethod);
            }

            if (method.isAnnotationPresent(AfterDestory.class)) {
                putAndAdd(Callbacks.Callback.after_destroy, wowMethod);
            }

            if (method.isAnnotationPresent(AfterSave.class)) {
                putAndAdd(Callbacks.Callback.after_save, wowMethod);
            }

            if (method.isAnnotationPresent(AfterUpdate.class)) {
                putAndAdd(Callbacks.Callback.after_update, wowMethod);
            }

            if (method.isAnnotationPresent(AfterValidation.class)) {
                putAndAdd(Callbacks.Callback.after_validation, wowMethod);
            }

        }
    }

    public void runCallbacks(Callbacks.Callback callback) {
        if (callbacks.size() == 0) {
            collectCallback();
        }
        List<WowMethod> wowMethods = callbacks.get(callback);
        if (wowMethods == null) return;
        for (WowMethod wowMethod : wowMethods) {
            wowMethod.invoke();
        }

    }

    private void putAndAdd(Callbacks.Callback key, WowMethod item) {
        if (callbacks.containsKey(key)) {
            callbacks.get(key).add(item);
        } else {
            callbacks.put(key, list(item));
        }
    }

    public static Criteria nativeQuery(String tableName) {
        return new Criteria(tableName);
    }

}
