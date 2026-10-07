package io.github.harshmittal.urlshortener.link.domain;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Returns the given codes in order, then fails, so tests control collisions exactly. */
public final class ScriptedShortCodeGenerator implements ShortCodeGenerator {

    private final Deque<ShortCode> script;

    public ScriptedShortCodeGenerator(List<ShortCode> codes) {
        this.script = new ArrayDeque<>(codes);
    }

    @Override
    public synchronized ShortCode next() {
        if (script.isEmpty()) {
            throw new IllegalStateException("script exhausted");
        }
        return script.removeFirst();
    }

    public synchronized int remaining() {
        return script.size();
    }
}
