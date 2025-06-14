package com.alizainsolutions.mycontacts.Model;




public class ContactModel {
    private String id;
    private String name;
    private String phoneNumber;
    private String photoUri;
    private boolean isStarred; // New field for favorite status

    // Constructor for general contacts (can default isStarred to false)
    public ContactModel(String id, String name, String phoneNumber, String photoUri) {
        this.id = id;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.photoUri = photoUri;
        this.isStarred = false; // Default to not starred
    }

    // New constructor to explicitly set favorite status
    public ContactModel(String id, String name, String phoneNumber, String photoUri, boolean isStarred) {
        this.id = id;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.photoUri = photoUri;
        this.isStarred = isStarred;
    }

    // --- Getters ---
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getPhotoUri() {
        return photoUri;
    }

    public boolean isStarred() { // Getter for favorite status
        return isStarred;
    }

    // --- Setters (if needed, though generally models are immutable after creation) ---
    public void setName(String name) {
        this.name = name;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public void setPhotoUri(String photoUri) {
        this.photoUri = photoUri;
    }

    public void setStarred(boolean starred) { // Setter for favorite status
        isStarred = starred;
    }
}