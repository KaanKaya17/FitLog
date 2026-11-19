package com.example.fitlog;

public class Validator {

    public static boolean isValidEmail(String email) {

        if(email == null || email.trim().isEmpty()){
            return false;
        }

        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    public static boolean isValidFirstName(String firstName){

        if(firstName == null || firstName.trim().isEmpty()){
            return false;
        }

        if(firstName.length() < 2){
            return false;
        }

        return firstName.matches("[a-zA-ZğüşıöçĞÜŞİÖÇ]+");
    }

    public static boolean isValidLastName(String lastName){

        if(lastName == null || lastName.trim().isEmpty()){
            return false;
        }

        if(lastName.length() < 2){
            return false;
        }

        return lastName.matches("[a-zA-ZğüşıöçĞÜŞİÖÇ]+");
    }

    public static boolean isValidPassword(String password){

        if(password == null){
            return false;
        }

        if(password.length() < 8 || password.length() > 72){ // bcrypt 72 bayt sınırı
            return false;
        }

        boolean hasUpper = password.matches(".*[A-Z].*");
        boolean hasLower = password.matches(".*[a-z].*");
        boolean hasDigit = password.matches(".*[0-9].*");

        return hasUpper && hasLower && hasDigit;
    }

    /** 3-30 karakter; harf, rakam, alt çizgi ve nokta. */
    public static boolean isValidUserName(String userName){

        if(userName == null){
            return false;
        }

        return userName.matches("[a-zA-Z0-9_.]{3,30}");
    }
}
