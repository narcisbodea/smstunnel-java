package io.smstunnel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** One page of received texts, newest first. Iterate it directly, or use {@link #getTexts()}. */
public final class ReceivedTexts implements Iterable<ReceivedText> {

    private final List<ReceivedText> texts;
    private final long total;
    private final int page;
    private final int limit;
    private final boolean hasMore;
    private final Map<String, Object> raw;

    private ReceivedTexts(Map<String, Object> raw) {
        Map<String, Object> data = Fields.object(raw, "data");
        List<ReceivedText> items = new ArrayList<>();
        for (Object item : Fields.list(data, "messages")) {
            if (item instanceof Map) {
                items.add(ReceivedText.from(Fields.object(item)));
            }
        }
        this.texts = Collections.unmodifiableList(items);
        this.total = Fields.longValue(data, "total", items.size());
        this.page = (int) Fields.longValue(data, "page", 1);
        this.limit = (int) Fields.longValue(data, "limit", items.size());
        this.hasMore = Fields.bool(data, "hasMore");
        this.raw = raw;
    }

    static ReceivedTexts from(Map<String, Object> raw) {
        return new ReceivedTexts(raw);
    }

    /** The texts on this page, newest first. */
    public List<ReceivedText> getTexts() {
        return texts;
    }

    /** All the texts that match, on every page. */
    public long getTotal() {
        return total;
    }

    /** This page's number, from 1. */
    public int getPage() {
        return page;
    }

    /** The page size SMSTunnel used. */
    public int getLimit() {
        return limit;
    }

    /** True when there is a next page: ask for {@code page + 1}. */
    public boolean hasMore() {
        return hasMore;
    }

    /** The whole answer, for fields this version does not map yet. */
    public Map<String, Object> getRaw() {
        return raw;
    }

    @Override
    public Iterator<ReceivedText> iterator() {
        return texts.iterator();
    }

    @Override
    public String toString() {
        return "ReceivedTexts{page=" + page + ", texts=" + texts.size() + ", total=" + total + ", hasMore=" + hasMore + "}";
    }
}
