package com.example.fitlog;

public class UserInsertDto {
    private String username;
    private String email;
    private String password_hash;
    private String first_name;
    private String last_name;

    public UserInsertDto(String username, String email, String password, String firstName, String lastName) {
        this.username      = username;
        this.email         = email;
        this.password_hash = password;
        this.first_name    = firstName;
        this.last_name     = lastName;
    }
}