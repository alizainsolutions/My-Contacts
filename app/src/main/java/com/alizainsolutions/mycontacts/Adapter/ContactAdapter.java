package com.alizainsolutions.mycontacts.Adapter;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.alizainsolutions.mycontacts.ContactDetailsActivity;
import com.alizainsolutions.mycontacts.Model.ContactModel;
import com.alizainsolutions.mycontacts.R;
import com.bumptech.glide.Glide;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.nativead.MediaView;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;

import java.util.ArrayList;
import java.util.List;

public class ContactAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements Filterable {

    private static final int VIEW_TYPE_CONTACT = 0;
    private static final int VIEW_TYPE_AD = 1;

    private final Context context;
    private final List<Object> mixedList = new ArrayList<>();
    private final List<ContactModel> originalList;
    private List<ContactModel> filteredList;

    public ContactAdapter(Context context, List<ContactModel> contactList) {
        this.context = context;
        this.originalList = new ArrayList<>(contactList);
        this.filteredList = new ArrayList<>(contactList);
        setUpMixedList(filteredList);
    }

    public static class ContactViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView, ivOptions;
        TextView tvName, tvNumber;

        public ContactViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageView);
            ivOptions = itemView.findViewById(R.id.ivOptions);
            tvName = itemView.findViewById(R.id.tvName);
            tvNumber = itemView.findViewById(R.id.tvNumber);
        }
    }

    public static class AdViewHolder extends RecyclerView.ViewHolder {
        NativeAdView adView;

        public AdViewHolder(View itemView) {
            super(itemView);
            adView = itemView.findViewById(R.id.native_ad_view);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return mixedList.get(position) instanceof ContactModel ? VIEW_TYPE_CONTACT : VIEW_TYPE_AD;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_CONTACT) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_contact, parent, false);
            return new ContactViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_native_ad, parent, false);
            return new AdViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == VIEW_TYPE_CONTACT) {
            ContactModel contact = (ContactModel) mixedList.get(position);
            ContactViewHolder viewHolder = (ContactViewHolder) holder;

            viewHolder.tvName.setText(contact.getName());
            viewHolder.tvNumber.setText(contact.getPhoneNumber());

            if (contact.getPhotoUri() != null) {
                Glide.with(context).load(Uri.parse(contact.getPhotoUri()))
                        .placeholder(R.drawable.user)
                        .into(viewHolder.imageView);
            } else {
                viewHolder.imageView.setImageResource(R.drawable.user);
            }

            viewHolder.ivOptions.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(context, viewHolder.ivOptions);
                popup.inflate(R.menu.menu_options);
                popup.setOnMenuItemClickListener(item -> {
                    int currentPosition = holder.getAdapterPosition();
                    if (item.getItemId() == R.id.delete_contact && currentPosition != RecyclerView.NO_POSITION) {
                        ContactModel contactToDelete = (ContactModel) mixedList.get(currentPosition);
                        showDeleteDialog(contactToDelete, currentPosition);
                        return true;
                    }
                    return false;
                });
                popup.show();
            });

            viewHolder.itemView.setOnClickListener(v -> {
                int currentPosition = holder.getAdapterPosition();
                ContactModel clickedContact = (ContactModel) mixedList.get(currentPosition);
                Intent intent = new Intent(context, ContactDetailsActivity.class);
                intent.putExtra("name", clickedContact.getName());
                intent.putExtra("number", clickedContact.getPhoneNumber());
                intent.putExtra("photoUri", clickedContact.getPhotoUri());
                intent.putExtra("id", clickedContact.getId());
                context.startActivity(intent);
            });

        } else if (holder instanceof AdViewHolder) {
            AdViewHolder adHolder = (AdViewHolder) holder;
            loadNativeAd(adHolder.adView);
        }
    }

    private void loadNativeAd(NativeAdView adView) {
        AdLoader adLoader = new AdLoader.Builder(context, "ca-app-pub-3940256099942544/2247696110") // test ad unit
                .forNativeAd(nativeAd -> {
                    TextView headlineView = adView.findViewById(R.id.ad_headline);
                    headlineView.setText(nativeAd.getHeadline());
                    adView.setHeadlineView(headlineView);

                    MediaView mediaView = adView.findViewById(R.id.ad_media);
                    mediaView.setMediaContent(nativeAd.getMediaContent());
                    adView.setMediaView(mediaView);

                    ImageView iconView = adView.findViewById(R.id.ad_icon);
                    if (nativeAd.getIcon() != null) {
                        iconView.setImageDrawable(nativeAd.getIcon().getDrawable());
                        iconView.setVisibility(View.VISIBLE);
                    } else {
                        iconView.setVisibility(View.GONE);
                    }
                    adView.setIconView(iconView);

                    Button ctaButton = adView.findViewById(R.id.ad_call_to_action);
                    if (nativeAd.getCallToAction() != null) {
                        ctaButton.setText(nativeAd.getCallToAction());
                        ctaButton.setVisibility(View.VISIBLE);
                    } else {
                        ctaButton.setVisibility(View.GONE);
                    }
                    adView.setCallToActionView(ctaButton);

                    adView.setNativeAd(nativeAd);
                })
                .build();

        adLoader.loadAd(new AdRequest.Builder().build());
    }

    private void showDeleteDialog(ContactModel contact, int position) {
        new AlertDialog.Builder(context)
                .setTitle("Delete Contact")
                .setMessage("Are you sure you want to delete '" + contact.getName() + "'?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteContactFromDevice(contact.getId());
                    originalList.remove(contact);
                    filteredList.remove(contact);
                    setUpMixedList(filteredList);
                    notifyDataSetChanged();
                    Toast.makeText(context, "Contact deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteContactFromDevice(String contactId) {
        Uri contactUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId);
        ContentResolver cr = context.getContentResolver();
        cr.delete(contactUri, null, null);
    }

    @Override
    public int getItemCount() {
        return mixedList.size();
    }

    @Override
    public Filter getFilter() {
        return contactFilter;
    }

    private final Filter contactFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<ContactModel> resultList = new ArrayList<>();
            if (constraint == null || constraint.length() == 0) {
                resultList.addAll(originalList);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();
                for (ContactModel contact : originalList) {
                    if ((contact.getName() != null && contact.getName().toLowerCase().contains(filterPattern)) ||
                            (contact.getPhoneNumber() != null && contact.getPhoneNumber().toLowerCase().contains(filterPattern))) {
                        resultList.add(contact);
                    }
                }
            }
            FilterResults results = new FilterResults();
            results.values = resultList;
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            filteredList = (List<ContactModel>) results.values;
            setUpMixedList(filteredList);
            notifyDataSetChanged();
        }
    };

    public void updateContactList(List<ContactModel> newList) {
        originalList.clear();
        originalList.addAll(newList);
        filteredList = new ArrayList<>(newList);
        setUpMixedList(filteredList);
        notifyDataSetChanged();
    }

    private void setUpMixedList(List<ContactModel> contacts) {
        mixedList.clear();
        for (int i = 0; i < contacts.size(); i++) {
            mixedList.add(contacts.get(i));
//            if ((i + 1) % 10 == 0)
            if (i == 3) {
                mixedList.add(new Object()); // Placeholder for Ad
            }
        }
    }

    public ContactModel getContactAtPosition(int position) {
        Object item = mixedList.get(position);
        return item instanceof ContactModel ? (ContactModel) item : null;
    }
}
