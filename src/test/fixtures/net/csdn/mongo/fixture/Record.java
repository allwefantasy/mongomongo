package net.csdn.mongo.fixture;

import net.csdn.mongo.Document;
import net.csdn.mongo.annotations.BeforeSave;

import static net.csdn.common.collections.WowCollections.map;

public class Record extends Document {
    static {
        storeIn(Names.of("record"));
        index(map("title", 1), map("name", "record_title_idx", "unique", true));
    }

    private String title;
    private int seq;
    private boolean callbackRan;

    @BeforeSave
    public void markBeforeSave() {
        attr("callbackRan", Boolean.TRUE);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getSeq() {
        return seq;
    }

    public void setSeq(int seq) {
        this.seq = seq;
    }

    public boolean isCallbackRan() {
        return callbackRan;
    }

    public void setCallbackRan(boolean callbackRan) {
        this.callbackRan = callbackRan;
    }
}
