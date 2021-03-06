/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
 */
package fr.louisbl.cordova.gpslocation;

import java.util.ArrayList;
import java.util.List;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationListener;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

/*
 * This class is the interface to the Geolocation.  It's bound to the geo object.
 */

public class CordovaGPSLocation extends CordovaPlugin {

	private CordovaLocationListener mListener;
	private LocationManager mLocationManager;
	Location location;

	LocationManager getLocationManager() {
		return mLocationManager;
	}

	public static final int INIT_REQ_CODE = 1;

	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);
		if (cordova.hasPermission(READ))
			requestLocationUpdates();
		else
			getReadPermission(INIT_REQ_CODE);
	}

	void requestLocationUpdates() {
		LocationManager locationManager = (LocationManager) cordova.getActivity()
				.getSystemService(Context.LOCATION_SERVICE);
		List<String> providers = locationManager.getProviders(true);
		String provider = providers.contains(LocationManager.NETWORK_PROVIDER) ?
			LocationManager.NETWORK_PROVIDER:
			LocationManager.GPS_PROVIDER;
		Log.d(LocationUtils.APPTAG, "use provider: " + provider);
		locationManager.requestLocationUpdates(provider, 2000, 0, new LocationListener() {
			@Override
			public void onStatusChanged(String provider, int status, Bundle extras) {}
			@Override
			public void onProviderEnabled(String provider) {}
			@Override
			public void onProviderDisabled(String provider) {}
			@Override
			public void onLocationChanged(Location location) {
				Log.d(LocationUtils.APPTAG, "onLocationChanged");
				CordovaGPSLocation.this.location = location;
			}
		});
	}

	/**
	 * Executes the request and returns PluginResult.
	 *
	 * @param action
	 *            The action to execute.
	 * @param args
	 *            JSONArry of arguments for the plugin.
	 * @param callbackContext
	 *            The callback id used when calling back into JavaScript.
	 * @return True if the action was valid, or false if not.
	 * @throws JSONException
	 */
	private LocationManager locationManager;

	private Activity mActivity;

	private CallbackContext mCcallbackContext;
	private JSONArray mArgs;

	public boolean execute(final String action, final JSONArray args,
			final CallbackContext callbackContext) {
		this.mArgs = args;
		this.mCcallbackContext = callbackContext;
		mActivity = this.cordova.getActivity();
		if (action == null
				|| !action.matches("getLocation|addWatch|clearWatch")) {
			return false;
		}

		final String id = args.optString(0, "");

		if (action.equals("clearWatch")) {
			clearWatch(id);
			return true;
		}

//		if (isGPSdisabled()) {
//			fail(CordovaLocationListener.POSITION_UNAVAILABLE,
//					"GPS is disabled on this device.", callbackContext, false);
//			return true;
//		}

		locationManager = (LocationManager) cordova.getActivity()
				.getSystemService(Context.LOCATION_SERVICE);
		if (action.equals("getLocation")) {
			if (cordova.hasPermission(READ)) {
				getLastLocation(args, callbackContext);
				// search(executeArgs);
			} else {
				getReadPermission(SEARCH_REQ_CODE);
			}

		} else if (action.equals("addWatch")) {
			addWatch(id, callbackContext);
		}

		return true;
	}

	public void onRequestPermissionResult(int requestCode,
			String[] permissions, int[] grantResults) throws JSONException {

		if (requestCode == INIT_REQ_CODE){
			requestLocationUpdates();
			return;
		}
		if (cordova.hasPermission(READ)) {
			getLastLocation(mArgs, mCcallbackContext);
		} else {
			getLastLocation(mArgs, mCcallbackContext);
		}

	}

	protected void getReadPermission(int requestCode) {
		cordova.requestPermission(this, requestCode, READ);
	}

	public static final String READ = Manifest.permission.ACCESS_FINE_LOCATION;
	public static final int SEARCH_REQ_CODE = 0;

	/**
	 * Called when the activity is to be shut down. Stop listener.
	 */
	public void onDestroy() {
		if (mListener != null) {
			mListener.destroy();
		}
	}

	/**
	 * Called when the view navigates. Stop the listeners.
	 */
	public void onReset() {
		this.onDestroy();
	}

	public JSONObject returnLocationJSON(Location loc) {
		JSONObject o = new JSONObject();

		try {
			o.put("latitude", loc.getLatitude());
			o.put("longitude", loc.getLongitude());
			o.put("altitude", (loc.hasAltitude() ? loc.getAltitude() : null));
			o.put("accuracy", loc.getAccuracy());
			o.put("heading",
					(loc.hasBearing() ? (loc.hasSpeed() ? loc.getBearing()
							: null) : null));
			o.put("velocity", loc.getSpeed());
			o.put("timestamp", loc.getTime());
		} catch (JSONException e) {
			e.printStackTrace();
		}

		return o;
	}

	public void win(Location loc, CallbackContext callbackContext,
			boolean keepCallback) {
		PluginResult result = new PluginResult(PluginResult.Status.OK,
				this.returnLocationJSON(loc));
		result.setKeepCallback(keepCallback);
		callbackContext.sendPluginResult(result);
	}

	/**
	 * Location failed. Send error back to JavaScript.
	 *
	 * @param code
	 *            The error code
	 * @param msg
	 *            The error message
	 * @throws JSONException
	 */
	public void fail(int code, String msg, CallbackContext callbackContext,
			boolean keepCallback) {
		JSONObject obj = new JSONObject();
		String backup = null;
		try {
			obj.put("code", code);
			obj.put("message", msg);
		} catch (JSONException e) {
			obj = null;
			backup = "{'code':" + code + ",'message':'"
					+ msg.replaceAll("'", "\'") + "'}";
		}
		PluginResult result;
		if (obj != null) {
			result = new PluginResult(PluginResult.Status.ERROR, obj);
		} else {
			result = new PluginResult(PluginResult.Status.ERROR, backup);
		}

		result.setKeepCallback(keepCallback);
		callbackContext.sendPluginResult(result);
	}

	private boolean isGPSdisabled() {
		boolean gps_enabled;
		try {
			gps_enabled = mLocationManager
					.isProviderEnabled(LocationManager.GPS_PROVIDER);
		} catch (Exception ex) {
			ex.printStackTrace();
			gps_enabled = false;
		}

		return !gps_enabled;
	}

	private void showMsg(String msg) {
		Toast.makeText(mActivity, msg, Toast.LENGTH_SHORT).show();
	}

	private void getLastLocation(JSONArray args, CallbackContext callbackContext) {
		PluginResult result = location != null ?
			new PluginResult(PluginResult.Status.OK, returnLocationJSON(location)) :
			new PluginResult(PluginResult.Status.ERROR, new JSONObject());
		callbackContext.sendPluginResult(result);
	}

	private void clearWatch(String id) {
		getListener().clearWatch(id);
	}

	private void getCurrentLocation(CallbackContext callbackContext, int timeout) {
		getListener().addCallback(callbackContext, timeout);
	}

	private void addWatch(String timerId, CallbackContext callbackContext) {
		getListener().addWatch(timerId, callbackContext);
	}

	private CordovaLocationListener getListener() {
		if (mListener == null) {
			mListener = new CordovaLocationListener(this, LocationUtils.APPTAG);
		}
		return mListener;
	}
}
