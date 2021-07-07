package com.siddhesh.heretest

import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.AdapterView.OnItemClickListener
import android.widget.ListPopupWindow
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.here.sdk.core.*
import com.here.sdk.core.errors.InstantiationErrorException
import com.here.sdk.gestures.TapListener
import com.here.sdk.mapviewlite.*
import com.here.sdk.mapviewlite.MapScene.LoadSceneCallback
import com.here.sdk.mapviewlite.PickMapItemsCallback
import com.here.sdk.routing.*
import com.here.sdk.search.*
import com.here.sdk.search.SearchCallback
import com.siddhesh.heretest.PlatformPositioningProvider.PlatformLocationListener
import com.siddhesh.heretest.databinding.ActivityMapsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.ArrayList


class MapsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapsBinding

    lateinit var mapScene: MapScene
    var maxItems = 30 //maximum search result count
    var searchOptions = SearchOptions(LanguageCode.EN_US, maxItems)
    lateinit var searchEngine: SearchEngine
    lateinit var mapActivityViewModel: MapActivityViewModel
    val sourceList: ArrayList<Place> = ArrayList<Place>()
    val destinationList: ArrayList<Place> = ArrayList<Place>()
    lateinit var sourcePlace: Place
    lateinit var destinationPlace: Place
    private var routingEngine: RoutingEngine? = null
    private lateinit var permissionsRequestor: PermissionsRequestor
    private var viewportGeoBox: GeoBox? = null
    private var isSourceSearch = true
    private var isSourceSelected = false
    private var isDestinationSelected = false
    private lateinit var platformPositioningProvider: PlatformPositioningProvider
    private lateinit var selectedGeoCoordinates: GeoCoordinates
    private lateinit var currentLocation: Location
    private lateinit var routeGeoPolyline: GeoPolyline
    private var restaurantMarkers = ArrayList<MapMarker>()
    private var restaurantList = ArrayList<String>()
    private var restaurantPlaceList = ArrayList<Place>()
    private lateinit var currentMarker: MapMarker
    private var sourceMarker: MapMarker? = null
    private var destinationMarker: MapMarker? = null
    private var routeMapPolyline: MapPolyline? = null
    private lateinit var sourcePopupList: ListPopupWindow
    private lateinit var destinationPopupList: ListPopupWindow
    private val searchKey = "restaurants"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_maps)
        mapActivityViewModel = MapActivityViewModel(application)
        binding.vm = mapActivityViewModel
        binding.lifecycleOwner = this

        initView(savedInstanceState)

        handleAndroidPermissions()
    }

    private fun handleAndroidPermissions() {
        permissionsRequestor = PermissionsRequestor(this)
        permissionsRequestor.request(object : PermissionsRequestor.ResultListener {
            override fun permissionsGranted() {
                loadMapScene()
            }

            override fun permissionsDenied() {
                Log.e("siddhesh", "Permissions denied by user.")
            }
        })
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        @NonNull permissions: Array<String>,
        @NonNull grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionsRequestor.onRequestPermissionsResult(requestCode, grantResults)
    }

    private fun initView(savedInstanceState: Bundle?) {
        binding.mapView.onCreate(savedInstanceState)
        platformPositioningProvider = PlatformPositioningProvider(this)
        sourcePopupList = ListPopupWindow(this)
        destinationPopupList = ListPopupWindow(this)
        try {
            searchEngine = SearchEngine()
            routingEngine = RoutingEngine()

        } catch (e: InstantiationErrorException) {
            throw RuntimeException("Initialization of SearchEngine failed: " + e.error.name)
        }

        mapActivityViewModel.source.observeForever {

            isSourceSelected = false
            isSourceSearch = true
            if (viewportGeoBox != null && !TextUtils.isEmpty(it) && it.length > 2) {
                searchEngine.search(
                    TextQuery(it, viewportGeoBox!!),
                    searchOptions,
                    querySearchCallback
                )
            }

        }
        mapActivityViewModel.destination.observeForever {
            isSourceSearch = false
            isDestinationSelected = false
            if (viewportGeoBox != null && !TextUtils.isEmpty(it) && it.length > 2) {
                searchEngine.search(
                    TextQuery(it, viewportGeoBox!!),
                    searchOptions,
                    querySearchCallback
                )
            }

        }

        mapActivityViewModel.currentLocation.observeForever {
            if (it != null) {
                selectedGeoCoordinates = GeoCoordinates(
                    it.latitude,
                    it.longitude,
                    it.altitude
                )
                addCurrentMapMarker(selectedGeoCoordinates)
                currentLocation = Location(selectedGeoCoordinates, Date())
            }
        }

    }

    private fun loadMapScene() {

        Log.d("siddhesh", "Checking loadMapScene : ")

//        binding.llLoading.visibility= View.VISIBLE
        platformPositioningProvider.startLocating(object : PlatformLocationListener {
            override fun onLocationUpdated(location: android.location.Location?) {
                binding.llLoading.visibility = View.GONE
                Log.d("siddhesh", "Checking onLocationUpdated location: " + location)
                mapActivityViewModel.currentLocation.value = location!!
                platformPositioningProvider.stopLocating()
                binding.icCurrentLocation.visibility = View.VISIBLE
            }


        })
        binding.mapView.mapScene.loadScene(MapStyle.NORMAL_DAY,
            LoadSceneCallback { errorCode ->
                if (errorCode == null) {
                    binding.mapView.camera.target = GeoCoordinates(20.5937, 78.9629) // Set India as default location
                    binding.mapView.camera.zoomLevel = 3.0
                    mapScene = binding.mapView.mapScene
                    viewportGeoBox = binding.mapView.camera.boundingBox
                    setTapGestureHandler()


                } else {
                    Log.d("Siddhesh", "onLoadScene failed: $errorCode")
                }
            })


    }


    private val querySearchCallback = SearchCallback { searchError, list ->
        if (searchError != null) {
            Log.d("Siddhesh", "Error: $searchError")
            return@SearchCallback
        }

        if (list != null) {
            if (isSourceSearch) {
                reset() //remove  polyline & clear list
                for (searchResult in list) {
                    sourceList.add(searchResult)
                    binding.mapView.camera.target = searchResult.geoCoordinates!!
                    Log.d("Siddhesh", "Search Source Values: " + searchResult.title)
                }
//                    if(!isSourceSelected){
                setSourcePopupList()
//                    }
            } else {
                destinationList.clear()
                for (searchResult in list) {
                    destinationList.add(searchResult)
                    Log.d("Siddhesh", "Search Destination Values: " + searchResult.title)
                }
//                    if(isDestinationSelected){
                setDestinationPopupList()
//                    }

            }
        } else {
            Toast.makeText(this, getString(R.string.no_place_found), Toast.LENGTH_SHORT).show()
            sourcePopupList.dismiss()
            destinationPopupList.dismiss()
        }


    }


    private fun setSourcePopupList() {

        sourcePopupList.anchorView = binding.etSource

        sourcePopupList.setAdapter(PlaceAdapter(this, sourceList, false))
        sourcePopupList.setOnItemClickListener(OnItemClickListener { parent, view, position, id ->

            isSourceSelected = true
            binding.etSource.setText(sourceList[position].title)
            sourcePlace = sourceList[position]
            addSourceMapMarker()
            sourcePopupList.dismiss()

        })

        if (sourcePopupList.isShowing) {
            sourcePopupList.dismiss()
        }
        sourcePopupList.show()
    }

    private fun setDestinationPopupList() {

        destinationPopupList.anchorView = binding.etDestination

        destinationPopupList.setAdapter(PlaceAdapter(this, destinationList, false))
        destinationPopupList.setOnItemClickListener(OnItemClickListener { parent, view, position, id ->
            binding.etDestination.setText(destinationList[position].title)
            isDestinationSelected = true
            destinationPlace = destinationList[position]
            addDestinationMapMarker()
            CoroutineScope(Dispatchers.Main).launch {
                getRoute()
            }
            destinationPopupList.dismiss()

        })

        if (destinationPopupList.isShowing) {
            destinationPopupList.dismiss()
        }
        destinationPopupList.show()
    }

    private fun getRoute() {

        val waypoints: ArrayList<Waypoint> = java.util.ArrayList()
        waypoints.add(Waypoint(sourcePlace.geoCoordinates!!))
        waypoints.add(Waypoint(destinationPlace.geoCoordinates!!))

        val carOptions = CarOptions()
        carOptions.routeOptions.optimizationMode =
            OptimizationMode.SHORTEST //to get less no of coordinates on polyline
        routingEngine!!.calculateRoute(waypoints, carOptions) { routingError, routes ->
            if (routingError == null) {
                val route = routes!![0]
                showRouteDetails(route)
                showRouteOnMap(route)
                searchAlongARoute(route)
            } else {
                Log.d("siddhesh", "Checking route error: " + routingError.name)

            }
        }
    }

    private fun showRouteDetails(route: Route) {
        val estimatedTravelTimeInSeconds = route.durationInSeconds.toLong()
        val lengthInMeters = route.lengthInMeters
        val routeDetails = ("Travel Time: " + formatTime(estimatedTravelTimeInSeconds)
                + ", Length: " + formatLength(lengthInMeters))

        Log.d("siddhesh", "Checking route details: " + routeDetails)

        Toast.makeText(this, routeDetails, Toast.LENGTH_LONG).show()
    }

    private fun showRouteOnMap(route: Route) {

        routeGeoPolyline = try {
            GeoPolyline(route.polyline)
        } catch (e: InstantiationErrorException) {
            e.printStackTrace()
            return
        }
        val mapPolylineStyle = MapPolylineStyle()
        mapPolylineStyle.setColor(0x00908AA0, PixelFormat.RGBA_8888)
        mapPolylineStyle.widthInPixels = 8.0
        routeMapPolyline = MapPolyline(routeGeoPolyline, mapPolylineStyle)
        binding.mapView.mapScene.removeMapPolyline(routeMapPolyline!!)
        binding.mapView.mapScene.addMapPolyline(routeMapPolyline!!)
        binding.mapView.camera.zoomLevel = 12.0


    }

    fun reset() {
        if (routeMapPolyline != null) {
            binding.mapView.mapScene.removeMapPolyline(routeMapPolyline!!)
        }
        restaurantMarkers.forEach {
            binding.mapView.mapScene.removeMapMarker(it)
        }
        restaurantMarkers.clear()
        restaurantPlaceList.clear()
        sourceList.clear()
        destinationList.clear()
    }

    private fun searchAlongARoute(route: Route) {
        binding.llLoading.visibility = View.VISIBLE
        binding.tvDesc.text = getString(R.string.fetching_restaurants)

        var count=20
        searchRestaurant(TextQuery(searchKey, route.polyline[0]))
        searchRestaurant(TextQuery(searchKey, route.polyline[route.polyline.size-1]))

        while (count<route.polyline.size-20){
            searchRestaurant(TextQuery(searchKey, route.polyline[count]))
            count+=20
        }

//        val textQueryRoute = TextQuery(searchKey, route.boundingBox)
//        val textQuerySourceAndRoute = TextQuery(searchKey, route.polyline.get(getRandomIndex(0, route.polyline.size / 2)))
//        val textQuerySource = TextQuery(searchKey, sourcePlace.geoCoordinates!!)
//        val textQueryDestination = TextQuery(searchKey, destinationPlace.geoCoordinates!!)
//       val textQueryBetweenDestinationAndRoute = TextQuery(searchKey, route.polyline.get(getRandomIndex(0, route.polyline.size / 2)))
//
//        searchRestaurant(textQueryRoute)
//        searchRestaurant(textQuerySource)
//        searchRestaurant(textQueryDestination)
//        searchRestaurant(textQuerySourceAndRoute)
//        searchRestaurant(textQueryBetweenDestinationAndRoute)

    }

    private fun getRandomIndex(start: Int, end: Int): Int {
        val r = Random()
        return r.nextInt(end - start) + start
    }

    private fun searchRestaurant(textQuery: TextQuery) {

        val searchOptions = SearchOptions(LanguageCode.EN_US, maxItems)

        searchEngine.search(textQuery, searchOptions,
            SearchCallback { searchError, items ->
                binding.llLoading.visibility = View.GONE
                if (searchError != null) {
                    if (searchError != SearchError.POLYLINE_TOO_LONG) {
                        Toast.makeText(
                            this,
                            getString(R.string.not_restaurant_found),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@SearchCallback
                }

                for (place in items!!) {
                    if (!restaurantList.contains(place.title)) {
                        restaurantList.add(place.title)
                        restaurantPlaceList.add(place)
                        addRestaurantMapMarker(place)
                        Log.d("Search", "Search along route found restaurant: " + place.title)
                    }
                }
            })
    }


    private fun addRestaurantMapMarker(restaurant: Place) {
        val restaurantMarker = MapMarker(restaurant.geoCoordinates!!)

        binding.mapView.mapScene.removeMapMarker(restaurantMarker)

        val mapImage = MapImageFactory.fromResource(resources, R.drawable.ic_restaurant)
        restaurantMarker.addImage(mapImage, MapMarkerImageStyle())
        addMetaDataToMarker(restaurantMarker, restaurant.title)

        binding.mapView.mapScene.addMapMarker(restaurantMarker)
        restaurantMarkers.add(restaurantMarker)
    }

    private fun addCurrentMapMarker(geoCoordinates: GeoCoordinates) {
        currentMarker = MapMarker(geoCoordinates)
        binding.mapView.mapScene.removeMapMarker(currentMarker)

        val mapImage =
            MapImageFactory.fromResource(resources, R.drawable.ic_current_location_marker)
        currentMarker.addImage(mapImage, MapMarkerImageStyle())
        addMetaDataToMarker(currentMarker, "Current Location")
        binding.mapView.mapScene.addMapMarker(currentMarker)
    }

    private fun addMetaDataToMarker(marker: MapMarker, data: String) {
        val metadata = Metadata()
        metadata.setString("name", data)
        marker.metadata = metadata

    }

    private fun addSourceMapMarker() {
        if (sourceMarker != null) {
            binding.mapView.mapScene.removeMapMarker(sourceMarker!!)
        }
        sourceMarker = MapMarker(sourcePlace.geoCoordinates!!)

        val mapImage = MapImageFactory.fromResource(resources, R.drawable.ic_marker)
        sourceMarker!!.addImage(mapImage, MapMarkerImageStyle())
        addMetaDataToMarker(sourceMarker!!, sourcePlace.title)
        binding.mapView.mapScene.addMapMarker(sourceMarker!!)
    }

    private fun addDestinationMapMarker() {

        if (destinationMarker != null) {
            binding.mapView.mapScene.removeMapMarker(destinationMarker!!)
        }
        destinationMarker = MapMarker(destinationPlace.geoCoordinates!!)

        val mapImage = MapImageFactory.fromResource(resources, R.drawable.ic_marker)
        destinationMarker!!.addImage(mapImage, MapMarkerImageStyle())
        addMetaDataToMarker(destinationMarker!!, destinationPlace.title)

        binding.mapView.mapScene.addMapMarker(destinationMarker!!)
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.mapView.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        platformPositioningProvider.stopLocating()

    }


    private fun formatTime(sec: Long): String? {
        val hours = sec / 3600
        val minutes = sec % 3600 / 60
        return String.format(Locale.getDefault(), "%02d:%02d", hours, minutes)
    }

    private fun formatLength(meters: Int): String? {
        val kilometers = meters / 1000
        val remainingMeters = meters % 1000
        return String.format(Locale.getDefault(), "%02d.%02d km", kilometers, remainingMeters)
    }

    private fun setTapGestureHandler() {
        binding.mapView.gestures.tapListener =
            TapListener { touchPoint -> pickMapMarker(touchPoint) }
    }

    private fun pickMapMarker(touchPoint: Point2D) {
        val radiusInPixel = 2.0
        binding.mapView.pickMapItems(touchPoint, radiusInPixel,
            PickMapItemsCallback { pickMapItemsResult ->
                if (pickMapItemsResult == null) {
                    return@PickMapItemsCallback
                }
                val topmostMapMarker =
                    pickMapItemsResult.topmostMarker ?: return@PickMapItemsCallback
                val metadata = topmostMapMarker.metadata
                if (metadata != null) {
                    Toast.makeText(this, metadata.getString("name"), Toast.LENGTH_LONG).show()
                }
            })
    }

    fun showRestaurantList(view: View) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.restaurant_along_route))

        builder.setNegativeButton(getString(R.string.cancel))
        { dialog, which -> dialog.dismiss() }

        builder.setAdapter(PlaceAdapter(this, restaurantPlaceList, true))
        { dialog, which ->
            binding.mapView.camera.target =restaurantPlaceList[which].geoCoordinates!!
            binding.mapView.camera.zoomLevel = 12.0

            dialog.dismiss()
        }

        builder.show()
    }

    fun moveToCurrentLocation(view: View) {
        binding.mapView.mapScene.loadScene(MapStyle.NORMAL_DAY,
            LoadSceneCallback { errorCode ->
                if (errorCode == null) {
                    binding.mapView.camera.target = selectedGeoCoordinates
                    binding.mapView.camera.zoomLevel = 12.0
                    mapScene = binding.mapView.mapScene
                    viewportGeoBox = binding.mapView.camera.boundingBox


                } else {
                    Log.d("Siddhesh", "onLoadScene failed: $errorCode")
                }
            })
    }


}