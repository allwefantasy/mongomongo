package net.csdn.mongo.commands;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import net.csdn.common.reflect.ReflectHelper;
import net.csdn.mongo.Callbacks;
import net.csdn.mongo.Document;

import static net.csdn.common.reflect.ReflectHelper.method;
import static net.csdn.common.reflect.ReflectHelper.staticMethod;

/**
 * User: WilliamZhu
 * Date: 12-10-17
 * Time: 下午2:07
 */
public class Save {

    public static boolean execute(Document doc, boolean validate) {
        Document parent = doc._parent;
        if (parent != null) {
            Save.execute(parent, validate);
        } else {
            doc.runCallbacks(Callbacks.Callback.before_save);
            //method(doc, "copyPojoFieldsToAllAttributes");
            //we cannot call doc.collection().remove() directly,because of the dam inheritance of static methods in java
            DBCollection collection = (DBCollection) staticMethod(doc.getClass(), "collection");
            collection.insert(new BasicDBObject(doc.attributes()));
            doc.runCallbacks(Callbacks.Callback.after_save);
        }

        return true;
    }
}
