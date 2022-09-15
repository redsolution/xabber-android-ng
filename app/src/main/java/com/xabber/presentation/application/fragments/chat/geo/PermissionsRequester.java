package com.xabber.presentation.application.fragments.chat.geo;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;

import com.xabber.presentation.XabberApplication;

public class PermissionsRequester {

     public static boolean  hasLocationPermission(Activity activity) {
        return checkPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION);
    }

      public static boolean isPermissionGranted(int[] grantResults) {
        return grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
    }

      public static boolean requestLocationPermissionIfNeeded(Activity activity, int requestCode) {
        return checkAndRequestPermission(Manifest.permission.ACCESS_FINE_LOCATION, activity, requestCode);
    }


    private static boolean checkPermission(Activity activity, String permission) {
        final int permissionCheck = ContextCompat.checkSelfPermission(activity, permission);
        return permissionCheck == PackageManager.PERMISSION_GRANTED;
    }

     private static boolean checkAndRequestPermission(String permission, Activity activity, int requestCode) {
        if (checkPermission(activity, permission)) {
            return true;
        } else {
            activity.requestPermissions(new String[]{permission}, requestCode);
        }
        return false;
    }
}
