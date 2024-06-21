package com.example.llmwithrag;

import android.content.Context;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.TextUtils;

import java.util.List;
import java.util.Locale;

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
}
