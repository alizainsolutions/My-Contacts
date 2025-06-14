package com.alizainsolutions.mycontacts;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.SmsManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alizainsolutions.mycontacts.Adapter.MessageAdapter;
import com.alizainsolutions.mycontacts.Model.MessageModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MessageActivity extends AppCompatActivity {

    private static final int REQUEST_SMS_PERMISSIONS = 101;

    private RecyclerView recyclerView;
    private EditText messageInput;
    private ImageButton sendButton;

    private MessageAdapter messageAdapter;
    private List<MessageModel> messageList;

    private String contactNumber; // Normalized contact number
    private String originalContactNumber; // Keep the original for broader matching
    private boolean userAtBottom = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        originalContactNumber = getIntent().getStringExtra("contact_number");
        String contactName = getIntent().getStringExtra("contact_name");

        if (originalContactNumber == null || originalContactNumber.isEmpty()) {
            Toast.makeText(this, "Contact number missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Normalize the contact number once at the beginning for consistent comparisons
        contactNumber = normalizeNumber(originalContactNumber);

        if(contactName != null && !contactName.isEmpty()){
            getSupportActionBar().setTitle(contactName);
        } else {
            getSupportActionBar().setTitle(originalContactNumber); // Use original if no name
        }
        getSupportActionBar().setSubtitle(originalContactNumber);

        Log.d("MessageActivity", "Normalized Contact Number: " + contactNumber); // Log for debugging

        recyclerView = findViewById(R.id.recyclerViewMessages);
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);

        messageList = new ArrayList<>();
        messageAdapter = new MessageAdapter(this, messageList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(messageAdapter);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                userAtBottom = !rv.canScrollVertically(1);
            }
        });

        if (!hasSmsPermissions()) {
            requestSmsPermissions();
        } else {
            loadMessages();
        }

        sendButton.setOnClickListener(v -> {
            String message = messageInput.getText().toString().trim();
            if (!TextUtils.isEmpty(message)) {
                showSimPickerAndSend(originalContactNumber, message); // Use original for sending
            } else {
                Toast.makeText(this, "Please enter message", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean hasSmsPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestSmsPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_PHONE_STATE
        }, REQUEST_SMS_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_SMS_PERMISSIONS && hasSmsPermissions()) {
            loadMessages();
        } else {
            Toast.makeText(this, "SMS permissions are required", Toast.LENGTH_SHORT).show();
            finish();
        }
    }



            // Message Loading and Filtering

    private void loadMessages() {
        messageList.clear();
        ContentResolver cr = getContentResolver();

        Uri inboxUri = Uri.parse("content://sms/inbox");
        Uri sentUri = Uri.parse("content://sms/sent");

        // Generate potential variations of the contact number for broader querying
        // This helps catch numbers stored in different formats in the SMS database
        List<String> possibleNumbers = generatePossibleNumberFormats(contactNumber);
        Log.d("MessageActivity", "Possible number formats for query: " + possibleNumbers.toString());

        // Construct a WHERE clause that checks for all possible variations
        // Example: "address LIKE ? OR address LIKE ? OR address LIKE ?"
        StringBuilder selectionBuilder = new StringBuilder("address LIKE ?");
        for (int i = 1; i < possibleNumbers.size(); i++) {
            selectionBuilder.append(" OR address LIKE ?");
        }
        String selection = selectionBuilder.toString();
        String[] selectionArgs = possibleNumbers.toArray(new String[0]);

        loadMessagesFromUri(cr, inboxUri, false, selection, selectionArgs);
        loadMessagesFromUri(cr, sentUri, true, selection, selectionArgs);

        // Sort messages by timestamp after loading all relevant ones
        Collections.sort(messageList, Comparator.comparingLong(MessageModel::getTimestamp));

        messageAdapter.notifyDataSetChanged();
        if (userAtBottom) {
            recyclerView.scrollToPosition(messageList.size() - 1);
        }
        Log.d("MessageActivity", "Total messages loaded: " + messageList.size());
    }

    private void loadMessagesFromUri(ContentResolver cr, Uri uri, boolean isSent, String selection, String[] selectionArgs) {
        // Query using LIKE operators to broadly match numbers
        Cursor cursor = null;
        try {
            cursor = cr.query(uri, null, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                int addressIdx = cursor.getColumnIndex("address");
                int bodyIdx = cursor.getColumnIndex("body");
                int dateIdx = cursor.getColumnIndex("date");

                do {
                    String address = cursor.getString(addressIdx);
                    String body = cursor.getString(bodyIdx);
                    long timestamp = cursor.getLong(dateIdx);

                    // No need for secondary isMatchingAddress here, as the LIKE query is broad
                    // enough for initial filtering, and we trust our number formats.
                    messageList.add(new MessageModel(body, timestamp, isSent));

                } while (cursor.moveToNext());
            }
        } catch (SecurityException e) {
            Log.e("MessageActivity", "Permission error reading SMS: " + e.getMessage());
            Toast.makeText(this, "Permission denied to read SMS.", Toast.LENGTH_SHORT).show();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    // This method generates variations of the contact number for robust querying
    private List<String> generatePossibleNumberFormats(String normalizedNumber) {
        List<String> formats = new ArrayList<>();
        // Add the normalized number itself (e.g., "03xxxxxxxxx")
        formats.add(normalizedNumber);

        // Add format without leading '0' (e.g., "3xxxxxxxxx")
        if (normalizedNumber.startsWith("0")) {
            formats.add(normalizedNumber.substring(1));
        }

        // Add format with '+92' (e.g., "+923xxxxxxxxx")
        if (normalizedNumber.startsWith("0")) {
            formats.add("+92" + normalizedNumber.substring(1));
        } else {
            formats.add("+92" + normalizedNumber);
        }

        // Add format with '92' (e.g., "923xxxxxxxxx")
        if (normalizedNumber.startsWith("0")) {
            formats.add("92" + normalizedNumber.substring(1));
        } else {
            formats.add("92" + normalizedNumber);
        }

        // Add formats with wildcards, for partial matches
        // Example: %3xxxxxxxxx%, %03xxxxxxxxx%, %+923xxxxxxxxx%
        for (int i = 0; i < formats.size(); i++) {
            String num = formats.get(i);
            // Add a format with '%' at start and end for broader LIKE matches
            if (!num.startsWith("%")) { // Avoid adding duplicates if already contains '%'
                formats.add("%" + num + "%");
            }
        }

        // Use a set to remove duplicates, then convert back to list
        return new ArrayList<>(new java.util.HashSet<>(formats));
    }


    private String normalizeNumber(String number) {
        if (number == null) return "";
        number = number.replaceAll("[^0-9+]", ""); // Remove all non-digit, non-plus characters

        // First, handle numbers starting with international prefixes
        if (number.startsWith("+92")) {
            return "0" + number.substring(3); // +92300 -> 0300
        } else if (number.startsWith("0092")) {
            return "0" + number.substring(4); // 0092300 -> 0300
        } else if (number.startsWith("92") && number.length() >= 11) { // 923001234567 (at least 11 digits for a valid Pak number: 92 + 9 digits)
            return "0" + number.substring(2); // 92300 -> 0300
        }
        // Then, ensure all numbers start with '0' if they are local Pakistani numbers
        else if (number.startsWith("0")) {
            return number; // Already starts with 0
        } else if (number.length() == 10 && !number.startsWith("0")) { // Assume a 10-digit number like 3001234567
            return "0" + number; // Prepend '0'
        }
        return number; // Return as is if it doesn't fit the above patterns (e.g., very short numbers, genuinely international numbers)
    }




           // ## SIM Selection and SMS Sending

    private void showSimPickerAndSend(String number, String message) {
        SubscriptionManager sm = SubscriptionManager.from(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "READ_PHONE_STATE permission is required to send SMS.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<SubscriptionInfo> sims = null;
        try {
            sims = sm.getActiveSubscriptionInfoList();
        } catch (SecurityException e) {
            Log.e("MessageActivity", "SecurityException: " + e.getMessage());
            Toast.makeText(this, "Permission denied to access SIM info.", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            return;
        }

        if (sims != null && sims.size() > 1) {
            String[] simNames = new String[sims.size()];
            for (int i = 0; i < sims.size(); i++) {
                simNames[i] = "SIM " + (i + 1) + " - " + sims.get(i).getDisplayName();
            }

            List<SubscriptionInfo> finalSims = sims;
            new AlertDialog.Builder(this)
                    .setTitle("Select SIM")
                    .setItems(simNames, (dialog, which) -> {
                        int subId = finalSims.get(which).getSubscriptionId();
                        sendSmsUsingSim(number, message, subId);
                    }).show();
        } else {
            // If only one SIM or no active SIMs (though latter should be rare on a phone)
            sendSmsUsingSim(number, message, -1); // -1 indicates default SIM
        }
    }

    private void sendSmsUsingSim(String number, String message, int subId) {
        try {
            SmsManager smsManager = (subId == -1)
                    ? SmsManager.getDefault()
                    : SmsManager.getSmsManagerForSubscriptionId(subId);

            smsManager.sendTextMessage(number, null, message, null, null);
            saveToSentBox(number, message); // Save the sent message to the database

            // Add the sent message to the RecyclerView immediately
            long now = System.currentTimeMillis();
            messageList.add(new MessageModel(message, now, true)); // 'true' for sent message
            messageAdapter.notifyItemInserted(messageList.size() - 1);
            messageInput.setText("");

            if (userAtBottom) {
                recyclerView.scrollToPosition(messageList.size() - 1);
            }
            Toast.makeText(this, "SMS sent!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error sending SMS: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            Log.e("MessageActivity", "Error sending SMS: " + e.getMessage());
        }
    }

    private void saveToSentBox(String number, String message) {
        try {
            ContentValues values = new ContentValues();
            values.put("address", number);
            values.put("body", message);
            values.put("date", System.currentTimeMillis());
            values.put("type", Telephony.Sms.MESSAGE_TYPE_SENT); // Mark as sent
            getContentResolver().insert(Uri.parse("content://sms/sent"), values);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("MessageActivity", "Error saving to sent box: " + e.getMessage());
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}