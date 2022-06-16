package com.xabber.presentation.application.fragments.chat

class PickGeolocationFragment
    //: DetailBaseFragment(R.layout.fragment_pick_geolocation) {
//    private val binding by viewBinding(FragmentPickGeolocationBinding::bind)
//    private var pointerColor: Int = 0
//
//    private val foundPlacesAdapter = FoundPlacesRecyclerViewAdapter(
//        onPlaceClickListener = {
//            myLocationOverlay?.disableFollowLocation()
//            binding.pickgeolocationMapView.controller.animateTo(it.toGeoPoint(), 16.5, 1)
//            binding.pickgeolocationMapView.invalidate()
//            binding.pickgeolocationRecyclerView.visibility = View.GONE
//            //     if (pickMarker != null ) {
//            binding.pickgeolocationLocationBottomRoot.visibility = View.VISIBLE
//            //    }
//            binding.pickgeolocationMyGeolocation.visibility = View.VISIBLE
//            //  tryToHideKeyboardIfNeed()
//        }
//    )
//
//    private var myLocationOverlay: MyLocationNewOverlay? = null
//
//    private val searchObservable = PublishSubject.create<String>()
////
////    init {
////        searchObservable.debounce(500, TimeUnit.MILLISECONDS)
////            .subscribe {
////                lifecycleScope.launch {
////                    binding.pickgeolocationProgressbar.visibility = View.VISIBLE
////                    val foundPlacesList = NominatimRetrofitModule.api.search(it)
////                    setupSearchList(foundPlacesList)
////                    binding.pickgeolocationProgressbar.visibility = View.INVISIBLE
////                }
////            }
////    }
//
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//        binding.pickgeolocationRecyclerView.apply {
//            val llm = LinearLayoutManager(context)
//            layoutManager = llm
//            adapter = foundPlacesAdapter
//            addItemDecoration(DividerItemDecoration(context, llm.orientation))
//        }
//
//        binding.searchToolbar.onTextChangedListener = SearchToolbar.OnTextChangedListener {
//            searchObservable.onNext(it)
//        }
//
//        binding.pickgeolocationMyGeolocation.setOnClickListener { tryToGetMyLocation() }
//
//        /* ignore to avoid interception of clicks by mapview */
//        binding.pickgeolocationLocationBottomRoot.setOnClickListener { }
//
//        binding.searchToolbar.title = "Pick location"
//
//        setupMap()
//        super.onCreate(savedInstanceState)
//    }
//
//    private fun setupSearchList(list: List<Place>) {
//        if (list.isEmpty()) {
//            binding.pickgeolocationRecyclerView.visibility = View.GONE
//
//            binding.pickgeolocationLocationBottomRoot.visibility = View.VISIBLE
//
//            binding.pickgeolocationMyGeolocation.visibility = View.VISIBLE
//        } else {
//            binding.pickgeolocationLocationBottomRoot.visibility = View.GONE
//            binding.pickgeolocationMyGeolocation.visibility = View.GONE
//            binding.pickgeolocationRecyclerView.visibility = View.VISIBLE
//            foundPlacesAdapter.placesList = list
//            foundPlacesAdapter.notifyDataSetChanged()
//        }
//    }
//
//    private fun tryToGetMyLocation() {
//        fun createMyLocationsOverlay() {
//            val locationsProvider = ObservableOsmLocationProvider(
//                binding.pickgeolocationMapView.context
//            )
//
//            locationsProvider.stateLiveData.observe(this) { state ->
//                updateMyLocationButton(state)
//            }
//
//            val pointer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                ContextCompat.getDrawable(requireContext(), R.drawable.ic_my_location_circle)
//            } else null
//
//            myLocationOverlay = CustomMyLocationOsmOverlay(
//                binding.pickgeolocationMapView,
//                locationsProvider,
//                pointerColor,
//                //    pointer
//            )
//
//            myLocationOverlay?.enableMyLocation()
//
//            binding.pickgeolocationMapView.overlays.add(myLocationOverlay)
//        }
//
//        fun centerOnMyLocation() {
//            lifecycleScope.launch {
//                repeat(15) {
//                    if (myLocationOverlay?.myLocation != null) {
//                        myLocationOverlay?.enableFollowLocation()
//                        binding.pickgeolocationMapView.controller.setZoom(16.5)
//                        cancel()
//                    }
//                    delay(300)
//                }
//                //todo possible show error while location retrieving
//            }
//        }
//
//
//        if (myLocationOverlay == null) {
//            createMyLocationsOverlay()
//        }
//        centerOnMyLocation()
//    }
//
////    private fun showDialogNeedToEnableLocations(){
////        AlertDialog.Builder(requireContext())
////            .setMessage(R.string.enable_geolocation_dialog_body)
////            .setPositiveButton(R.string.use_external_dialog_enable_button
////            ) { _, _ ->
////                startActivity(
////                    Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
////                )
////            }.setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
////            .show()
////    }
//
//    override fun onStop() {
//        myLocationOverlay?.disableFollowLocation()
//        myLocationOverlay?.disableMyLocation()
//        super.onStop()
//    }
//
////    override fun onRequestPermissionsResult(
////        requestCode: Int,
////        permissions: Array<out String>,
////        grantResults: IntArray
////    ) {
////        if (requestCode == REQUEST_LOCATION_PERMISSION_CODE) {
////            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
////                tryToGetMyLocation()
////            }
////        }
////        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
////    }
//
////    private fun colorizeToolbar() {
////        binding.searchToolbar.color =
////            if (SettingsManager.interfaceTheme() == SettingsManager.InterfaceTheme.light) {
////                Color.WHITE
////            } else {
////                Color.BLACK
////            }
////    }
//
////    private fun colorizeStatusBar() {
////        if (SettingsManager.interfaceTheme() == SettingsManager.InterfaceTheme.light) {
////            window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
////            StatusBarPainter.instanceUpdateWIthColor(this, Color.WHITE)
////        }
////    }
//
////    private fun isLocationAllowed(): Boolean {
////        return (getSystemService(LOCATION_SERVICE) as? LocationManager)?.getProviders(true)?.isNotEmpty()
////            ?: false
////    }
//
//    private fun setupMap() {
//        binding.pickgeolocationMapView.apply {
//            overlays.add(
//                MapEventsOverlay(
//                    object : MapEventsReceiver {
//                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
//                            p?.let { updatePickMarker(it) }
//                            return true
//                        }
//
//                        override fun longPressHelper(p: GeoPoint?): Boolean = false
//                    }
//                )
//            )
//
//            zoomController.setVisibility(CustomZoomButtonsController.Visibility.ALWAYS)
//            zoomController.display.setPositions(
//                false,
//                CustomZoomButtonsDisplay.HorizontalPosition.RIGHT,
//                CustomZoomButtonsDisplay.VerticalPosition.CENTER
//            )
//
//            setMultiTouchControls(true)
//
//            visibility = View.VISIBLE
//            setTileSource(TileSourceFactory.MAPNIK)
//            isTilesScaledToDpi = true
//
//            controller.apply {
//                setZoom(5.0)
//                minZoomLevel = 3.5
//            }
//
//            setHasTransientState(true)
//        }
//
//
//        tryToGetMyLocation()
//
//    }
//
//    private fun updateMyLocationButton(locationStatus: ObservableOsmLocationProvider.LocationState) {
//        fun updateButton(@DrawableRes drawableId: Int) {
//            binding.pickgeolocationMyGeolocation.setImageResource(drawableId)
//        }
//        updateButton(
//            when (locationStatus) {
//                ObservableOsmLocationProvider.LocationState.LocationReceived -> {
//                      R.drawable.ic_crosshairs_gps
//                }
//                ObservableOsmLocationProvider.LocationState.LocationNotFound -> {
//                    R.drawable.ic_crosshairs_question
//                }
//            }
//        )
//    }
//
//    private fun updatePickMarker(location: GeoPoint) {
//
//        val    pickMarker = Marker(binding.pickgeolocationMapView).apply {
//                icon = resources.getDrawable(R.drawable.ic_location).apply {
//                    setColorFilter(pointerColor, PorterDuff.Mode.MULTIPLY)
//                }
//                /* Ignore just to avoid showing a strange standard osm bubble on marker click */
//                setOnMarkerClickListener { _, _ -> false }
//            }
//            binding.pickgeolocationMapView.overlays.add(pickMarker)
//
//        pickMarker?.position = location
//        binding.pickgeolocationMapView.invalidate()
//        updateLocationInfoBubble(location)
//    }
//
//    private fun updateLocationInfoBubble(location: GeoPoint?) {
//        if (location != null) {
//            binding.pickgeolocationProgressbar.visibility = View.VISIBLE
//            lifecycleScope.launch(CoroutineExceptionHandler { _, ex ->
//                binding.pickgeolocationProgressbar.visibility = View.INVISIBLE
//                binding.pickgeolocationLocationTitle.visibility = View.GONE
//
//            }) {
//                val lang = Locale.getDefault().language
//                val place = NominatimRetrofitModule.api.fromLonLat(
//                    location.longitude, location.latitude, lang
//                )
//                binding.pickgeolocationLocationTitle.text = place.prettyName
//                binding.pickgeolocationLocationTitle.visibility = View.VISIBLE
//                binding.pickgeolocationProgressbar.visibility = View.INVISIBLE
//            }
//            val coordFormatString = "%.4f"
//            binding.pickgeolocationLocationCoordinates.text =
//                "${coordFormatString.format(location.longitude)}, ${
//                    coordFormatString.format(
//                        location.latitude
//                    )
//                }"
//
//            binding.pickgeolocationLocationSendButton.setOnClickListener {
////                setResult(
////                    RESULT_OK,
////                    Intent().apply {
////                        putExtra(LAT_RESULT, location.latitude)
////                        putExtra(LON_RESULT, location.longitude)
////                    }
////                )
////                finish()
//            }
//            binding.pickgeolocationLocationBottomRoot.visibility = View.VISIBLE
//        } else {
//            binding.pickgeolocationLocationBottomRoot.visibility = View.GONE
//        }
//    }
//

//}