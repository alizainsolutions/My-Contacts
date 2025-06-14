package com.alizainsolutions.mycontacts.Fragments;


import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alizainsolutions.mycontacts.Adapter.CallLogAdapter;

import com.alizainsolutions.mycontacts.CustomDialerActivity;
import com.alizainsolutions.mycontacts.MessageActivity;
import com.alizainsolutions.mycontacts.Model.CallLogModel;
import com.alizainsolutions.mycontacts.R;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class RecentsFragment extends Fragment implements CallLogAdapter.OnCallLogInteractionListener {

    private static final String TAG = "RecentFragment";
    private static final int CALL_LOG_PERMISSIONS_REQUEST_CODE = 200;

    private RecyclerView recyclerView;
    private CallLogAdapter callLogAdapter;
    private FloatingActionButton fabDialer;
    private TextView tvNoCallLogsMessage;
    private InterstitialAd interstitialAd;
    private boolean isAdLoaded = false;

    private ActivityResultLauncher<String[]> requestPermissionsLauncher;


    private static class LoadProgressUpdate {
        CallLogModel callLog;

        LoadProgressUpdate(CallLogModel callLog) {
            this.callLog = callLog;
        }
    }

    public RecentsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: Fragment created.");

        requestPermissionsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    boolean readGranted = permissions.getOrDefault(Manifest.permission.READ_CALL_LOG, false);
                    boolean writeGranted = permissions.getOrDefault(Manifest.permission.WRITE_CALL_LOG, false);
                    boolean allGranted = readGranted && writeGranted;

                    Log.d(TAG, "Permissions callback: READ_CALL_LOG=" + readGranted + ", WRITE_CALL_LOG=" + writeGranted);

                    if (allGranted) {
                        Log.d(TAG, "All required call log permissions granted. Loading call logs.");
                        // Clear adapter and start loading
                        if (callLogAdapter != null) {
                            callLogAdapter.updateCallLogList(new ArrayList<>()); // Clear adapter immediately
                        }
                        if (tvNoCallLogsMessage != null) {
                            tvNoCallLogsMessage.setVisibility(View.GONE); // Hide message when loading starts
                            tvNoCallLogsMessage.setText("Loading call logs..."); // Optional: show loading message
                        }
                        loadCallLogsInBackground();
                    } else {
                        Log.w(TAG, "Not all call log permissions granted.");
                        if (isAdded() && getContext() != null) {
                            Toast.makeText(getContext().getApplicationContext(), "Call Log permissions are required to view/manage recent calls.", Toast.LENGTH_LONG).show();
                        }
                        if (tvNoCallLogsMessage != null) {
                            tvNoCallLogsMessage.setVisibility(View.VISIBLE);
                            tvNoCallLogsMessage.setText("Permissions denied. Cannot show call logs.");
                            if (callLogAdapter != null) {
                                callLogAdapter.updateCallLogList(new ArrayList<>()); // Clear adapter if permissions are denied
                            }
                        }
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView: View created.");
        View view = inflater.inflate(R.layout.fragment_recents, container, false);

        recyclerView = view.findViewById(R.id.recyclerViewCallLogs);
        fabDialer = view.findViewById(R.id.fabDialer);
        tvNoCallLogsMessage = view.findViewById(R.id.tv_no_call_logs_message);

        if (getContext() != null) {
            callLogAdapter = new CallLogAdapter(getContext(), this);
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            recyclerView.setAdapter(callLogAdapter);
        } else {
            Log.e(TAG, "Context is null in onCreateView, cannot initialize adapter.");
        }

        if (hasCallLogPermissions()) {
            Log.d(TAG, "onCreateView: Permissions already granted. Loading call logs.");
            if (callLogAdapter != null) {
                callLogAdapter.updateCallLogList(new ArrayList<>()); // Clear adapter immediately
            }
            if (tvNoCallLogsMessage != null) {
                tvNoCallLogsMessage.setVisibility(View.GONE); // Hide message when loading starts
                tvNoCallLogsMessage.setText("Loading call logs...");
            }
            loadCallLogsInBackground();
        } else {
            Log.d(TAG, "onCreateView: Permissions not granted. Requesting permissions.");
            // Initial state: show message and clear adapter if no permissions
            if (tvNoCallLogsMessage != null) {
                tvNoCallLogsMessage.setVisibility(View.VISIBLE);
                tvNoCallLogsMessage.setText("Permissions required to show call logs. Please grant them.");
            }
            if (callLogAdapter != null) {
                callLogAdapter.updateCallLogList(new ArrayList<>());
            }
            requestCallLogPermissions(); // Request permissions if not granted
        }

        fabDialer.setOnClickListener(v -> openCustomDialer());
        getInterstitialAd(this);


        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: Fragment resumed.");

        if (hasCallLogPermissions()) {
            Log.d(TAG, "onResume: Permissions granted, reloading call logs.");
            if (callLogAdapter != null) {
                callLogAdapter.updateCallLogList(new ArrayList<>()); // Clear adapter immediately
            }
            if (tvNoCallLogsMessage != null) {
                tvNoCallLogsMessage.setVisibility(View.GONE); // Hide message when loading starts
                tvNoCallLogsMessage.setText("Loading call logs...");
            }
            loadCallLogsInBackground();
        } else {
            Log.d(TAG, "onResume: Permissions not granted, not reloading call logs.");
            if (tvNoCallLogsMessage != null) {
                tvNoCallLogsMessage.setVisibility(View.VISIBLE);
                tvNoCallLogsMessage.setText("Permissions denied. Cannot show call logs.");
                if (callLogAdapter != null) {
                    callLogAdapter.updateCallLogList(new ArrayList<>()); // Clear adapter
                }
            }
        }
    }

    private boolean hasCallLogPermissions() {
        Context currentContext = getContext();
        if (currentContext == null) {
            Log.w(TAG, "hasCallLogPermissions: Context is null.");
            return false;
        }
        boolean readGranted = ContextCompat.checkSelfPermission(currentContext, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED;
        boolean writeGranted = ContextCompat.checkSelfPermission(currentContext, Manifest.permission.WRITE_CALL_LOG) == PackageManager.PERMISSION_GRANTED;
        Log.d(TAG, "hasCallLogPermissions: READ_CALL_LOG=" + readGranted + ", WRITE_CALL_LOG=" + writeGranted);
        return readGranted && writeGranted;
    }

    private void requestCallLogPermissions() {
        requestPermissionsLauncher.launch(new String[]{
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_CALL_LOG
        });
    }

    private void loadCallLogsInBackground() {
        if (!isAdded() || getContext() == null) {
            Log.w(TAG, "loadCallLogsInBackground: Fragment not attached or context is null. Aborting load.");
            return;
        }
        if (callLogAdapter != null) {
            callLogAdapter.updateCallLogList(new ArrayList<>()); // Clear the adapter's list
        }
        new LoadCallLogsTask(getContext().getContentResolver(), getContext().getApplicationContext()).execute();
    }

    // --- MODIFIED AsyncTask for incremental loading ---
    private class LoadCallLogsTask extends AsyncTask<Void, LoadProgressUpdate, List<CallLogModel>> { // Reverted to use LoadProgressUpdate
        private ContentResolver contentResolver;
        private Context applicationContext;
        private List<CallLogModel> finalResultList; // To store the complete list for onPostExecute fallback/logging

        public LoadCallLogsTask(ContentResolver resolver, Context appContext) {
            this.contentResolver = resolver;
            this.applicationContext = appContext;
            this.finalResultList = new ArrayList<>();
        }

        @Override
        protected List<CallLogModel> doInBackground(Void... voids) {
            String[] projection = {
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.DATE,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.CACHED_PHOTO_URI,
                    CallLog.Calls.CACHED_LOOKUP_URI
            };

            String sortOrder = CallLog.Calls.DATE + " DESC"; // Still sort by most recent first
            Cursor cursor = null;
            try {
                Log.d(TAG, "Attempting to query call logs...");
                cursor = contentResolver.query(CallLog.Calls.CONTENT_URI, projection, null, null, sortOrder);

                if (cursor != null) {
                    Log.d(TAG, "Call log cursor count: " + cursor.getCount());
                    if (cursor.getCount() > 0) { // Check if there are any results

                        // Get column indices once before the loop for safety and efficiency
                        int idIdx = cursor.getColumnIndex(CallLog.Calls._ID);
                        int numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER);
                        int nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME);
                        int dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE);
                        int typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE);
                        int durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION);
                        int photoUriIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_PHOTO_URI);
                        int lookupUriIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_LOOKUP_URI);

                        while (cursor.moveToNext()) {
                            // Use the pre-fetched indices with null/default checks
                            String callLogId = (idIdx != -1) ? cursor.getString(idIdx) : null;
                            String number = (numberIdx != -1) ? cursor.getString(numberIdx) : "Unknown";
                            String name = (nameIdx != -1) ? cursor.getString(nameIdx) : null;
                            long date = (dateIdx != -1) ? cursor.getLong(dateIdx) : 0;
                            int type = (typeIdx != -1) ? cursor.getInt(typeIdx) : 0;
                            long duration = (durationIdx != -1) ? cursor.getLong(durationIdx) : 0;
                            String photoUri = (photoUriIdx != -1) ? cursor.getString(photoUriIdx) : null;
                            String lookupUriString = (lookupUriIdx != -1) ? cursor.getString(lookupUriIdx) : null;

                            String contactId = null;
                            if (lookupUriString != null && !lookupUriString.isEmpty()) {
                                try {
                                    Uri lookupUri = Uri.parse(lookupUriString);
                                    try (Cursor contactCursor = contentResolver.query(lookupUri, new String[]{ContactsContract.Contacts._ID}, null, null, null)) {
                                        if (contactCursor != null && contactCursor.moveToFirst()) {
                                            contactId = contactCursor.getString(contactCursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID));
                                        }
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error parsing lookup URI or querying contact ID for call log: " + lookupUriString + " - " + e.getMessage());
                                }
                            }
                            CallLogModel model = new CallLogModel(number, name, date, type, duration, photoUri, contactId, callLogId);

                            finalResultList.add(model); // Keep track of the full list (for debugging/fallback if needed)
                            publishProgress(new LoadProgressUpdate(model)); // Publish each model as it's fetched
                            Log.d(TAG, "doInBackground: Fetched & published call log -> Name: " + model.getName() + ", Number: " + model.getNumber());
                        }
                    } else {
                        Log.d(TAG, "Call log cursor count is 0. No call logs found.");
                    }
                }
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException reading call logs: " + e.getMessage(), e);
                if (applicationContext != null) {
                    new android.os.Handler(applicationContext.getMainLooper()).post(() ->
                            Toast.makeText(applicationContext, "Permission denied to read call logs.", Toast.LENGTH_LONG).show()
                    );
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading call logs: " + e.getMessage(), e);
                if (applicationContext != null) {
                    new android.os.Handler(applicationContext.getMainLooper()).post(() ->
                            Toast.makeText(applicationContext, "Error loading call logs. Please check permissions.", Toast.LENGTH_LONG).show()
                    );
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return finalResultList; // Return the full list (might be useful for onPostExecute, but UI is updated via progress)
        }

        // --- Reinstated and Modified onProgressUpdate METHOD ---
        @Override
        protected void onProgressUpdate(LoadProgressUpdate... values) {
            super.onProgressUpdate(values);
            if (isAdded() && callLogAdapter != null && values != null && values.length > 0) {
                CallLogModel newCallLog = values[0].callLog;

                // Always add to the bottom. Since doInBackground fetches in DESC order,
                // adding to the bottom means the list is built correctly (most recent first).
                callLogAdapter.addCallLogEntryAtBottom(newCallLog);
                Log.d(TAG, "onProgressUpdate: Added item to RecyclerView (bottom) -> Name: " + newCallLog.getName());

                // Hide the "no call logs" message as soon as the first item appears
                if (tvNoCallLogsMessage != null && tvNoCallLogsMessage.getVisibility() == View.VISIBLE) {
                    tvNoCallLogsMessage.setVisibility(View.GONE);
                }
            } else {
                Log.w(TAG, "onProgressUpdate: Fragment detached or adapter null, skipping UI update.");
            }
        }

        @Override
        protected void onPostExecute(List<CallLogModel> result) {
            Log.d(TAG, "onPostExecute: Loading finished. Total items fetched = " + (result != null ? result.size() : "null"));

            if (isAdded() && getActivity() != null) {
                // If the adapter is still empty after all items were processed by onProgressUpdate,
                // then no call logs were found at all.
                if (callLogAdapter != null && callLogAdapter.getItemCount() == 0) {
                    if (tvNoCallLogsMessage != null) {
                        tvNoCallLogsMessage.setVisibility(View.VISIBLE);
                        tvNoCallLogsMessage.setText("No call logs found.");
                    }
                } else if (tvNoCallLogsMessage != null) {
                    // Ensure the message is hidden if items were loaded
                    tvNoCallLogsMessage.setVisibility(View.GONE);
                }
                Log.d(TAG, "Call logs loading complete. Final RecyclerView item count: " + (callLogAdapter != null ? callLogAdapter.getItemCount() : "Adapter Null"));
            } else {
                Log.w(TAG, "LoadCallLogsTask: Fragment detached, not performing final UI check in onPostExecute.");
            }
        }
    }

    @Override
    public void onCallLogClick(CallLogModel callLog) {
        if (isAdded() && getContext() != null) {
            Intent intent = new Intent(getContext(), MessageActivity.class);
            intent.putExtra("contact_number", callLog.getNumber());
            intent.putExtra("contact_name", callLog.getName());
            intent.putExtra("contact_id", callLog.getContactId());
            intent.putExtra("contact_image", callLog.getPhotoUri());
            startActivity(intent);
        } else {
            Log.w(TAG, "onCallLogClick: Fragment detached or context null, cannot open MessageActivity.");
        }
    }

    @Override
    public void onCallLogOptionsClick(CallLogModel callLog, View view, int position) {
        if (isAdded() && getContext() != null) {
            PopupMenu popup = new PopupMenu(getContext(), view);
            popup.getMenuInflater().inflate(R.menu.menu_call_log_options, popup.getMenu());

            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.action_delete_call_log) {
                    showDeleteConfirmationDialog(callLog, position);
                    return true;
                }
                return false;
            });
            popup.show();
        } else {
            Log.w(TAG, "onCallLogOptionsClick: Fragment detached or context null, cannot show options menu.");
        }
    }

    private void showDeleteConfirmationDialog(CallLogModel callLog, int position) {
        if (isAdded() && getContext() != null) {
            new AlertDialog.Builder(getContext())
                    .setTitle("Delete Call Log Entry")
                    .setMessage("Are you sure you want to delete this call log entry for " +
                            (callLog.getName() != null && !callLog.getName().isEmpty() ? callLog.getName() : callLog.getNumber()) + "?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        deleteCallLogEntryInBackground(callLog.getCallLogId());
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            Log.w(TAG, "showDeleteConfirmationDialog: Fragment detached or context null, cannot show dialog.");
        }
    }

    private void deleteCallLogEntryInBackground(String callLogId) {
        if (!isAdded() || getContext() == null) {
            Log.w(TAG, "deleteCallLogEntryInBackground: Fragment not attached or context null.");
            return;
        }
        if (callLogId == null || callLogId.isEmpty()) {
            if (getContext() != null) {
                Toast.makeText(getContext().getApplicationContext(), "Cannot delete: Call log ID is missing.", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        final Context applicationContextForToast = getContext().getApplicationContext();

        new Thread(() -> {
            ContentResolver cr = getContext().getContentResolver();
            Uri deleteUri = Uri.withAppendedPath(CallLog.Calls.CONTENT_URI, callLogId);

            try {
                int deletedRows = cr.delete(deleteUri, null, null);
                if (deletedRows > 0) {
                    Log.d(TAG, "Deleted " + deletedRows + " call log entry with ID: " + callLogId);
                    if (isAdded() && getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (callLogAdapter != null) {
                                callLogAdapter.removeCallLogEntry(callLogId); // This method still works for single deletions
                                if (callLogAdapter.getItemCount() == 0 && tvNoCallLogsMessage != null) {
                                    tvNoCallLogsMessage.setVisibility(View.VISIBLE);
                                    tvNoCallLogsMessage.setText("No call logs found.");
                                }
                            }
                            Toast.makeText(applicationContextForToast, "Call log entry deleted", Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    Log.w(TAG, "No call log entries found for deletion with ID: " + callLogId);
                    if (isAdded() && getActivity() != null) {
                        getActivity().runOnUiThread(() -> Toast.makeText(applicationContextForToast, "Failed to delete call log entry.", Toast.LENGTH_SHORT).show());
                    }
                }
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException deleting call log: " + e.getMessage(), e);
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> Toast.makeText(applicationContextForToast, "Permission denied to delete call logs.", Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error deleting call log: " + e.getMessage(), e);
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> Toast.makeText(applicationContextForToast, "Error deleting call log.", Toast.LENGTH_LONG).show());
                }
            }
        }).start();
    }


    private void openCustomDialer() {
        if (isAdded() && getContext() != null) {
            startActivity(new Intent(requireContext(), CustomDialerActivity.class));

        } else {
            Log.w(TAG, "openCustomDialer: Fragment detached or context null, cannot open dialer.");
        }
    }



    public void getInterstitialAd(Fragment fragment) {
        if (fragment == null || fragment.getContext() == null || !fragment.isAdded()) {
            Log.w("AdHelper", "Fragment is null or not attached");
            return;
        }

        // Initialize AdMob (safe to call multiple times)
        MobileAds.initialize(fragment.getContext(), initializationStatus -> {});

        // Create and load ad
        AdRequest adRequest = new AdRequest.Builder().build();

        InterstitialAd.load(
                fragment.getContext(),
                "ca-app-pub-3940256099942544/1033173712", // ✅ Test Interstitial Ad ID
                adRequest,
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd ad) {
                        interstitialAd = ad;
                        isAdLoaded = true;
                        Log.d("AdHelper", "Interstitial ad loaded.");

                        // Show after 4 seconds
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            if (interstitialAd != null && fragment.isAdded()) {
                                interstitialAd.show(fragment.requireActivity());
                            }
                        }, 4000);
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        interstitialAd = null;
                        isAdLoaded = false;
                        Log.e("AdHelper", "Ad failed to load: " + loadAdError.getMessage());
                    }
                }
        );
    }

}