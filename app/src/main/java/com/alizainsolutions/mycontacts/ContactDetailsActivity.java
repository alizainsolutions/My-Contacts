package com.alizainsolutions.mycontacts;

import android.Manifest;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;

import java.util.ArrayList;

public class ContactDetailsActivity extends AppCompatActivity {

    private static final String TAG = "ContactDetailsActivity";

    private ImageView contactPhoto;
    private TextView nameView, numberView;
    private EditText nameEdit, numberEdit;
    private Button updateButton;
    private ImageView editButton, favouriteIcon;
    private ImageView callButton, messageButton;

    private String contactName, contactNumber, contactPhotoUri;
    private String contactId; // This will store the ContactsContract.Contacts._ID

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_details);

        contactPhoto = findViewById(R.id.contactPhoto);
        nameView = findViewById(R.id.nameView);
        numberView = findViewById(R.id.numberView);
        nameEdit = findViewById(R.id.nameEdit);
        numberEdit = findViewById(R.id.numberEdit);
        updateButton = findViewById(R.id.updateButton);
        editButton = findViewById(R.id.editIcon);
        callButton = findViewById(R.id.callButton);
        messageButton = findViewById(R.id.messageButton);
        favouriteIcon = findViewById(R.id.favouriteIcon);

        // Hide edit fields and update button initially
        nameEdit.setVisibility(View.GONE);
        numberEdit.setVisibility(View.GONE);
        updateButton.setVisibility(View.GONE);

        // Get data from intent
        Intent intent = getIntent();
        contactId = intent.getStringExtra("id"); // Make sure you pass the contact ID
        contactName = intent.getStringExtra("name");
        contactNumber = intent.getStringExtra("number");
        contactPhotoUri = intent.getStringExtra("photoUri");

        messageButton.setOnClickListener(v -> {
            Intent intentMsg = new Intent(this, MessageActivity.class);
            intentMsg.putExtra("contact_name", contactName);
            intentMsg.putExtra("contact_number", contactNumber);
            intentMsg.putExtra("contact_image", contactPhotoUri); // Pass image URI if available
            startActivity(intentMsg);
        });

        // Set initial data to views
        nameView.setText(contactName);
        numberView.setText(contactNumber);
        if (contactPhotoUri != null && !contactPhotoUri.isEmpty()) {
            Glide.with(this).load(Uri.parse(contactPhotoUri)).placeholder(R.drawable.user).error(R.drawable.user).into(contactPhoto);
        } else {
            contactPhoto.setImageResource(R.drawable.user); // Default user image
        }

        // --- FAVORITE ICON LOGIC ---
        // 1. Initial state: Check if contact is favorite and set icon
        updateFavoriteIcon();

        // 2. Click listener: Toggle favorite status
        favouriteIcon.setOnClickListener(v -> {
            if (contactId != null) {
                toggleContactFavorite(contactId);
            } else {
                Toast.makeText(this, "Cannot set favorite: Contact ID is missing.", Toast.LENGTH_SHORT).show();
            }
        });
        // --- END FAVORITE ICON LOGIC ---


        editButton.setOnClickListener(v -> {
            nameView.setVisibility(View.GONE);
            numberView.setVisibility(View.GONE);
            nameEdit.setVisibility(View.VISIBLE);
            numberEdit.setVisibility(View.VISIBLE);
            updateButton.setVisibility(View.VISIBLE);

            nameEdit.setText(contactName);
            numberEdit.setText(contactNumber);
        });

        updateButton.setOnClickListener(v -> {
            String newName = nameEdit.getText().toString().trim();
            String newNumber = numberEdit.getText().toString().trim();

            if (newName.isEmpty() || newNumber.isEmpty()) {
                Toast.makeText(this, "Name and number cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            // Call method to update the contact
            updateContact(contactId, newName, newNumber);
        });

        callButton.setOnClickListener(v -> makePhoneCall(contactNumber));
    }



    private boolean isContactFavorite(String contactId) {
        if (contactId == null || contactId.isEmpty()) {
            return false;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            // Permission not granted, cannot check favorite status
            // You might want to request it here or handle this gracefully
            return false;
        }

        Uri contactUri = ContactsContract.Contacts.CONTENT_URI.buildUpon()
                .appendPath(contactId).build();

        String[] projection = new String[]{ContactsContract.Contacts.STARRED};
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(contactUri, projection, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int starredIndex = cursor.getColumnIndex(ContactsContract.Contacts.STARRED);
                if (starredIndex != -1) {
                    return cursor.getInt(starredIndex) == 1;
                }
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error checking favorite status for contact ID: " + contactId, e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return false;
    }


    private void toggleContactFavorite(String contactId) {
        if (contactId == null || contactId.isEmpty()) {
            Toast.makeText(this, "Cannot toggle favorite: Invalid contact ID.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission to modify contacts denied.", Toast.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_CONTACTS}, 2);
            return;
        }

        boolean isCurrentlyFavorite = isContactFavorite(contactId);
        int newStarredStatus = isCurrentlyFavorite ? 0 : 1; // 0 for not favorite, 1 for favorite

        ContentValues values = new ContentValues();
        values.put(ContactsContract.Contacts.STARRED, newStarredStatus);

        Uri contactUri = ContactsContract.Contacts.CONTENT_URI.buildUpon()
                .appendPath(contactId).build();

        int rowsAffected = 0;
        try {
            rowsAffected = getContentResolver().update(contactUri, values, null, null);
            if (rowsAffected > 0) {
                Toast.makeText(this, isCurrentlyFavorite ? "Removed from Favorites" : "Added to Favorites", Toast.LENGTH_SHORT).show();
                updateFavoriteIcon(); // Update the icon immediately
            } else {
                Toast.makeText(this, "Failed to update favorite status.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error toggling favorite status for contact ID: " + contactId, e);
            Toast.makeText(this, "Error updating favorite status.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Updates the favorite icon based on the current favorite status of the contact.
     */
    private void updateFavoriteIcon() {
        if (contactId != null && isContactFavorite(contactId)) {
            favouriteIcon.setImageResource(R.drawable.favourite_true);
        } else {
            favouriteIcon.setImageResource(R.drawable.favourite_false); // You need a favourite_false drawable
        }
    }


    private void makePhoneCall(String number) {
        Intent callIntent = new Intent(Intent.ACTION_CALL);
        callIntent.setData(Uri.parse("tel:" + number));

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            startActivity(callIntent);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CALL_PHONE}, 1);
        }
    }

    private void updateContact(String contactId, String newName, String newNumber) {
        if (contactId == null) {
            Toast.makeText(this, "Invalid contact ID", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission to modify contacts denied.", Toast.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_CONTACTS}, 2); // Request WRITE_CONTACTS
            return;
        }

        ArrayList<ContentProviderOperation> ops = new ArrayList<>();

        // Update name
        String whereName = ContactsContract.Data.CONTACT_ID + "=? AND " +
                ContactsContract.Data.MIMETYPE + "=?";
        String[] nameParams = new String[]{String.valueOf(contactId),
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE};
        ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                .withSelection(whereName, nameParams)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, newName)
                .build());

        // Update number
        String whereNumber = ContactsContract.Data.CONTACT_ID + "=? AND " +
                ContactsContract.Data.MIMETYPE + "=?";
        String[] numberParams = new String[]{String.valueOf(contactId),
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE};
        ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                .withSelection(whereNumber, numberParams)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, newNumber)
                .build());

        try {
            getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
            Toast.makeText(this, "Contact updated successfully", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK); // Indicate that data might have changed
            finish();
        } catch (RemoteException | OperationApplicationException e) {
            android.util.Log.e(TAG, "Update failed: " + e.getMessage(), e);
            Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) { // CALL_PHONE permission
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                makePhoneCall(contactNumber);
            } else {
                Toast.makeText(this, "CALL_PHONE permission denied", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == 2) { // WRITE_CONTACTS permission (for favorite toggle/update)
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            } else {
                Toast.makeText(this, "WRITE_CONTACTS permission denied. Cannot modify contacts.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}