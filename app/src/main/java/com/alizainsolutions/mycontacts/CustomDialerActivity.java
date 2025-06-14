package com.alizainsolutions.mycontacts;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi; // Import for @RequiresApi
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.os.Build; // Import Build class
import android.annotation.SuppressLint; // Import for @SuppressLint
import java.lang.reflect.Method; // Import for Reflection

public class CustomDialerActivity extends AppCompatActivity {

    private static final String TAG = "CustomDialerActivity";

    private EditText dialedNumberEditText;
    private ImageView backspaceButton;
    private FloatingActionButton callFab;
    private ImageView sim1Button;
    private ImageView sim2Button;

    private TelecomManager telecomManager;
    private SubscriptionManager subscriptionManager;
    private List<SubscriptionInfo> activeSubscriptionInfoList;
    private Map<Integer, PhoneAccountHandle> simPhoneAccountHandles; // Maps SIM slot index to PhoneAccountHandle

    // ActivityResultLauncher for handling permissions
    private ActivityResultLauncher<String[]> requestPermissionsLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_dialer);
        Log.d(TAG, "onCreate: Activity created.");

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
           actionBar.setElevation(0f);
        }

        // Initialize views by finding them from the layout
        dialedNumberEditText = findViewById(R.id.dialedNumberEditText);
        backspaceButton = findViewById(R.id.backspaceButton);
        callFab = findViewById(R.id.callFab);
        sim1Button = findViewById(R.id.sim1Button);
        sim2Button = findViewById(R.id.sim2Button);

        // Initialize system services required for telephony features
        telecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
        subscriptionManager = (SubscriptionManager) getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        simPhoneAccountHandles = new HashMap<>(); // Initialize the map for SIM handles

        // Setup various UI components and their listeners
        setupDialpadEditText();
        setupDialpadButtons();
        setupBackspaceButton();
        setupCallButton();
        setupSimSelectionButtons();

        // Register for permission results using the new ActivityResult API
        requestPermissionsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    // Check if CALL_PHONE permission was granted
                    boolean callPhoneGranted = permissions.getOrDefault(Manifest.permission.CALL_PHONE, false);
                    // Check if READ_PHONE_STATE permission was granted (needed for SIM info)
                    boolean readPhoneStateGranted = permissions.getOrDefault(Manifest.permission.READ_PHONE_STATE, false);

                    Log.d(TAG, "Permissions callback: CALL_PHONE=" + callPhoneGranted + ", READ_PHONE_STATE=" + readPhoneStateGranted);

                    if (callPhoneGranted) {
//                        Toast.makeText(this, "Call permission granted.", Toast.LENGTH_SHORT).show();
                        callFab.setEnabled(true); // Enable call button if permission is granted
                    } else {
//                        Toast.makeText(this, "CALL_PHONE permission denied. Cannot make calls.", Toast.LENGTH_LONG).show();
                        callFab.setEnabled(false); // Disable call button if permission is denied
                    }

                    if (readPhoneStateGranted) {
//                        Toast.makeText(this, "Phone state permission granted.", Toast.LENGTH_SHORT).show();
                        updateSimButtonsVisibility(); // Update SIM buttons visibility and enabled state after permission
                    } else {
//                        Toast.makeText(this, "READ_PHONE_STATE permission denied. SIM selection may not work.", Toast.LENGTH_LONG).show();
                        sim1Button.setEnabled(false); // Disable SIM buttons
                        sim2Button.setEnabled(false);
                    }
                }
        );

        // Request necessary permissions when the activity is created
        requestRequiredPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: Activity resumed.");
        // Update SIM button visibility and enabled state whenever the activity resumes.
        // This is important if permissions are changed externally or SIM cards are inserted/removed.
        updateSimButtonsVisibility();
    }

    private void requestRequiredPermissions() {
        requestPermissionsLauncher.launch(new String[]{
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_PHONE_STATE // Required for dual-SIM detection
        });
    }


    private void setupDialpadEditText() {
        // Set input type to phone to enable numeric keypad features (though we prevent soft keyboard)
        dialedNumberEditText.setRawInputType(InputType.TYPE_CLASS_PHONE);
        dialedNumberEditText.setTextIsSelectable(true);
        // Prevent the soft keyboard from appearing
        dialedNumberEditText.setShowSoftInputOnFocus(false);
        dialedNumberEditText.requestFocus(); // Set focus to show cursor

        // Ensure cursor is always visible if EditText is focused
        dialedNumberEditText.setOnClickListener(v -> {
            dialedNumberEditText.requestFocus();
            // Move cursor to the end of the current text
            dialedNumberEditText.setSelection(dialedNumberEditText.getText().length());
        });

        // Add a TextWatcher to dynamically show/hide the backspace button
        dialedNumberEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Show backspace button if there's any text, otherwise hide it
                if (s.length() > 0) {
                    backspaceButton.setVisibility(View.VISIBLE);
                } else {
                    backspaceButton.setVisibility(View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Initially hide backspace if no text is present
        backspaceButton.setVisibility(View.GONE);
    }


    private void setupDialpadButtons() {
        int[] buttonIds = {
                R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
                R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9,
                R.id.btnStar, R.id.btnHash
        };

        for (int id : buttonIds) {
            Button button = findViewById(id);
            if (button != null) {
                // Short click appends the digit
                button.setOnClickListener(v -> {
                    String digit = ((Button) v).getText().toString();
                    dialedNumberEditText.append(digit);
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); // Haptic feedback on tap
                });

                // Special long-press for '0' to input '+'
                if (id == R.id.btn0) {
                    button.setOnLongClickListener(v -> {
                        dialedNumberEditText.append("+");
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); // Haptic feedback on long press
                        return true; // Consume the long click event
                    });
                }
            }
        }
    }


    private void setupBackspaceButton() {
        // Short click: delete last character
        backspaceButton.setOnClickListener(v -> {
            Editable editable = dialedNumberEditText.getText();
            int length = editable.length();
            if (length > 0) {
                editable.delete(length - 1, length); // Delete the last character
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
        });

        // Long click: clear all text
        backspaceButton.setOnLongClickListener(v -> {
            dialedNumberEditText.setText(""); // Clear the entire EditText
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            return true; // Consume the long click event
        });
    }


    private void setupCallButton() {
        callFab.setOnClickListener(v -> {
            String phoneNumber = dialedNumberEditText.getText().toString();
            if (phoneNumber.trim().isEmpty()) {
//                Toast.makeText(this, "Please enter a number to call.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Check CALL_PHONE permission before attempting to make a call
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                makeCall(phoneNumber, -1); // -1 indicates using the default system handling for SIM selection
            } else {
//                Toast.makeText(this, "CALL_PHONE permission is required to make calls.", Toast.LENGTH_LONG).show();
                requestRequiredPermissions(); // Request permissions if they are not granted
            }
        });
    }

    private void setupSimSelectionButtons() {
        // Initially disable SIM buttons until permissions are confirmed and SIMs are detected
        sim1Button.setEnabled(false);
        sim2Button.setEnabled(false);

        // Listener for SIM 1 button
        sim1Button.setOnClickListener(v -> {
            String phoneNumber = dialedNumberEditText.getText().toString();
            if (phoneNumber.trim().isEmpty()) {
//                Toast.makeText(this, "Please enter a number to call.", Toast.LENGTH_SHORT).show();
                return;
            }
            // Check both CALL_PHONE and READ_PHONE_STATE permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                makeCall(phoneNumber, 0); // Use SIM Slot 0 (typically SIM 1)
            } else {
//                Toast.makeText(this, "Permissions for calls and phone state are required to select SIM.", Toast.LENGTH_LONG).show();
                requestRequiredPermissions(); // Request permissions if not granted
            }
        });

        // Listener for SIM 2 button
        sim2Button.setOnClickListener(v -> {
            String phoneNumber = dialedNumberEditText.getText().toString();
            if (phoneNumber.trim().isEmpty()) {
//                Toast.makeText(this, "Please enter a number to call.", Toast.LENGTH_SHORT).show();
                return;
            }
            // Check both CALL_PHONE and READ_PHONE_STATE permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                makeCall(phoneNumber, 1); // Use SIM Slot 1 (typically SIM 2)
            } else {
//                Toast.makeText(this, "Permissions for calls and phone state are required to select SIM.", Toast.LENGTH_LONG).show();
                requestRequiredPermissions(); // Request permissions if not granted
            }
        });

        // Call this method initially to set the correct visibility and state based on current conditions
        updateSimButtonsVisibility();
    }


    private void updateSimButtonsVisibility() {
        // If READ_PHONE_STATE permission is not granted, hide and disable SIM buttons
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_PHONE_STATE permission not granted, SIM buttons disabled and hidden.");
            sim1Button.setVisibility(View.GONE);
            sim2Button.setVisibility(View.GONE);
            sim1Button.setEnabled(false);
            sim2Button.setEnabled(false);
            return; // Exit early as we cannot get SIM info
        }

        // Get active subscription info list. This might throw SecurityException if permission is just revoked.
        activeSubscriptionInfoList = null;
        try {
            if (subscriptionManager != null) {
                activeSubscriptionInfoList = subscriptionManager.getActiveSubscriptionInfoList();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException getting active subscription info, SIM buttons disabled: " + e.getMessage());
            activeSubscriptionInfoList = null; // Ensure list is null if exception occurs
        }

        // Handle different scenarios based on the number of active SIMs
        if (activeSubscriptionInfoList == null || activeSubscriptionInfoList.isEmpty()) {
            Log.d(TAG, "No active SIMs detected. Hiding SIM buttons.");
            sim1Button.setVisibility(View.GONE);
            sim2Button.setVisibility(View.GONE);
            sim1Button.setEnabled(false);
            sim2Button.setEnabled(false);
        } else if (activeSubscriptionInfoList.size() == 1) {
            Log.d(TAG, "One active SIM detected.");
            sim1Button.setVisibility(View.VISIBLE); // Show SIM1 button
            sim1Button.setEnabled(true); // Enable SIM1 button
            // Optionally, label SIM1 button with carrier name
            sim1Button.setContentDescription("Call with " + activeSubscriptionInfoList.get(0).getCarrierName() + " (SIM 1)");
            sim2Button.setVisibility(View.GONE); // Hide SIM2 button if only one SIM
            sim2Button.setEnabled(false);

            // Corrected: Call helper method for API 28+ specific logic if applicable
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // Android 9.0 (Pie)
                // Pass the single SubscriptionInfo to the API 28+ helper
                processSubscriptionInfoPieAndAbove(activeSubscriptionInfoList.get(0));
            } else {
                Log.w(TAG, "getPhoneAccountHandle() requires API 28+. Explicit SIM selection for SIM 1 not fully supported on this device.");
            }

        } else { // activeSubscriptionInfoList.size() >= 2, assuming dual-SIM
            Log.d(TAG, activeSubscriptionInfoList.size() + " active SIMs detected (dual SIM or more). Showing both SIM buttons.");
            sim1Button.setVisibility(View.VISIBLE);
            sim2Button.setVisibility(View.VISIBLE);
            sim1Button.setEnabled(true);
            sim2Button.setEnabled(true);

            // Populate PhoneAccountHandles and set content descriptions for each active SIM
            simPhoneAccountHandles.clear(); // Clear previous handles
            for (SubscriptionInfo info : activeSubscriptionInfoList) {
                // Corrected: Call helper method for API 28+ specific logic if applicable
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // Android 9.0 (Pie)
                    processSubscriptionInfoPieAndAbove(info);
                } else {
                    Log.w(TAG, "getPhoneAccountHandle() requires API 28+. Explicit SIM selection not fully supported on this device.");
                    // For older devices, the simPhoneAccountHandles map will remain empty,
                    // and makeCall(phoneNumber, simSlotIndex) will fall back to default.
                }

                // Set content descriptions for accessibility and user info
                if (info.getSimSlotIndex() == 0) { // SIM slot 0 (typically SIM 1)
                    sim1Button.setContentDescription("Call with " + info.getCarrierName() + " (SIM 1)");
                } else if (info.getSimSlotIndex() == 1) { // SIM slot 1 (typically SIM 2)
                    sim2Button.setContentDescription("Call with " + info.getCarrierName() + " (SIM 2)");
                }
            }
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.P) // Corrected to API 28 (Pie)
    @SuppressLint("NewApi") // Added to suppress the "Cannot resolve method" error
    private void processSubscriptionInfoPieAndAbove(SubscriptionInfo info) {
        // This is the PhoneAccountHandle that identifies the SIM account.
        // --- START OF REFLECTION WORKAROUND ---
        PhoneAccountHandle phoneAccountHandle = null;
        try {
            // Get the method 'getPhoneAccountHandle' from the SubscriptionInfo class via reflection
            Method getPhoneAccountHandleMethod = info.getClass().getMethod("getPhoneAccountHandle");
            // Invoke the method on the 'info' object to get the PhoneAccountHandle
            phoneAccountHandle = (PhoneAccountHandle) getPhoneAccountHandleMethod.invoke(info);
        } catch (NoSuchMethodException e) {
            // Log this if the method doesn't exist (e.g., on older APIs where it shouldn't be called)
            Log.e(TAG, "Method getPhoneAccountHandle not found via reflection. This should only happen on API < 28: " + e.getMessage());
        } catch (Exception e) {
            // Catch other reflection-related exceptions (IllegalAccessException, InvocationTargetException)
            Log.e(TAG, "Error invoking getPhoneAccountHandle via reflection: " + e.getMessage());
        }
        // --- END OF REFLECTION WORKAROUND ---

        if (phoneAccountHandle != null) {
            // Store the PhoneAccountHandle itself in the map, using the SIM slot index as the key.
            // telecomManager.getPhoneAccount(phoneAccountHandle) would return a PhoneAccount object,
            // which contains more details, but the PhoneAccountHandle is what's needed for dialing.
            simPhoneAccountHandles.put(info.getSimSlotIndex(), phoneAccountHandle);
        } else {
            Log.w(TAG, "PhoneAccountHandle is null (possibly due to older API or no active handle) for SIM slot " + info.getSimSlotIndex() + " (Carrier: " + info.getCarrierName() + ")");
        }
    }


    private void makeCall(String phoneNumber, int simSlotIndex) {
        // Double-check CALL_PHONE permission right before initiating the call
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
//            Toast.makeText(this, "CALL_PHONE permission not granted. Cannot make call.", Toast.LENGTH_SHORT).show();
            requestRequiredPermissions(); // Prompt for permissions if missing
            return;
        }

        // Create the call Intent with the telephone URI
        Intent callIntent = new Intent(Intent.ACTION_CALL);
        // Use Uri.encode to handle special characters like # or * in the number string
        callIntent.setData(Uri.parse("tel:" + Uri.encode(phoneNumber)));

        // If a specific SIM slot is requested AND the API level supports explicit SIM selection
        if (simSlotIndex != -1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // Corrected to API 28 (Pie)
            // Retrieve the stored PhoneAccountHandle for the requested SIM slot
            PhoneAccountHandle phoneAccountHandle = simPhoneAccountHandles.get(simSlotIndex);
            if (phoneAccountHandle != null) {
                Log.d(TAG, "Attempting to call " + phoneNumber + " via SIM slot " + simSlotIndex + " (Account ID: " + phoneAccountHandle.getId() + ")");
                Bundle extras = new Bundle();
                // Attach the PhoneAccountHandle to the Intent to specify the SIM
                extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);
                callIntent.putExtra(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, extras);
                startActivity(callIntent); // Start the call
            } else {
                Log.w(TAG, "PhoneAccountHandle not found for SIM slot " + simSlotIndex + " or API < 28. Falling back to default dialer.");
//                Toast.makeText(this, "Could not use specific SIM. Dialing with default method.", Toast.LENGTH_LONG).show();
                startActivity(callIntent); // Fallback to default system behavior
            }
        } else {
            // If no specific SIM is chosen, or API level doesn't support explicit selection,
            // let the system handle it (e.g., show SIM selection dialog if dual SIM is available)
            Log.d(TAG, "Attempting to call " + phoneNumber + " via default system dialer/selection.");
            startActivity(callIntent); // Start the call with default handling
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.add_contact_option, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if(item.getItemId() == R.id.addDialedContact){
            String phoneNumber = dialedNumberEditText.getText().toString();
            if (!phoneNumber.trim().isEmpty()) {
                Intent intent = new Intent(this, CreateContactActivity.class);
                intent.putExtra("dialedPhoneNumber", phoneNumber);
                startActivity(intent);
                return true;
            }

        }
        return super.onOptionsItemSelected(item);
    }
}
