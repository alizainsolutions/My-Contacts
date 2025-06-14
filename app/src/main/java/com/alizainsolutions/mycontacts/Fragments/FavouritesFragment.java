package com.alizainsolutions.mycontacts.Fragments;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView; // Make sure this import is present
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alizainsolutions.mycontacts.Adapter.FavouriteContactAdapter;
import com.alizainsolutions.mycontacts.ContactDetailsActivity;
import com.alizainsolutions.mycontacts.Model.ContactModel;
import com.alizainsolutions.mycontacts.R;

import java.util.ArrayList;
import java.util.List;

public class FavouritesFragment extends Fragment implements FavouriteContactAdapter.OnFavouriteContactInteractionListener {

    private static final String TAG = "FavouritesFragment";
    private static final int READ_CONTACTS_PERMISSION_REQUEST_CODE = 300;
    private static final int WRITE_CONTACTS_PERMISSION_REQUEST_CODE = 200; // Define a request code for write permission

    private RecyclerView recyclerViewFavourites;
    private FavouriteContactAdapter favouriteContactAdapter;
    private List<ContactModel> favouriteContactList;
    private TextView tvNoFavoritesMessage;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_favourites, container, false);

        recyclerViewFavourites = view.findViewById(R.id.recyclerViewFavourites);
        recyclerViewFavourites.setLayoutManager(new LinearLayoutManager(getContext()));
        tvNoFavoritesMessage = view.findViewById(R.id.tv_no_favorites_message);

        favouriteContactList = new ArrayList<>();
        favouriteContactAdapter = new FavouriteContactAdapter(getContext(), favouriteContactList, this);
        recyclerViewFavourites.setAdapter(favouriteContactAdapter);

        Log.d(TAG, "onCreateView: Fragment created and adapter set.");

        if (hasReadContactsPermission()) {
            loadFavoriteContactsInBackground();
        } else {
            requestReadContactsPermission();
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: Fragment resumed.");
        // Reload contacts when fragment resumes to reflect changes (e.g., from ContactDetailsActivity)
        if (hasReadContactsPermission()) {
            loadFavoriteContactsInBackground();
        } else {
            Log.d(TAG, "onResume: Permission not granted, not reloading favorites.");
        }
    }

    private boolean hasReadContactsPermission() {
        if (getContext() == null) return false;
        return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestReadContactsPermission() {
        if (getActivity() != null) {
            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.READ_CONTACTS},
                    READ_CONTACTS_PERMISSION_REQUEST_CODE);
        } else {
            Log.e(TAG, "getActivity() is null, cannot request READ_CONTACTS permission.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == READ_CONTACTS_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "READ_CONTACTS permission granted.");
                loadFavoriteContactsInBackground();
            } else {
                Log.w(TAG, "READ_CONTACTS permission denied.");
                Toast.makeText(getContext(), "Permission denied to read contacts. Cannot show favorites.", Toast.LENGTH_LONG).show();
                // If permission is denied, ensure the list is empty and message is shown
                if (tvNoFavoritesMessage != null) {
                    tvNoFavoritesMessage.setVisibility(View.VISIBLE);
                    favouriteContactAdapter.updateContactList(new ArrayList<>()); // Clear list
                }
            }
        } else if (requestCode == WRITE_CONTACTS_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "WRITE_CONTACTS permission granted.");
                // User will need to re-attempt the action (e.g., click delete again)
                Toast.makeText(getContext(), "Permission granted. Please re-attempt the action.", Toast.LENGTH_SHORT).show();
            } else {
                Log.w(TAG, "WRITE_CONTACTS permission denied.");
                Toast.makeText(getContext(), "Permission denied to modify contacts. Cannot remove from favorites.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void loadFavoriteContactsInBackground() {
        if (getContext() == null) {
            Log.e(TAG, "Context is null, cannot load favorite contacts.");
            return;
        }
        new LoadFavoriteContactsTask(getContext().getContentResolver()).execute();
    }

    // --- AsyncTask for loading contacts in the background ---
    private class LoadFavoriteContactsTask extends AsyncTask<Void, Void, List<ContactModel>> {
        private ContentResolver contentResolver;

        public LoadFavoriteContactsTask(ContentResolver resolver) {
            this.contentResolver = resolver;
        }

        @Override
        protected List<ContactModel> doInBackground(Void... voids) {
            List<ContactModel> tempFavoriteList = new ArrayList<>();
            Uri uri = ContactsContract.Contacts.CONTENT_URI;
            String[] projection = new String[]{
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.HAS_PHONE_NUMBER,
                    ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                    ContactsContract.Contacts.STARRED
            };

            String selection = ContactsContract.Contacts.STARRED + " = ?";
            String[] selectionArgs = {"1"}; // 1 means starred

            Cursor cursor = null;
            try {
                // Query the main Contacts table
                cursor = contentResolver.query(uri, projection, selection, selectionArgs, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC");

                if (cursor != null && cursor.getCount() > 0) {
                    Log.d(TAG, "Favorite contacts cursor count: " + cursor.getCount());

                    // Get column indices once outside the loop for efficiency and safety
                    int idCol = cursor.getColumnIndex(ContactsContract.Contacts._ID);
                    int nameCol = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY);
                    int photoCol = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI);
                    int hasPhoneCol = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER);
                    int starredCol = cursor.getColumnIndex(ContactsContract.Contacts.STARRED);

                    while (cursor.moveToNext()) {
                        // Retrieve data safely, checking for -1 index
                        String id = (idCol != -1) ? cursor.getString(idCol) : null;
                        String name = (nameCol != -1) ? cursor.getString(nameCol) : "Unknown Name";
                        String photoUri = (photoCol != -1) ? cursor.getString(photoCol) : null;
                        int hasPhoneNumber = (hasPhoneCol != -1) ? cursor.getInt(hasPhoneCol) : 0;
                        int isStarred = (starredCol != -1) ? cursor.getInt(starredCol) : 0;

                        String number = "";
                        // Only query for phone number if contact has one and ID is valid
                        if (hasPhoneNumber > 0 && id != null) {
                            Cursor phoneCursor = null;
                            try {
                                // Specific projection for phone number
                                String[] phoneProjection = new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER};
                                phoneCursor = contentResolver.query(
                                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                        phoneProjection, // Corrected to use specific projection
                                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                                        new String[]{id},
                                        null
                                );
                                if (phoneCursor != null && phoneCursor.moveToFirst()) {
                                    int numberCol = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                                    number = (numberCol != -1) ? phoneCursor.getString(numberCol) : "No Number";
                                }
                            } finally {
                                if (phoneCursor != null) {
                                    phoneCursor.close();
                                }
                            }
                        }
                        tempFavoriteList.add(new ContactModel(id, name, number, photoUri, isStarred == 1));
                    }
                } else {
                    Log.d(TAG, "No favorite contacts found in query result.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error querying favorite contacts: " + e.getMessage(), e);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return tempFavoriteList;
        }

        @Override
        protected void onPostExecute(List<ContactModel> result) {
            if (favouriteContactAdapter != null) {
                favouriteContactAdapter.updateContactList(result);
                Log.d(TAG, "Favorite contacts loaded and adapter updated. Count: " + result.size());

                // Show/hide "No favorites found" message based on list emptiness
                if (tvNoFavoritesMessage != null) {
                    tvNoFavoritesMessage.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
                }
            }
        }
    }

    // --- OnFavouriteContactInteractionListener methods (from Adapter interface) ---
    @Override
    public void onContactClick(ContactModel contact) {
        // Implement what happens when a favorite contact is clicked (e.g., open ContactDetailsActivity)
        if (contact.getId() == null) {
            Toast.makeText(getContext(), "Cannot open details: Contact ID is missing.", Toast.LENGTH_SHORT).show();
            return;
        }
//        Toast.makeText(getContext(), "Clicked: " + contact.getName(), Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(getContext(), ContactDetailsActivity.class);
        intent.putExtra("id", contact.getId());
        intent.putExtra("name", contact.getName());
        intent.putExtra("number", contact.getPhoneNumber());
        intent.putExtra("photoUri", contact.getPhotoUri());
        startActivity(intent);
    }

    @Override
    public void onDeleteFavouriteContact(ContactModel contact, int position) {
        if (getContext() == null || contact.getId() == null) {
            Toast.makeText(getContext(), "Cannot remove from favorites: Invalid context or contact.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Prompt for confirmation (AlertDialog is good practice)
        new android.app.AlertDialog.Builder(getContext())
                .setTitle("Remove from Favorites")
                .setMessage("Are you sure you want to remove " + contact.getName() + " from your favorites?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    // Call the method to un-favorite the contact
                    // Pass 'true' for isCurrentlyFavorite because we are removing it from favorites
                    toggleContactFavoriteStatus(contact.getId(), true, position);
                })
                .setNegativeButton("No", null)
                .show();
    }

    @Override
    public void onFavouriteContactOptionsClick(ContactModel contact, View view, int position) {
        if (getContext() == null) return;

        PopupMenu popup = new PopupMenu(getContext(), view);
        popup.getMenuInflater().inflate(R.menu.menu_favourite_contact_options, popup.getMenu());

        popup.setOnMenuItemClickListener(item -> {
            // Use if (item.getItemId() == R.id.your_menu_item_id) for modern Android
            if (item.getItemId() == R.id.action_delete_favorite) {
                onDeleteFavouriteContact(contact, position);
                return true;
            }
            return false;
        });
        popup.show();
    }


    private void toggleContactFavoriteStatus(String contactId, boolean isCurrentlyFavorite, int position) {
        if (getContext() == null || contactId == null || contactId.isEmpty()) {
            Toast.makeText(getContext(), "Cannot update favorite: Invalid context or contact ID.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(getContext(), "Permission to modify contacts denied.", Toast.LENGTH_SHORT).show();
            if (getActivity() != null) {
                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.WRITE_CONTACTS}, WRITE_CONTACTS_PERMISSION_REQUEST_CODE);
            }
            return;
        }

        ContentValues values = new ContentValues();
        // If it's currently favorite (isCurrentlyFavorite is true), set STARRED to 0 (unfavorite)
        values.put(ContactsContract.Contacts.STARRED, isCurrentlyFavorite ? 0 : 1);

        Uri contactUri = ContactsContract.Contacts.CONTENT_URI.buildUpon()
                .appendPath(contactId).build();

        try {
            int rowsAffected = getContext().getContentResolver().update(contactUri, values, null, null);
            if (rowsAffected > 0) {
                Toast.makeText(getContext(), "Removed from Favorites", Toast.LENGTH_SHORT).show();
                // Remove from adapter's list and notify RecyclerView for instant update
                if (position >= 0 && position < favouriteContactList.size()) {
                    favouriteContactList.remove(position);
                    favouriteContactAdapter.notifyItemRemoved(position);
                    // Notify range changed for subsequent items to correctly animate/reposition
                    favouriteContactAdapter.notifyItemRangeChanged(position, favouriteContactList.size());
                }
                // Update "No favorites found" message after removal
                if (tvNoFavoritesMessage != null) {
                    tvNoFavoritesMessage.setVisibility(favouriteContactList.isEmpty() ? View.VISIBLE : View.GONE);
                }
            } else {
                Toast.makeText(getContext(), "Failed to remove from favorites.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error removing contact from favorites: " + contactId, e);
            Toast.makeText(getContext(), "Error removing from favorites.", Toast.LENGTH_SHORT).show();
        }
    }
}