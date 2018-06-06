package com.joshblour.reactnativepermissions;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.PermissionChecker;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.WritableNativeMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class ReactNativePermissionsModule extends ReactContextBaseJavaModule {
  private final ReactApplicationContext reactContext;

  private static final String SOME_GRANTED = "SOME_GRANTED";
  private static final String ALL_GRANTED = "ALL_GRANTED";
  private static final String E_ACTIVITY_DOES_NOT_EXIST = "E_ACTIVITY_DOES_NOT_EXIST";
  private static final String E_PERMISSION_DENIED = "E_PERMISSION_DENIED";
  private static HashMap<Integer, Promise> requestPromises = new HashMap<>();
  private static HashMap<Integer, WritableNativeMap> requestResults = new HashMap<>();

  private static final int REQUEST_CODE_LOCATION_SETTINGS = 2;

  public enum RNType {
    LOCATION,
    CAMERA,
    MICROPHONE,
    CONTACTS,
    EVENT,
    STORAGE,
    PHOTO;
  }

  public ReactNativePermissionsModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  @Override
  public String getName() {
    return "ReactNativePermissions";
  }

  @ReactMethod
  public void getPermissionStatus(String permissionString, String nullForiOSCompat, Promise promise) {
    Activity currentActivity = getCurrentActivity();
    String permission = permissionForString(permissionString);

    if (currentActivity == null) {
      promise.reject(E_ACTIVITY_DOES_NOT_EXIST, "Activity doesn't exist");
      return;
    }
    // check if permission is valid
    if (permission == null) {
      promise.reject("unknown-permission", "ReactNativePermissions: unknown permission type - " + permissionString);
      return;
    }

//    int result = PermissionChecker.checkSelfPermission(this.reactContext, permission);
    int result = ContextCompat.checkSelfPermission(currentActivity, permission);
    switch (result) {
      case PermissionChecker.PERMISSION_DENIED:
        // PermissionDenied could also mean that we've never asked for permission yet.
        // Use shouldShowRequestPermissionRationale to determined which on it is.
        if (currentActivity != null) {
          boolean deniedOnce = ActivityCompat.shouldShowRequestPermissionRationale(currentActivity, permission);
          promise.resolve(deniedOnce ? "denied" : "undetermined");
        } else {
          promise.resolve("denied");
        }
        break;
      case PermissionChecker.PERMISSION_DENIED_APP_OP:
        promise.resolve("denied");
        break;
      case PermissionChecker.PERMISSION_GRANTED:
        promise.resolve("authorized");
        break;
      default:
        promise.resolve("undetermined");
        break;
    }
  }

  @ReactMethod
  public void requestPermission(final ReadableArray permsArray, String nullForiOSCompat, final Promise promise) {

    int reqCode = Integer.parseInt(nullForiOSCompat);


    Activity currentActivity = getCurrentActivity();
    if (currentActivity == null) {
      promise.reject(E_ACTIVITY_DOES_NOT_EXIST, "Activity doesn't exist");
      return;
    }

    WritableNativeMap q = new WritableNativeMap();

    List<String> permList = new ArrayList<>();
    for (int i=0; i<permsArray.size(); i++) {
      String permission = permissionForString(permsArray.getString(i));
      int hasPermission = ContextCompat.checkSelfPermission(currentActivity, permission);
      if(hasPermission != PackageManager.PERMISSION_GRANTED) {
        permList.add(permission); // list to request
        q.putBoolean(permission, false);
      } else {
        q.putBoolean(permission, true); // already granted
      }
    }

    if(permList.size() > 0)
    {
      requestPromises.put(reqCode, promise);
      requestResults.put(reqCode, q);

      String[] perms = permList.toArray(new String[permList.size()]);
      ActivityCompat.requestPermissions(currentActivity, perms, reqCode);
    }
    else
    {
      WritableNativeMap result = new WritableNativeMap();
      result.putString("code", ALL_GRANTED);
      result.putMap("result", q);

      promise.resolve(result);
    }
  }

  @ReactMethod
  public void canOpenSettings(Promise promise) {
    promise.resolve(true);
  }

  @ReactMethod
  public void openSettings() {
    final Intent i = new Intent();
    i.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
    i.addCategory(Intent.CATEGORY_DEFAULT);
    i.setData(Uri.parse("package:" + this.reactContext.getPackageName()));
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
    i.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
    this.reactContext.startActivity(i);
  }

  @ReactMethod
  public void isOpenGps(Promise promise) {
    LocationManager locationManager
            = (LocationManager) reactContext.getSystemService(Context.LOCATION_SERVICE);
    // 通过GPS卫星定位，定位级别可以精确到街（通过24颗卫星定位，在室外和空旷的地方定位准确、速度快）
    boolean gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

    promise.resolve(gps);
  }

  private String permissionForString(String permission) {
    switch (RNType.valueOf(permission.toUpperCase(Locale.ENGLISH))) {
      case LOCATION:
        return Manifest.permission.ACCESS_FINE_LOCATION;
      case CAMERA:
        return Manifest.permission.CAMERA;
      case MICROPHONE:
        return Manifest.permission.RECORD_AUDIO;
      case CONTACTS:
        return Manifest.permission.READ_CONTACTS;
      case EVENT:
        return Manifest.permission.READ_CALENDAR;
      case STORAGE:
      case PHOTO:
        return Manifest.permission.READ_EXTERNAL_STORAGE;
      default:
        return null;
    }
  }

  protected static void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    if(requestPromises.containsKey(requestCode) && requestResults.containsKey(requestCode))
    {
      Promise promise = requestPromises.get(requestCode);
      WritableNativeMap q = requestResults.get(requestCode);

      for(int i=0; i<permissions.length; i++) {
        q.putBoolean(permissions[i], grantResults[i] == PackageManager.PERMISSION_GRANTED);
      }

      int countGranted = 0;
      int totalPerms = 0;
      ReadableMapKeySetIterator itr = q.keySetIterator();
      while(itr.hasNextKey()) {
        String permKey = itr.nextKey();
        if(q.hasKey(permKey) && q.getBoolean(permKey) == true)
        {
          countGranted++;
        }
        totalPerms++;
      }

      if(countGranted > 0)
      {
        WritableNativeMap result = new WritableNativeMap();
        result.putString("code", countGranted == totalPerms ? ALL_GRANTED : SOME_GRANTED);
        result.putMap("result", q);

        promise.resolve(result); // some or all of permissions was granted...
      }
      else
      {
        promise.reject(E_PERMISSION_DENIED, "Permission request denied");
      }

      requestPromises.remove(requestCode);
      requestResults.remove(requestCode);
    }
  }

}
