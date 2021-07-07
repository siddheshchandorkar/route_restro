package com.siddhesh.heretest

import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.AdapterView.OnItemClickListener
import android.widget.EditText
import android.widget.ListPopupWindow
import android.widget.Toast
import androidx.annotation.NonNull
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
import java.util.*
import kotlin.collections.ArrayList


class MapsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapsBinding

    //    val mapPolyline = createPolyline()
    lateinit var mapScene: MapScene
    var maxItems = 30 //maximum search result count
    var searchOptions = SearchOptions(LanguageCode.EN_US, maxItems)
    lateinit var searchEngine: SearchEngine
    lateinit var mapActivityViewModel: MapActivityViewModel
    val sourceList: ArrayList<Place> = ArrayList<Place>()
    val destinationList: ArrayList<Place> = ArrayList<Place>()
    lateinit var sourceCoordinates: GeoCoordinates
    lateinit var destinationCoordinates: GeoCoordinates
    private var routingEngine: RoutingEngine? = null
    private lateinit var permissionsRequestor: PermissionsRequestor
    private var viewportGeoBox: GeoBox? = null
    private var isSourceSearch = true
    private lateinit var platformPositioningProvider: PlatformPositioningProvider
    private lateinit var selectedGeoCoordinates: GeoCoordinates
    private lateinit var currentLocation: Location
    private lateinit var routeGeoPolyline: GeoPolyline
    private lateinit var currentMarker: MapMarker
    private lateinit var sourceMarker: MapMarker
    private lateinit var destinationMarker: MapMarker
    private lateinit var routeMapPolyline: MapPolyline


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

        try {
            searchEngine = SearchEngine()
            routingEngine = RoutingEngine()

        } catch (e: InstantiationErrorException) {
            throw RuntimeException("Initialization of SearchEngine failed: " + e.error.name)
        }

        mapActivityViewModel.source.observeForever {

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
                binding.mapView.mapScene.loadScene(MapStyle.NORMAL_DAY,
                    LoadSceneCallback { errorCode ->
                        if (errorCode == null) {
//                                binding.mapView.camera.target =  GeoCoordinates(52.530932, 13.384915)
                            binding.mapView.camera.target = selectedGeoCoordinates
                            binding.mapView.camera.zoomLevel = 12.0
                            mapScene = binding.mapView.mapScene
                            viewportGeoBox = binding.mapView.camera.boundingBox


//                    mapScene.addMapPolyline(mapPolyline!!)

                        } else {
                            Log.d("Siddhesh", "onLoadScene failed: $errorCode")
                        }
                    })
            }
        }

    }

    private fun loadMapScene() {
        // Load a scene from the SDK to render the map with a map style.
        Log.d("siddhesh", "Checking loadMapScene : ")

//        binding.llLoading.visibility= View.VISIBLE
        platformPositioningProvider.startLocating(object : PlatformLocationListener {
            override fun onLocationUpdated(location: android.location.Location?) {
                binding.llLoading.visibility = View.GONE
                Log.d("siddhesh", "Checking onLocationUpdated location: " + location)
                mapActivityViewModel.currentLocation.value = location!!
                platformPositioningProvider.stopLocating()

            }


        })
        binding.mapView.mapScene.loadScene(MapStyle.NORMAL_DAY,
            LoadSceneCallback { errorCode ->
                if (errorCode == null) {
                    binding.mapView.camera.target = GeoCoordinates(52.530932, 13.384915)
//                    binding.mapView.camera.target =  GeoCoordinates(it.latitude, it.longitude)
                    binding.mapView.camera.zoomLevel = 12.0
                    mapScene = binding.mapView.mapScene
                    viewportGeoBox = binding.mapView.camera.boundingBox
                    setTapGestureHandler()

//                    mapScene.addMapPolyline(mapPolyline!!)

                } else {
                    Log.d("Siddhesh", "onLoadScene failed: $errorCode")
                }
            })


    }


    private val querySearchCallback =
        SearchCallback { searchError, list ->
            if (searchError != null) {
                Log.d("Siddhesh", "Error: $searchError")
//                showDialog("Search", "Error: $searchError")
                return@SearchCallback
            }

            // If error is null, list is guaranteed to be not empty.
//            showDialog("Search", "Results: " + list!!.size)
            Log.d("Siddhesh", "Results: " + list!!.size)


            // Add new marker for each search result on map.
            if (isSourceSearch) {
                sourceList.clear()
                for (searchResult in list) {
                    sourceList.add(searchResult)
                    binding.mapView.camera.target = searchResult.geoCoordinates!!
                    // ...
                    Log.d("Siddhesh", "Search Source Values: " + searchResult.title)
                }


                setPopupList(sourceList, binding.etSource)
            } else {
                destinationList.clear()
                for (searchResult in list) {
                    destinationList.add(searchResult)
                    // ...
                    Log.d("Siddhesh", "Search Destination Values: " + searchResult.title)
                }

                setPopupList(destinationList, binding.etDestination)


            }


        }


    /*
    * To create polyline on map for given location coordinates
    * */
    private fun createPolyline(): MapPolyline? {
        val coordinates: ArrayList<GeoCoordinates> = ArrayList()
        coordinates.add(GeoCoordinates(52.53032, 13.37409))
        coordinates.add(GeoCoordinates(52.5309, 13.3946))
        coordinates.add(GeoCoordinates(52.53894, 13.39194))
        coordinates.add(GeoCoordinates(52.54014, 13.37958))
        val geoPolyline: GeoPolyline = try {
            GeoPolyline(coordinates)
        } catch (e: InstantiationErrorException) {
            // Less than two vertices.
            return null
        }
        val mapPolylineStyle = MapPolylineStyle()
        mapPolylineStyle.widthInPixels = 10.0
        mapPolylineStyle.setColor(-0xffff60, PixelFormat.RGBA_8888)
        return MapPolyline(geoPolyline, mapPolylineStyle)
    }

    /*
    * Search Restaurant for specific coordinates
    * */
    private fun searchForCategories() {
        val categoryList: MutableList<PlaceCategory> = ArrayList()
        categoryList.add(PlaceCategory(PlaceCategory.EAT_AND_DRINK_RESTAURANT))
//        val categoryQuery = CategoryQuery(categoryList, GeoCoordinates(52.520798, 13.409408))
        val categoryQuery = CategoryQuery(categoryList, GeoCoordinates(18.9943, 72.8213))
        val maxItems = 100
        val searchOptions = SearchOptions(LanguageCode.EN_US, maxItems)
        searchEngine.search(categoryQuery, searchOptions,
            SearchCallback { searchError, list ->
                if (searchError != null) {
                    Log.d("Siddhesh", "Search Error: $searchError")

                    return@SearchCallback
                }

                // If error is null, list is guaranteed to be not empty.
                val numberOfResults = "Search results: " + list!!.size + ". See log for details."
                Log.d("Siddhesh", numberOfResults)

                list.sortBy { it.title }
                for (i in 0 until list.size) {
                    if (i == 0) {
                        sourceCoordinates = list[i].geoCoordinates!!
//                        addMapMarker(sourceCoordinates, R.drawable.ic_marker)
                    } else {
                        destinationCoordinates = list[i].geoCoordinates!!

                    }

                }
//                for (searchResult in list) {
//                    val addressText = searchResult.address.addressText
//                    Log.d("Siddhesh", searchResult.title + "," + addressText)
//                }
            })
    }

    private fun setPopupList(placeList: ArrayList<Place>, editText: EditText) {

        val statusPopupList = ListPopupWindow(this)


        statusPopupList.anchorView = editText
        statusPopupList.setAdapter(PlaceAdapter(this, placeList))
        statusPopupList.setOnItemClickListener(OnItemClickListener { parent, view, position, id ->
            if (isSourceSearch) {
                binding.etSource.setText(placeList[position].title)
                sourceCoordinates = placeList[position].geoCoordinates!!
                addSourceMapMarker()

            } else {
                binding.etDestination.setText(placeList[position].title)
                destinationCoordinates = placeList[position].geoCoordinates!!
                addDestinationMapMarker()
                getRoute()

            }
            statusPopupList.dismiss()
        })

        statusPopupList.show()
    }

    private fun getRoute() {

        val waypoints: ArrayList<Waypoint> = java.util.ArrayList()
        waypoints.add(Waypoint(sourceCoordinates))
        waypoints.add(Waypoint(destinationCoordinates))

        routingEngine!!.calculateRoute(waypoints, CarOptions()) { routingError, routes ->
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
        // Show route as polyline.

        routeGeoPolyline = try {
            GeoPolyline(route.polyline)
        } catch (e: InstantiationErrorException) {
            // It should never happen that the route polyline contains less than two vertices.
            return
        }
        val mapPolylineStyle = MapPolylineStyle()
        mapPolylineStyle.setColor(0x00908AA0, PixelFormat.RGBA_8888)
        mapPolylineStyle.widthInPixels = 8.0
        routeMapPolyline = MapPolyline(routeGeoPolyline, mapPolylineStyle)
            binding.mapView.mapScene.removeMapPolyline(routeMapPolyline)

        binding.mapView.mapScene.addMapPolyline(routeMapPolyline)


    }

    // Perform a search for charging stations along the found route.
    private fun searchAlongARoute(route: Route) {
        // We specify here that we only want to include results
        // within a max distance of xx meters from any point of the route.
//        binding.llLoading.visibility=View.VISIBLE
        binding.tvDesc.text=getString(R.string.fetching_restaurants)
        val halfWidthInMeters = 500
        val routeCorridor = GeoCorridor(route.polyline, halfWidthInMeters)
        val textQuery = TextQuery("restaurants", routeCorridor, sourceCoordinates)
        val maxItems = 100
        val searchOptions = SearchOptions(LanguageCode.EN_US, maxItems)
        searchEngine.search(textQuery, searchOptions,
            SearchCallback { searchError, items ->
                if (searchError != null) {
                    if (searchError == SearchError.POLYLINE_TOO_LONG) {
                        // Increasing halfWidthInMeters will result in less precise results with the benefit of a less
                        // complex route shape.
                        Log.d("Search", "Route too long or halfWidthInMeters too small.")
                    } else {
                        Log.d(
                            "Search",
                            "No charging stations found along the route. Error: $searchError"
                        )
                    }
                    return@SearchCallback
                }

                // If error is nil, it is guaranteed that the items will not be nil.
                Log.d("Search", "Search along route found " + items!!.size + " charging stations:")
                for (place in items) {

                    addRestaurantMapMarker(place.geoCoordinates!!)

                    Log.d("Search", "Checking Search along route found restaurant: " + place.title)

                    // ...
                }

            })
    }


    private fun addRestaurantMapMarker(geoCoordinates: GeoCoordinates) {
        currentMarker = MapMarker(geoCoordinates)

            binding.mapView.mapScene.removeMapMarker(currentMarker)

        val mapImage = MapImageFactory.fromResource(resources, R.drawable.ic_restaurant)
        currentMarker.addImage(mapImage, MapMarkerImageStyle())
        binding.mapView.mapScene.addMapMarker(currentMarker)
    }

    private fun addCurrentMapMarker(geoCoordinates: GeoCoordinates) {
        currentMarker = MapMarker(geoCoordinates)
            binding.mapView.mapScene.removeMapMarker(currentMarker)

        val mapImage =
            MapImageFactory.fromResource(resources, R.drawable.ic_current_location_marker)
        currentMarker.addImage(mapImage, MapMarkerImageStyle())
        binding.mapView.mapScene.addMapMarker(currentMarker)
    }

    private fun addSourceMapMarker() {
        sourceMarker = MapMarker(sourceCoordinates)

            binding.mapView.mapScene.removeMapMarker(sourceMarker)
//            if (routeMapPolyline != null) {
//                binding.mapView.mapScene.removeMapPolyline(routeMapPolyline)
//            }


        val mapImage = MapImageFactory.fromResource(resources, R.drawable.ic_marker)


        sourceMarker.addImage(mapImage, MapMarkerImageStyle())
        binding.mapView.mapScene.addMapMarker(sourceMarker)
    }

    private fun addDestinationMapMarker() {
        destinationMarker = MapMarker(destinationCoordinates)
            binding.mapView.mapScene.removeMapMarker(destinationMarker)

        val mapImage = MapImageFactory.fromResource(resources, R.drawable.ic_marker)
        destinationMarker.addImage(mapImage, MapMarkerImageStyle())
        binding.mapView.mapScene.addMapMarker(destinationMarker)
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
//        if (mapPolyline != null) {
//            mapScene.removeMapPolyline(mapPolyline)
//        }
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
       binding. mapView.getGestures()
            .setTapListener(TapListener { touchPoint -> pickMapMarker(touchPoint) })
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
               Toast.makeText(this, "${topmostMapMarker.coordinates.latitude}",Toast.LENGTH_LONG).show()
            })
    }


}