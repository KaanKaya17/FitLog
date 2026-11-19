package com.example.fitlog.ui;

/** Ekrana gösterilecek bir yükleme durumunu ve sonucunu taşır. */
public final class Resource<T> {

    public enum Status { LOADING, SUCCESS, ERROR }

    public final Status status;
    public final T data;
    public final Throwable error;

    private Resource(Status status, T data, Throwable error) {
        this.status = status; this.data = data; this.error = error;
    }

    public static <T> Resource<T> loading(T previous) { return new Resource<>(Status.LOADING, previous, null); }
    public static <T> Resource<T> success(T data) { return new Resource<>(Status.SUCCESS, data, null); }
    public static <T> Resource<T> error(Throwable e, T previous) { return new Resource<>(Status.ERROR, previous, e); }

    public boolean isLoading() { return status == Status.LOADING; }
    public boolean isSuccess() { return status == Status.SUCCESS; }
    public boolean isError() { return status == Status.ERROR; }
}
