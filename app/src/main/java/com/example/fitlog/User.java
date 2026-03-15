package com.example.fitlog;

public class User {
    // Veritabanındaki sütun isimleriyle birebir aynı olmalı
    private String username;
    private String email;
    private String password_hash;
    private String first_name;
    private String last_name;

    // 1. Boş Constructor (GSON'ın nesneyi oluşturabilmesi için gereklidir)
    public User() {}

    // 2 parametreli kayıt için bu Constructor'ı ekle
    public User(String username, String email, String password,String firstName, String lastName) {
        this.username = username;
        this.email = email;
        this.password_hash = password;
        this.first_name = firstName;
        this.last_name = lastName;
    }

    // 2. Ana Constructor (Kullanıcı kaydı için gerekli alanlar)
    public User(String username, String email, String password_hash) {
        this.username = username;
        this.email = email;
        this.password_hash = password_hash;
    }

    // 3. Getter ve Setter Metotları
    // (Android Studio'da sınıfın içine sağ tıkla -> Generate -> Getter and Setter -> Hepsini seç)

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword_hash() { return password_hash; }
    public void setPassword_hash(String password_hash) { this.password_hash = password_hash; }

    public String getFirst_name() { return first_name; }
    public void setFirst_name(String first_name) { this.first_name = first_name; }

    public String getLast_name() { return last_name; }
    public void setLast_name(String last_name) { this.last_name = last_name; }
}