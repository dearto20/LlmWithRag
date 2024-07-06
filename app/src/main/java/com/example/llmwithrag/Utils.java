package com.example.llmwithrag;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.TextUtils;

import com.example.llmwithrag.kg.IElement;
import com.example.llmwithrag.llm.CompletionMessage;
import com.example.llmwithrag.llm.CompletionRequest;
import com.example.llmwithrag.llm.CompletionResponse;
import com.example.llmwithrag.llm.OpenAiService;
import com.example.llmwithrag.llm.RetrofitClient;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class Utils {
    public static String getCoordinatesFromReadableAddress(Context context, String address) {
        if (TextUtils.isEmpty(address)) return "";
        Geocoder geocoder = new Geocoder(context, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocationName(address, 1);
            if (addresses == null || addresses.isEmpty()) return "";
            Address location = addresses.get(0);
            double scale = Math.pow(10, 4);
            double latitude = Math.round(location.getLatitude() * scale) / scale;
            double longitude = Math.round(location.getLongitude() * scale) / scale;
            return latitude + ", " + longitude;
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return "";
    }

    public static String getReadableAddressFromCoordinates(Context context, String location) {
        if (TextUtils.isEmpty(location)) return "";

        Geocoder geocoder = new Geocoder(context, Locale.getDefault());
        String result = "";

        try {
            String[] values = location.split(",");
            double latitude = Double.parseDouble(values[0].trim());
            double longitude = Double.parseDouble(values[1].trim());

            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                String countryName = address.getCountryName();
                result = address.getAddressLine(0);
                if (!TextUtils.isEmpty(countryName)) {
                    result = result.replace(countryName, "")
                            .replaceAll(",\\s*,", ",")
                            .replaceAll("^,\\s*", "")
                            .replaceAll(",\\s*$", "");
                }
                return result;
            }
        } catch (Throwable ignored) {
        }
        return location;
    }

    public static String getContactNameByEmail(Context context, String address) {
        Uri uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Email.CONTENT_FILTER_URI,
                Uri.encode(address));
        return getContactName(context, uri, address);
    }

    public static String getContactNameByPhoneNumber(Context context, String address) {
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(address));
        return getContactName(context, uri, address);
    }

    private static String getContactName(Context context, Uri uri, String address) {
        String[] projection = new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME};
        try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
                    return (nameIndex >= 0) ? cursor.getString(nameIndex) : "";
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return address;
    }

    public interface QueryResponseListener {
        void onSuccess(String result);

        void onError();

        void onFailure();
    }

    public static void performQuery(String query, QueryResponseListener listener) {
        List<CompletionMessage> messages = new ArrayList<>();
        messages.add(new CompletionMessage("user", query));
        CompletionRequest request = new CompletionRequest("gpt-4o", messages);
        OpenAiService service = RetrofitClient.getInstance().create(OpenAiService.class);
        Call<CompletionResponse> call = service.getCompletions(request);
        call.enqueue(new Callback<CompletionResponse>() {
            @Override
            public void onResponse(Call<CompletionResponse> call, Response<CompletionResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    listener.onSuccess(response.body().choices.get(0).message.content);
                } else {
                    listener.onError();
                }
            }

            @Override
            public void onFailure(Call<CompletionResponse> call, Throwable t) {
                listener.onFailure();
            }
        });
    }

    public static long getSharedPreferenceLong(Context context, String name, String key,
                                               long defaultValue) {
        SharedPreferences prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE);
        return prefs.getLong(key, defaultValue);
    }

    public static void setSharedPreferenceLong(Context context, String name, String key, long value) {
        SharedPreferences prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(key, value);
        editor.apply();
    }

    public static String generateTimeDeterministicQuery(String target) {
        StringBuilder sb = new StringBuilder();
        sb.append("\nCurrent time is ").append(getDate(System.currentTimeMillis()));
        sb.append("\nHere's the target sentence : \"").append(target).append("\"");
        sb.append("\nIf the sentence includes implicit day like \"오늘\", \"내일\", \"today\" or " +
                "\"tomorrow\" or any other similar ones as well, adjust it with explicit value to look like a natural sentence.");
        sb.append("\nIf the sentence includes implicit durations like \"지난 주말\" or any other similar ones as well," +
                "adjust it with the duration from explicit start date to explicit end date, using the expression \"사이\" or \"between\"" +
                "or something like that to look like a natural sentence.");
        sb.append("\nReturn only the sentence either adjusted or not.");
        return sb.toString();
    }

    public static String generateDescriptiveQuery(IElement element) {
        StringBuilder sb = new StringBuilder();
        sb.append("\nCurrent time is ").append(getDate(System.currentTimeMillis()));
        sb.append("\nHere's the target entity : \"").append(element.getDescription()).append("\"");
        sb.append("\nFlatten the entity with all including attributes to the natural one single sentence in Korean.");
        sb.append("\nMake sure NOT to omit any single attribute in generating the sentence.");
        sb.append("\nIf the sentence includes implicit day like \"오늘\", \"내일\", \"today\" or " +
                "\"tomorrow\" or any other similar ones as well, adjust it with explicit value to look like a natural sentence.");
        sb.append("\nIf the sentence includes implicit durations like \"지난 주말\" or any other similar ones as well," +
                "adjust it with the duration from explicit start date to explicit end date, using the expression \"사이\" or \"between\"" +
                "or something like that to look like a natural sentence.");
        sb.append("\nReturn only the sentence in a informative and declarative form, not in a interrogative or exclamatory either adjusted or not.");
        return sb.toString();
    }

    public static String getDate(long timestamp) {
        return (timestamp == -1) ? "" :
                new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(timestamp);
    }

    public static String getTime(long timestamp) {
        return (timestamp == -1) ? "" :
                new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(timestamp);
    }

    public static String getFileName(String path) {
        return new File(path).getName();
    }
}
