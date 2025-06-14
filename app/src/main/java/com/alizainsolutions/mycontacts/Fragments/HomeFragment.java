package com.alizainsolutions.mycontacts.Fragments;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context; // Import Context for better null handling
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log; // Added for debugging
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alizainsolutions.mycontacts.Adapter.ContactAdapter; // Ensure this import is correct
import com.alizainsolutions.mycontacts.CreateContactActivity;
import com.alizainsolutions.mycontacts.MessageActivity;
import com.alizainsolutions.mycontacts.Model.ContactModel; // Ensure this import is correct
import com.alizainsolutions.mycontacts.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment"; // Define TAG for logging
    private static final int PERMISSIONS_REQUEST_READ_CONTACTS = 100;
    private RecyclerView recyclerView;
    private ContactAdapter contactAdapter;
    // contactList here will still be the master list that you pass to the adapter initially.
    // The adapter itself will manage the 'displayed' and 'full' lists internally.
    private List<ContactModel> contactList;
    private EditText searchBar;
    private FloatingActionButton addContact;

    private ActivityResultLauncher<Intent> addContactLauncher;
    private ActivityResultLauncher<Intent> editContactLauncher; // Although editContactLauncher is not used in this fragment

    public HomeFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView: View created."); // Added log
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        recyclerView = view.findViewById(R.id.recyclerView);
        searchBar = view.findViewById(R.id.searchBar);
        addContact = view.findViewById(R.id.fabAddContact);

        contactList = new ArrayList<>(); // Initialize the master list
        // Pass the master list to the adapter. The adapter now manages its own 'displayed' list.
        if (getContext() != null) { // Guard against null context
            contactAdapter = new ContactAdapter(getContext(), contactList);
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            recyclerView.setAdapter(contactAdapter);
        } else {
            Log.e(TAG, "Context is null in onCreateView, cannot initialize adapter.");
        }


        // --- Activity Result Launchers ---
        addContactLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // Check if fragment is still attached before accessing activity results or context
                    if (isAdded() && getActivity() != null) {
                        if (result.getResultCode() == getActivity().RESULT_OK) {
                            // After adding a contact, reload all contacts
                            loadContactsInBackground();
                            Toast.makeText(getContext(), "Contact Added Successfully!", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.w(TAG, "addContactLauncher: Fragment not attached, skipping result handling.");
                    }
                }
        );

        editContactLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // Check if fragment is still attached before accessing activity results or context
                    if (isAdded() && getActivity() != null) {
                        if (result.getResultCode() == getActivity().RESULT_OK) {
                            // After editing a contact, reload all contacts
                            loadContactsInBackground();
                            Toast.makeText(getContext(), "Contact Updated Successfully!", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.w(TAG, "editContactLauncher: Fragment not attached, skipping result handling.");
                    }
                }
        );

        // --- Permission Check and Load Contacts ---
        // Using getContext() instead of requireContext() for safety, and checking for null.
        if (getContext() != null) {
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS},
                        PERMISSIONS_REQUEST_READ_CONTACTS
                );
            } else {
                loadContactsInBackground();
            }
        } else {
            Log.e(TAG, "Context is null at permission check in onCreateView.");
            Toast.makeText(getContext().getApplicationContext(), "Context not available for permissions check.", Toast.LENGTH_LONG).show();
        }


        // --- Search Bar Text Watcher ---
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                // When text changes, filter the adapter's displayed list
                if (contactAdapter != null) { // Guard against null adapter
                    contactAdapter.getFilter().filter(s);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // --- Add Contact Floating Action Button ---
        addContact.setOnClickListener(v -> {
            if (isAdded() && getActivity() != null) { // Guard against fragment not attached
                Intent intent = new Intent(getActivity(), CreateContactActivity.class);
                addContactLauncher.launch(intent);
            } else {
                Log.w(TAG, "Add Contact: Fragment not attached, cannot launch CreateContactActivity.");
            }
        });

        // --- Swipe-to-Message Functionality ---
        ItemTouchHelper.SimpleCallback swipeToMessageCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false; // We don't want to support move operations
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && direction == ItemTouchHelper.RIGHT) {
                    // CRITICAL FIX: Ensure contactAdapter is not null before using it
                    if (contactAdapter == null) {
                        Log.e(TAG, "Error: contactAdapter is null during swipe.");
                        if (getContext() != null) {
                            Toast.makeText(getContext().getApplicationContext(), "Adapter not initialized. Cannot retrieve contact.", Toast.LENGTH_SHORT).show();
                        }
                        return;
                    }

                    // *** CRITICAL FIX: Get the ContactModel from the adapter's currently displayed list ***
                    ContactModel contact = contactAdapter.getContactAtPosition(position);

                    if (contact != null) {
                        Log.d(TAG, "Swiped Contact: Name=" + contact.getName() + ", Number=" + contact.getPhoneNumber());
                        if (isAdded() && getContext() != null) { // Guard against fragment not attached
                            Intent intent = new Intent(getContext(), MessageActivity.class);
                            intent.putExtra("contact_id", contact.getId());
                            intent.putExtra("contact_name", contact.getName());
                            intent.putExtra("contact_number", contact.getPhoneNumber());
                            intent.putExtra("contact_image", contact.getPhotoUri()); // Pass image URI if available
                            startActivity(intent);
                        } else {
                            Log.w(TAG, "Swiped: Fragment not attached, cannot launch MessageActivity.");
                        }
                    } else {
                        Log.e(TAG, "Error: Swiped contact is null at adapter position: " + position);
                        if (getContext() != null) {
                            Toast.makeText(getContext().getApplicationContext(), "Could not retrieve contact details.", Toast.LENGTH_SHORT).show();
                        }
                    }
                    // Important: Always notify the adapter to reset the swiped item's view
                    // This must be done on the main thread, and after checking adapter is not null.
                    if (isAdded() && getActivity() != null && contactAdapter != null) {
                        getActivity().runOnUiThread(() -> contactAdapter.notifyItemChanged(position));
                    }
                }
            }
        };

        new ItemTouchHelper(swipeToMessageCallback).attachToRecyclerView(recyclerView);

        return view;
    }

    // --- Load Contacts in Background Thread ---
    private void loadContactsInBackground() {
        new Thread(() -> {
            List<ContactModel> tempList = new ArrayList<>();
            // CRITICAL FIX: Use getContext() and check for null
            Context fragmentContext = getContext();
            if (fragmentContext == null) {
                Log.w(TAG, "Fragment context is null in background thread, aborting contact load.");
                return; // Abort if context is not available
            }
            ContentResolver cr = fragmentContext.getContentResolver();
            Uri uri = ContactsContract.Contacts.CONTENT_URI;
            // Query for contacts, ordering by display name
            Cursor cursor = null; // Initialize cursor to null
            try {
                cursor = cr.query(uri, null, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");

                if (cursor != null && cursor.getCount() > 0) {
                    while (cursor.moveToNext()) {
                        // Use getColumnIndex and check for -1 for robustness
                        int idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID);
                        int nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
                        int photoUriIndex = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI);
                        int hasPhoneNumberIndex = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER);

                        String id = (idIndex != -1) ? cursor.getString(idIndex) : null;
                        String name = (nameIndex != -1) ? cursor.getString(nameIndex) : "Unknown Contact";
                        String photoUri = (photoUriIndex != -1) ? cursor.getString(photoUriIndex) : null;

                        int hasPhoneNumber = (hasPhoneNumberIndex != -1) ? cursor.getInt(hasPhoneNumberIndex) : 0;

                        if (id != null && hasPhoneNumber > 0) {
                            // Query for phone numbers associated with the contact ID
                            Cursor phoneCursor = null; // Initialize phoneCursor to null
                            try {
                                phoneCursor = cr.query(
                                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                        null,
                                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                                        new String[]{id}, null);

                                if (phoneCursor != null) {
                                    // Iterate through phone numbers (a contact can have multiple)
                                    while (phoneCursor.moveToNext()) {
                                        int phoneNumberIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                                        String phoneNumber = (phoneNumberIndex != -1) ? phoneCursor.getString(phoneNumberIndex) : "No Number";
                                        // Add the contact with its first found phone number
                                        tempList.add(new ContactModel(id, name, phoneNumber, photoUri));
                                        break; // Only takes the first phone number. Remove this if you want to add multiple entries for a contact with multiple numbers.
                                    }
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error querying phone numbers for contact ID " + id + ": " + e.getMessage());
                            } finally {
                                if (phoneCursor != null) {
                                    phoneCursor.close();
                                }
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "No contacts found in cursor.");
                }
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException reading contacts: " + e.getMessage(), e);
                // Handle permission denied case on the UI thread
                if (fragmentContext != null) {
                    new android.os.Handler(fragmentContext.getMainLooper()).post(() ->
                            Toast.makeText(fragmentContext.getApplicationContext(), "Permission denied to read contacts.", Toast.LENGTH_LONG).show()
                    );
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading contacts: " + e.getMessage(), e);
                if (fragmentContext != null) {
                    new android.os.Handler(fragmentContext.getMainLooper()).post(() ->
                            Toast.makeText(fragmentContext.getApplicationContext(), "Error loading contacts. Please check permissions.", Toast.LENGTH_LONG).show()
                    );
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }


            // CRITICAL FIX: Update UI on the main thread only if fragment is still attached
            if (isAdded() && getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    // IMPORTANT: Pass the newly loaded list to the adapter's update method
                    if (contactAdapter != null) { // Guard against null adapter
                        contactAdapter.updateContactList(tempList);
                        Log.d(TAG, "Contacts loaded: " + tempList.size());
                    } else {
                        Log.e(TAG, "contactAdapter is null when trying to update UI.");
                    }
                });
            } else {
                Log.w(TAG, "Fragment not attached to activity, skipping UI update after contact load.");
            }
        }).start();
    }

    // --- Permission Request Result Handling ---
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); // Always call superclass method
        if (requestCode == PERMISSIONS_REQUEST_READ_CONTACTS) {
            // Ensure fragment is still attached before showing Toast or loading contacts
            if (isAdded() && getContext() != null) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // If permissions are granted, load contacts
                    loadContactsInBackground();
                } else {
                    // If permissions are denied, inform the user
                    Toast.makeText(getContext().getApplicationContext(), "Permission Denied to read contacts. Cannot display contacts.", Toast.LENGTH_LONG).show();
                }
            } else {
                Log.w(TAG, "onRequestPermissionsResult: Fragment not attached, skipping permission result handling.");
            }
        }
    }
}