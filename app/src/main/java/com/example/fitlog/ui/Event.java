package com.example.fitlog.ui;

/** Bir kez tüketilen olay (toast, gezinme vb. ekran döndürmede tekrarlanmasın). */
public final class Event<T> {
    private final T content;
    private boolean handled;

    public Event(T content) { this.content = content; }

    public T consume() {
        if (handled) return null;
        handled = true;
        return content;
    }

    public T peek() { return content; }
}
