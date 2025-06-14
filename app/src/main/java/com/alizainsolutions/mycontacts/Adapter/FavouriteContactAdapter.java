package com.alizainsolutions.mycontacts.Adapter;



import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.alizainsolutions.mycontacts.ContactDetailsActivity;
import com.alizainsolutions.mycontacts.Model.ContactModel; // Using the updated ContactModel
import com.alizainsolutions.mycontacts.R;
import com.bumptech.glide.Glide;

import java.util.List;

// This adapter reuses item_contact.xml for its layout
public class FavouriteContactAdapter extends RecyclerView.Adapter<FavouriteContactAdapter.FavouriteContactViewHolder> {

    private Context context;
    private List<ContactModel> favouriteContactList; // This will hold the favorite contacts
    private OnFavouriteContactInteractionListener listener;

    // Interface to communicate clicks back to the FavouritesFragment
    public interface OnFavouriteContactInteractionListener {
        void onContactClick(ContactModel contact);
        void onDeleteFavouriteContact(ContactModel contact, int position); // For removing from favorites
        void onFavouriteContactOptionsClick(ContactModel contact, View view, int position); // For the 3 dots menu
    }

    public FavouriteContactAdapter(Context context, List<ContactModel> favouriteContactList, OnFavouriteContactInteractionListener listener) {
        this.context = context;
        this.favouriteContactList = favouriteContactList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public FavouriteContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the same item_contact.xml layout
        View view = LayoutInflater.from(context).inflate(R.layout.item_contact, parent, false);
        return new FavouriteContactViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FavouriteContactViewHolder holder, int position) {
        if (position < 0 || position >= favouriteContactList.size()) {
            return; // Guard against invalid position
        }

        ContactModel contact = favouriteContactList.get(position);
        if (contact == null) {
            return; // Guard against null contact model
        }

        holder.tvName.setText(contact.getName());
        holder.tvNumber.setText(contact.getPhoneNumber());

        // Load photo using Glide
        String photoUriString = contact.getPhotoUri();
        if (photoUriString != null && !photoUriString.trim().isEmpty()) {
            try {
                Glide.with(context)
                        .load(Uri.parse(photoUriString))
                        .placeholder(R.drawable.user) // Default user icon
                        .error(R.drawable.user) // Error icon
                        .circleCrop() // Assuming you want circular images here too
                        .into(holder.imageView);
            } catch (IllegalArgumentException e) {
                android.util.Log.e("FavContactAdapter", "Invalid photo URI: " + photoUriString, e);
                holder.imageView.setImageResource(R.drawable.user); // Fallback on error
            }
        } else {
            holder.imageView.setImageResource(R.drawable.user); // Default user image
        }

        // Handle item click (e.g., open contact details)
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onContactClick(contact);
            }
        });

        // Handle options button click (3 dots menu)
        holder.ivOptions.setOnClickListener(v -> {
            if (listener != null) {
                listener.onFavouriteContactOptionsClick(contact, v, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return favouriteContactList != null ? favouriteContactList.size() : 0;
    }

    /**
     * Updates the adapter's list of favorite contacts and notifies RecyclerView of changes.
     * @param newList The new list of favorite contacts.
     */
    public void updateContactList(List<ContactModel> newList) {
        if (newList == null) {
            android.util.Log.e("FavContactAdapter", "updateContactList: newList is null!");
            return;
        }
        this.favouriteContactList.clear();
        this.favouriteContactList.addAll(newList);
        notifyDataSetChanged(); // Notify the adapter that the data set has changed
    }


    public ContactModel getContactAtPosition(int position) {
        if (favouriteContactList != null && position >= 0 && position < favouriteContactList.size()) {
            return favouriteContactList.get(position);
        }
        return null;
    }

    // ViewHolder class, same as your ContactAdapter's ViewHolder (using item_contact.xml views)
    public static class FavouriteContactViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView, ivOptions;
        TextView tvName, tvNumber;

        public FavouriteContactViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageView);
            ivOptions = itemView.findViewById(R.id.ivOptions);
            tvName = itemView.findViewById(R.id.tvName);
            tvNumber = itemView.findViewById(R.id.tvNumber);
        }
    }
}
