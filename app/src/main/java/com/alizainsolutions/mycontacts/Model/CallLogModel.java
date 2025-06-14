package com.alizainsolutions.mycontacts.Model;




public class CallLogModel {
    private String number;
    private String name; // Can be null if contact is not saved
    private long date; // Timestamp in milliseconds
    private int type; // Call type: incoming, outgoing, missed (from CallLog.Calls.TYPE constants)
    private long duration; // Call duration in seconds
    private String photoUri; // URI to contact photo, can be null
    private String contactId; // ID of the contact if found, can be null
    private String callLogId; // Unique ID of the call log entry itself (from CallLog.Calls._ID)

    public CallLogModel(String number, String name, long date, int type, long duration, String photoUri, String contactId, String callLogId) {
        this.number = number;
        this.name = name;
        this.date = date;
        this.type = type;
        this.duration = duration;
        this.photoUri = photoUri;
        this.contactId = contactId;
        this.callLogId = callLogId; // Initialize callLogId
    }

    // --- Getters ---
    public String getNumber() {
        return number;
    }

    public String getName() {
        return name;
    }

    public long getDate() {
        return date;
    }

    public int getType() {
        return type;
    }

    public long getDuration() {
        return duration;
    }

    public String getPhotoUri() {
        return photoUri;
    }

    public String getContactId() {
        return contactId;
    }

    public String getCallLogId() { // Getter for callLogId
        return callLogId;
    }

    // --- Setters (if needed, though generally call logs are immutable) ---
    // public void setName(String name) { this.name = name; }
    // public void setPhotoUri(String photoUri) { this.photoUri = photoUri; }
    // public void setContactId(String contactId) { this.contactId = contactId; }
    // public void setCallLogId(String callLogId) { this.callLogId = callLogId; }
}