package com.example.android.demo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.JsonRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.android.demo.databinding.ActivityMapsBinding
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.Thread.sleep
import java.net.HttpURLConnection
import java.net.URL

class MapsActivity : AppCompatActivity() {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var userLocation: Location
    private lateinit var toLatLng: LatLng
    private lateinit var fromLatLng: LatLng
    private lateinit var binding: ActivityMapsBinding
    private lateinit var mapFragment: SupportMapFragment
    private lateinit var fromSearchView: SearchView
    private lateinit var toSearchView: SearchView

    val GOOGLE_MAP_KEY = "AIzaSyDiiCwOpDXDu34YkEmI0dBw9eozudS_r-c"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.

        mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        Dexter.withContext(applicationContext)
            .withPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
            .withListener(object : PermissionListener {
                override fun onPermissionGranted(p0: PermissionGrantedResponse?) {
                    getMyLocation()
                }

                override fun onPermissionDenied(p0: PermissionDeniedResponse?) {
//                    TODO("Not yet implemented")
                }

                override fun onPermissionRationaleShouldBeShown(
                    p0: PermissionRequest?,
                    p1: PermissionToken?
                ) {
                    p1?.continuePermissionRequest()
                }

            }).check()

        binding.btnNext.setOnClickListener(object : View.OnClickListener {
            override fun onClick(p0: View?) {
                val locationJsonObject = JSONObject()
                if (fromLatLng == null) {
                    Toast.makeText(
                        applicationContext,
                        "Enter your Origin Location",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
                if (toLatLng == null) {
                    Toast.makeText(
                        applicationContext,
                        "Enter your destination Location",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
                locationJsonObject.put("origin", "${fromLatLng.latitude},${fromLatLng.longitude}")
                locationJsonObject.put("destination", "${toLatLng.latitude},${toLatLng.longitude}")

                getDistance(locationJsonObject)

                // Getting URL to the Google Directions API

                val url = getDirectionsUrl(fromLatLng, toLatLng)

                //do in background

                try {

                    downloadUrl(url)
                } catch (e: Exception) {
                    Log.d("Background Task", e.toString())
                }

            }

        })

        // initializing our search view.

        fromSearchView = binding.searchviewFrom
        toSearchView = binding.searchviewTo


        fromSearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                // on below line we are getting the
                // location name from search view.
                val location: String = fromSearchView.getQuery().toString()

                // below line is to create a list of address
                // where we will store the list of all address.
                var addressList: List<Address>? = null

                // checking if the entered location is null or not.
                if (location != null || location == "") {
                    // on below line we are creating and initializing a geo coder.
                    val geocoder = Geocoder(this@MapsActivity)
                    try {
                        // on below line we are getting location from the
                        // location name and adding that location to address list.
                        addressList = geocoder.getFromLocationName(location, 1)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                    // on below line we are getting the location
                    // from our list a first position.
                    val address: Address = addressList!![0]

                    // on below line we are creating a variable for our location
                    // where we will add our locations latitude and longitude.
                    val latLng = LatLng(address.getLatitude(), address.getLongitude())

                    mMap.clear()
                    fromLatLng = latLng

                    // on below line we are adding marker to that position.
                    mMap.addMarker(MarkerOptions().position(latLng).title(location))

                    // below line is to animate camera to that position.
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 13f))
                }
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })

        toSearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                // on below line we are getting the
                // location name from search view.
                val location: String = toSearchView.getQuery().toString()

                // below line is to create a list of address
                // where we will store the list of all address.
                var addressList: List<Address>? = null

                // checking if the entered location is null or not.
                if (location != null || location == "") {
                    // on below line we are creating and initializing a geo coder.
                    val geocoder = Geocoder(this@MapsActivity)
                    try {
                        // on below line we are getting location from the
                        // location name and adding that location to address list.
                        addressList = geocoder.getFromLocationName(location, 3)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                    // on below line we are getting the location
                    // from our list a first position.
                    val address: Address = addressList!![0]

                    // on below line we are creating a variable for our location
                    // where we will add our locations latitude and longitude.
                    val latLng = LatLng(address.getLatitude(), address.getLongitude())

                    toLatLng = latLng
                    mMap.clear()

                    // on below line we are adding marker to that position.
                    mMap.addMarker(MarkerOptions().position(latLng).title(location))
                    mMap.addMarker(MarkerOptions().position(fromLatLng).title(location))

                    // below line is to animate camera to that position.
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 13f))
                }
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {

                return false
            }
        })


    }


    private fun getMyLocation() {
        var task: Task<Location>
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        task = fusedLocationClient.lastLocation
        task.addOnSuccessListener(object : OnSuccessListener<Location>, OnMapReadyCallback {
            override fun onSuccess(p0: Location?) {
                userLocation = p0 as Location;
                mapFragment.getMapAsync(this)
            }

            override fun onMapReady(p0: GoogleMap) {
                mMap = p0
                placeMarkerOnMap()
            }
        })
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */

    private fun placeCircle(latLng: LatLng) {
        mMap.addCircle(
            CircleOptions().center(latLng)
                .radius(15.0)
                .strokeColor(Color.CYAN)
                .strokeWidth(2f)
                .fillColor(Color.BLUE)
        )
    }

    private fun placeMarkerOnMap() {
        var curLatLong = LatLng(userLocation.latitude, userLocation.longitude)
        val markerOptions = MarkerOptions().position(curLatLong)
        fromLatLng = curLatLong
        mMap.addMarker(markerOptions)
        mMap.moveCamera(CameraUpdateFactory.newLatLng(curLatLong))
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(curLatLong, 15.5f))

        placeCircle(curLatLong)
    }

    @Throws(JSONException::class)
    private fun getDistance(locationJsonObject: JSONObject) {
        val queue = Volley.newRequestQueue(this@MapsActivity)
        val url = "https://maps.googleapis.com/maps/api/distancematrix/" +
                "json?origins=" + locationJsonObject.getString("origin") + "&destinations=" + locationJsonObject.getString(
            "destination"
        ) + "&mode=driving&" +
                "language=en-EN&sensor=false" + "&key=" + "AIzaSyDiiCwOpDXDu34YkEmI0dBw9eozudS_r-c"
        val stringRequest = StringRequest(
            Request.Method.GET, url,
            { response -> //mTextView.setText("Response is: " + response.substring(0, 500))

                showCost(JSONObject(response))

            }) {
            //mTextView.setText("That didn't work!")
            Toast.makeText(applicationContext, "api didnt work", Toast.LENGTH_SHORT).show()
        }
        queue.add(stringRequest)
    }

    private fun showCost(jsonObject: JSONObject) {
        var jRows: JSONArray
        var jElement: JSONArray
        var jDistace: JSONObject
        var distance: String
        try {
            jRows = jsonObject.getJSONArray("rows")
            /** Traversing all routes  */
            for (i in 0 until jRows.length()) {
                jElement = (jRows[i] as JSONObject).getJSONArray("elements")
                for (j in 0 until jElement.length()) {
                    jDistace = (jElement[j] as JSONObject).getJSONObject("distance")
                    distance = jDistace.getString("text")
                    showBottomSheet(distance.substring(0,distance.length-4).toFloat())
                }
            }
        } catch (e: Exception) {
            Toast.makeText(applicationContext, e.message, Toast.LENGTH_SHORT).show()
        }


    }

    private fun showBottomSheet(distance:Float) {
        val bottomSheetDialog:BottomSheetDialog= BottomSheetDialog(this)
        bottomSheetDialog.setContentView(R.layout.bottom_sheet_dialog_layout)
        var tvDistance:TextView?=null
        tvDistance=bottomSheetDialog.findViewById(R.id.tv_distance)

        var tvCost:TextView?=null
        tvCost=bottomSheetDialog.findViewById(R.id.tv_cost)
        if (tvDistance != null) {
            tvDistance.text="${distance} KM"
        }
        if (tvCost != null) {
            tvCost.text="Cost : ₹${distance*10}"
        }
        bottomSheetDialog.show()
    }


    private fun getDirectionsUrl(origin: LatLng, dest: LatLng): String {

        // Origin of route
        val str_origin = "origin=" + origin.latitude + "," + origin.longitude

        // Destination of route
        val str_dest = "destination=" + dest.latitude + "," + dest.longitude

        // Sensor enabled
        val sensor = "sensor=false"
        val mode = "mode=driving"
        val key = "key=AIzaSyDiiCwOpDXDu34YkEmI0dBw9eozudS_r-c"

        // Building the parameters to the web service
        val parameters = "$str_origin&$str_dest&$sensor&$mode&$key"

        // Output format
        val output = "json"

        // Building the url to the web service
        return "https://maps.googleapis.com/maps/api/directions/$output?$parameters"
    }

    @Throws(IOException::class)
    private fun downloadUrl(url: String) {

        val queue = Volley.newRequestQueue(this@MapsActivity)

        val jsonString = StringRequest(Request.Method.GET, url,
            { response ->
                drawDirection(response.toString())
            },
            { error ->
                Toast.makeText(applicationContext, error.message, Toast.LENGTH_SHORT).show()
            }
        )
        queue.add(jsonString)

    }

    private fun drawDirection(data: String) {
        val jObject: JSONObject
        var routes: List<List<HashMap<*, *>>>? = null
        try {
            jObject = JSONObject(data)
            val parser = DirectionsJSONParser()
            routes = parser.parse(jObject)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        var points: ArrayList<LatLng?>? = null
        var lineOptions: PolylineOptions? = null
        val markerOptions = MarkerOptions()
        for (i in routes!!.indices) {
            points = ArrayList()
            lineOptions = PolylineOptions()
            val path = routes[i]
            for (j in path.indices) {
                val point = path[j]
                val lat = point["lat"].toString().toDouble()
                val lng = point["lng"].toString().toDouble()
                val position = LatLng(lat, lng)
                points.add(position)
            }
            lineOptions.addAll(points)
            lineOptions.width(12f)
            lineOptions.color(Color.RED)
            lineOptions.geodesic(true)
        }

        // Drawing polyline in the Google Map for the i-th route
        mMap!!.addPolyline(lineOptions)
    }

}
