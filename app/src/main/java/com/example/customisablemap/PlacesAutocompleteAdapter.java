package com.example.customisablemap;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.model.TypeFilter;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsResponse;
import com.google.android.libraries.places.api.net.PlacesClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom adapter for handling Google Places API autocomplete predictions.
 */
public class PlacesAutocompleteAdapter extends ArrayAdapter<AutocompletePrediction> implements Filterable {
    private static final String TAG = "PlacesAutoAdapter";
    private final PlacesClient placesClient;
    private final AutocompleteSessionToken sessionToken;
    private List<AutocompletePrediction> predictions = new ArrayList<>();

    /**
     * Creates a new PlacesAutocompleteAdapter.
     */
    public PlacesAutocompleteAdapter(Context context) {
        super(context, android.R.layout.simple_dropdown_item_1line, new ArrayList<>());

        // Places client
        if (!Places.isInitialized()) {
            Places.initialize(context, context.getString(R.string.google_maps_key));
        }
        placesClient = Places.createClient(context);
        sessionToken = AutocompleteSessionToken.newInstance();
    }

    /**
     * Returns the number of predictions in the adapter.
     *
     * @return The number of place predictions returned
     */
    @Override
    public int getCount() {
        return predictions.size();
    }

    /**
     * Gets the prediction at the specified position.
     *
     * @param position The position of the prediction in the list
     * @return The AutocompletePrediction at the specified position
     */
    @Nullable
    @Override
    public AutocompletePrediction getItem(int position) {
        return predictions.get(position);
    }

    /**
     * Creates or reuses a view to display a place prediction.
     * @param position The position of the item in the list
     * @param convertView The recycled view
     * @param parent The parent ViewGroup
     * @return A View corresponding to the prediction at the specified position
     */
    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(android.R.layout.simple_dropdown_item_1line, parent, false);
        }

        AutocompletePrediction prediction = getItem(position);
        if (prediction != null) {
            TextView textView = (TextView) convertView;
            textView.setText(prediction.getFullText(null));
        }

        return convertView;
    }

    /**
     * Requests place predictions based on user input.
     *
     * @param constraint The text to use for the prediction request
     */
    public void getPredictions(String constraint) {
        // request creation to place predictions
        FindAutocompletePredictionsRequest request = FindAutocompletePredictionsRequest.builder()
                .setTypeFilter(TypeFilter.ADDRESS)
                .setSessionToken(sessionToken)
                .setQuery(constraint)
                .build();

        // submitting the request
        Task<FindAutocompletePredictionsResponse> task = placesClient.findAutocompletePredictions(request);

        task.addOnSuccessListener(response -> {
            // handle successful
            predictions = response.getAutocompletePredictions();
            notifyDataSetChanged();
        }).addOnFailureListener(exception -> {
            // handle failure
            if (exception instanceof ApiException) {
                ApiException apiException = (ApiException) exception;
                Log.e(TAG, "Place not found: " + apiException.getStatusCode());
            } else {
                Log.e(TAG, "Exception: " + exception.getMessage());
            }
        });
    }

    /**
     * Returns a filter that performs autocomplete requests.
     *
     * @return A Filter for this adapter
     */
    @NonNull
    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                // needed by Filter interface but doesn't need to be used
            }
        };
    }
}