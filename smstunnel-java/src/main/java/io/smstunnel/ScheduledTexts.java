package io.smstunnel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * One page of the texts this key scheduled, from {@link SmsTunnelClient#getScheduledTexts(ScheduledTextsQuery)}.
 * Iterate it directly, or use {@link #getTexts()}.
 */
public final class ScheduledTexts implements Iterable<ScheduledText> {

    private final List<ScheduledText> texts;
    private final long total;
    private final int page;
    private final int limit;
    private final long waiting;
    private final long max;
    private final Map<String, Object> raw;

    private ScheduledTexts(Map<String, Object> raw) {
        Map<String, Object> data = Fields.object(raw, "data");
        List<ScheduledText> items = new ArrayList<>();
        for (Object item : Fields.list(data, "items")) {
            if (item instanceof Map) {
                items.add(ScheduledText.from(Fields.object(item)));
            }
        }
        this.texts = Collections.unmodifiableList(items);
        this.total = Fields.longValue(data, "total", items.size());
        this.page = (int) Fields.longValue(data, "page", 1);
        this.limit = (int) Fields.longValue(data, "limit", items.size());
        this.waiting = Fields.longValue(data, "waiting", 0);
        this.max = Fields.longValue(data, "max", 0);
        this.raw = raw;
    }

    static ScheduledTexts from(Map<String, Object> raw) {
        return new ScheduledTexts(raw);
    }

    /** The texts on this page: waiting ones soonest first, done ones (sent, failed, canceled) newest first. */
    public List<ScheduledText> getTexts() {
        return texts;
    }

    /** All the texts of this key that match (waiting, or done), on every page. */
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

    /** True when {@link #getTotal()} counts texts after this page: ask for {@code page + 1}. */
    public boolean hasMore() {
        return !texts.isEmpty() && (long) page * limit < total;
    }

    /**
     * How many texts wait to be sent in the whole account, those of every key and of the dashboard together: the
     * plan lets {@link #getMax()} wait at once. 0 when SMSTunnel did not say.
     */
    public long getWaiting() {
        return waiting;
    }

    /**
     * How many texts the plan lets wait at once (Free 10, Starter 50, Standard 200, Professional 500, Enterprise 1000).
     * 0 when SMSTunnel did not say.
     */
    public long getMax() {
        return max;
    }

    /** The whole answer, for fields this version does not map yet. */
    public Map<String, Object> getRaw() {
        return raw;
    }

    @Override
    public Iterator<ScheduledText> iterator() {
        return texts.iterator();
    }

    @Override
    public String toString() {
        return "ScheduledTexts{page=" + page + ", texts=" + texts.size() + ", total=" + total + ", waiting=" + waiting
                + ", max=" + max + "}";
    }
}
