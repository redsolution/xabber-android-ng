package com.xabber.presentation.application.fragments.chat.geo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.xabber.R
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.databinding.ActivityPickGeolocationBinding
import com.xabber.dto.MessageDto
import com.xabber.dto.MessageReferenceDto
import com.xabber.presentation.application.fragments.chat.CustomMyLocationOsmOverlay
import com.xabber.remote.NominatimRetrofitModule
import com.xabber.remote.Place
import com.xabber.remote.prettyName
import com.xabber.remote.toGeoPoint
import com.xabber.utils.custom.SearchToolbar
import com.xabber.utils.getBitmap
import com.xabber.utils.hideSoftKeyboard
import com.xabber.utils.showToast
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.*
import java.util.concurrent.TimeUnit


class PickGeolocationActivity : AppCompatActivity() {
    private val chatVM: PickGeolocationViewModel by viewModels()
    private val binding: ActivityPickGeolocationBinding by lazy {
        ActivityPickGeolocationBinding.inflate(
            layoutInflater
        )
    }

    private val requestGeolocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ::onGotLocationPermissionResult
    )

    private var pickMarker: Marker? = null
    private var pointerColor: Int = 0
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private val searchObservable = PublishSubject.create<String>()
    private val pZoom = 16.5
    private val pSpeed = 1L
    private var isBubbleShow = false
    private var location = Location(0.0, 0.0)
    private var isMyLocation = true


    private val foundPlacesAdapter = FoundPlacesRecyclerViewAdapter(
        onPlaceClickListener = {
            myLocationOverlay?.disableFollowLocation()
            binding.mapView.controller.animateTo(it.toGeoPoint(), pZoom, pSpeed)
            updatePickMarker(it.toGeoPoint())
            binding.mapView.invalidate()
            binding.rvLocations.isVisible = false
            binding.searchToolbar.collapseSearchBar(true)
            hideSoftKeyboard(binding.root)
            if (pickMarker != null) {
                binding.bottomBubble.isVisible = true
            }
        }
    )

    init {
        searchObservable.debounce(500, TimeUnit.MILLISECONDS)
            .subscribe {
                lifecycleScope.launch {
                    binding.progressbarSearchLocations.isVisible =
                        binding.searchToolbar.isOpenSearchBar()
                    val foundPlacesList = NominatimRetrofitModule.api.search(it)
                    setupSearchList(foundPlacesList)
                    binding.progressbarSearchLocations.isVisible = false
                }
            }
    }

    private fun isLocationAllowed(): Boolean {
        return (getSystemService(LOCATION_SERVICE) as? LocationManager)?.getProviders(true)
            ?.isNotEmpty()
            ?: false
    }

    private fun onGotLocationPermissionResult(granted: Boolean) {
        if (granted) tryToGetMyLocation()
        else if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) showDialogNeedToEnableLocations()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setFullScreenMode()
        setHeightStatusBar()
        if (savedInstanceState != null) {
            isBubbleShow = savedInstanceState.getBoolean(IS_BUBBLE_SHOW_KEY)
        }
        if (savedInstanceState == null && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            tryToGetMyLocation()
        }
        binding.bottomBubble.isVisible = isBubbleShow
        binding.frameSnack.isVisible = !isBubbleShow
        if (isBubbleShow) {

        }
        initToolbarActions()
        initSearchRecycler()
        setupMap()
        initMapButtons()
        initSendButton()
    }

    private fun setFullScreenMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    private fun setHeightStatusBar() {
        val height = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = resources.getDimensionPixelSize(height)
        binding.searchToolbar.setPadding(0, statusBarHeight, 0, 0)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            insets.consumeSystemWindowInsets()
        }
    }

    private fun initToolbarActions() {
        addSearchToolbarBackPressedListener()
        addColorChangerToolbar()
        binding.searchToolbar.onTextChangedListener = SearchToolbar.OnTextChangedListener {
            searchObservable.onNext(it)
        }
    }

    private fun addSearchToolbarBackPressedListener() {
        binding.searchToolbar.onBackPressedListener = SearchToolbar.OnBackPressedListener {
            if (binding.searchToolbar.isOpenSearchBar()) {
                binding.rvLocations.isVisible = false
                binding.progressbarSearchLocations.isVisible = false
                binding.searchToolbar.collapseSearchBar(true)
            } else {
                setResult(RESULT_CANCELED)
                finish()
            }
        }
    }

    private fun addColorChangerToolbar() {
        binding.searchToolbar.searchEditText.setOnFocusChangeListener { _, hasFocused ->
            when {
                hasFocused -> binding.searchToolbar.setBackgroundResource(R.color.white)

                else -> binding.searchToolbar.setBackgroundResource(R.drawable.light_gradient)
            }
        }
    }

    private fun initSearchRecycler() {
        binding.rvLocations.apply {
            val llm = LinearLayoutManager(this@PickGeolocationActivity)
            layoutManager = llm
            adapter = foundPlacesAdapter
            addItemDecoration(DividerItemDecoration(context, llm.orientation))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.bottomBubble.isVisible) {
            binding.tvLocationTitle.text = ""
            binding.tvLocationCoordinates.text = ""
            binding.bottomBubble.isVisible = false
            binding.frameSnack.isVisible = true
        } else {
            super.onBackPressed()
        }
    }

    private fun initMapButtons() {
        binding.imSendLocation.setOnClickListener {
            val resultIntent = Intent()
            resultIntent.putExtra(LON_RESULT, myLocationOverlay?.myLocation?.longitude)
            resultIntent.putExtra(LAT_RESULT, myLocationOverlay?.myLocation?.latitude)
            finish()
        }

        binding.imMyGeolocation.setOnClickListener {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                tryToGetMyLocation()
            } else requestGeolocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setupSearchList(list: List<Place>) {
        if (list.isEmpty()) {
            binding.rvLocations.isVisible = false
            if (pickMarker != null) {
                binding.bottomBubble.isVisible = true
            }
            binding.imMyGeolocation.isVisible = true
        } else {
            binding.bottomBubble.isVisible = false
            binding.imMyGeolocation.isVisible = false
            binding.rvLocations.isVisible = true
            foundPlacesAdapter.placesList = list
            foundPlacesAdapter.notifyDataSetChanged()
        }
    }

    private fun tryToGetMyLocation() {
        fun createMyLocationsOverlay() {
            val locationsProvider = ObservableOsmLocationProvider(
                binding.mapView.context
            )

            val pointer =
                ContextCompat.getDrawable(this, R.drawable.ic_my_location_circle)!!.getBitmap()
            initMapButtons()
            myLocationOverlay = CustomMyLocationOsmOverlay(
                binding.mapView,
                locationsProvider,
                pointerColor,
                pointer
            )
            myLocationOverlay?.enableMyLocation()
            binding.mapView.overlays.add(myLocationOverlay)
        }

        fun centerOnMyLocation() {
            lifecycleScope.launch {
                repeat(15) {
                    if (myLocationOverlay?.myLocation != null) {
                        myLocationOverlay?.enableFollowLocation()
                        binding.mapView.controller.setZoom(16.5)
                        cancel()
                        binding.tvLocationTitle.text = "Send current location "
                        if (myLocationOverlay != null) location = Location(
                            myLocationOverlay!!.myLocation.latitude,
                            myLocationOverlay!!.myLocation.longitude
                        )
                        isMyLocation = true
                        val location = myLocationOverlay?.myLocation.toString()
                        val list = location.split(",")
                        val a = list[1]
                        val lista = a.split(".")
                        val x = lista[0] + "," + lista[1].substring(0..3)
                        val listb = list[0].split(".")
                        val y = listb[0] + "," + listb[1].substring(0..3)
                        binding.tvLocationCoordinates.text = x + ", " + y
                        binding.tvLocationCoordinates.setTextColor(
                            ContextCompat.getColor(
                                this@PickGeolocationActivity,
                                R.color.blue_400
                            )
                        )
                        binding.frameSnack.isVisible = false
                        if (binding.tvLocationTitle.text.isNotEmpty() && binding.tvLocationCoordinates.text.isNotEmpty()) binding.bottomBubble.isVisible =
                            true

                    }
                    delay(300)
                }

            }

            if (PermissionsRequester.requestLocationPermissionIfNeeded(
                    this,
                    REQUEST_LOCATION_PERMISSION_CODE
                )
            ) {
                if (isLocationAllowed()) {
                    if (myLocationOverlay == null) {
                        createMyLocationsOverlay()
                    }

                } else {
                    showDialogNeedToEnableLocations()
                }
            }
        }

        if (PermissionsRequester.requestLocationPermissionIfNeeded(
                this,
                REQUEST_LOCATION_PERMISSION_CODE
            )
        ) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                if (myLocationOverlay == null) {
                    createMyLocationsOverlay()
                }
                centerOnMyLocation()
            } else {
                showDialogNeedToEnableLocations()
            }
        }
    }

    private fun showDialogNeedToEnableLocations() {
        AlertDialog.Builder(this)
            .setMessage(R.string.enable_geolocation_dialog_body)
            .setPositiveButton(
                R.string.use_external_dialog_enable_button
            ) { _, _ ->
                startActivity(
                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                )
            }.setNegativeButton(R.string.dialog_button_cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun onStop() {
        myLocationOverlay?.disableFollowLocation()
        myLocationOverlay?.disableMyLocation()
        super.onStop()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_LOCATION_PERMISSION_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                tryToGetMyLocation()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun setupMap() {
        Configuration.getInstance()
            .load(this, androidx.preference.PreferenceManager.getDefaultSharedPreferences(this))
        binding.mapView.apply {
            setUseDataConnection(true)
            overlays.add(
                MapEventsOverlay(
                    object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            p?.let { updatePickMarker(it) }
                            return true
                        }

                        override fun longPressHelper(p: GeoPoint?): Boolean = false
                    }
                )
            )
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            binding.imZoomIn.setOnClickListener { binding.mapView.controller.zoomIn() }
            binding.imZoomOut.setOnClickListener { binding.mapView.controller.zoomOut() }
            setMultiTouchControls(true)

            setTileSource(TileSourceFactory.MAPNIK)
            isTilesScaledToDpi = true

            controller.apply {
                setZoom(5.0)
                minZoomLevel = 3.5
            }
            setHasTransientState(true)
        }

    }

    private fun updatePickMarker(location: GeoPoint) {
        if (pickMarker == null) {
            pickMarker = Marker(binding.mapView).apply {
                icon =
                    ContextCompat.getDrawable(this@PickGeolocationActivity, R.drawable.ic_location)

                /* Ignore just to avoid showing a strange standard osm bubble on marker click */
                setOnMarkerClickListener { _, _ -> false }
            }
            binding.mapView.overlays.add(pickMarker)
        }
        pickMarker?.position = location
        binding.mapView.invalidate()
        binding.frameSnack.isVisible = false
        updateLocationInfoBubble(location)
    }

    @SuppressLint("SetTextI18n")
    private fun updateLocationInfoBubble(newLocation: GeoPoint?) {
        if (newLocation != null) location = Location(newLocation.latitude, newLocation.longitude)
        isMyLocation = false
        if (newLocation != null) {
            binding.progressbarSearchLocations.visibility = View.VISIBLE
            lifecycleScope.launch(CoroutineExceptionHandler { _, _ ->
                binding.progressbarSearchLocations.visibility = View.INVISIBLE
                binding.tvLocationTitle.isVisible = false
            }) {
                val lang = Locale.getDefault().language
                val place = NominatimRetrofitModule.api.fromLonLat(
                    newLocation.longitude, newLocation.latitude, lang
                )
                binding.tvLocationTitle.text =
                    if (place.prettyName != null) place.prettyName else "Location not defined"
                binding.progressbarSearchLocations.isVisible = false
                val coordinateFormatString = "%.4f"
                binding.tvLocationCoordinates.setTextColor(
                    ContextCompat.getColor(
                        this@PickGeolocationActivity,
                        R.color.grey_600
                    )
                )
                binding.tvLocationCoordinates.text =
                    "${coordinateFormatString.format(newLocation.longitude)}, ${
                        coordinateFormatString.format(
                            newLocation.latitude
                        )
                    }"
            }


            binding.bottomBubble.isVisible = true
        } else {
            binding.bottomBubble.isVisible = false
        }
    }

    private fun initSendButton() {
        binding.imSendLocation.setOnClickListener {
            val sendingLocation = if (isMyLocation) myLocationOverlay?.myLocation else location
            if (sendingLocation == null) showToast("Пожалуйста, подождите пока карта загрузится")
            else {
                val lon = location.longitude
                val lat = location.latitude
                if (lon != null && lat != null) {
                    val geoMessage = MessageReferenceDto(
                        isGeo = true,
                        latitude = lat,
                        longitude = lon,
                        size = 0L
                    )
                    val refer = ArrayList<MessageReferenceDto>()
                    refer.add(geoMessage)
                    val id = intent.getStringExtra("id") ?: ""
                    val chat = chatVM.getChat(id)
                    val message = MessageDto(
                        primary = id + System.currentTimeMillis(),
                        references = refer,
                        isOutgoing = true,
                        owner = chat!!.owner,
                        opponentJid = chat.opponentJid,
                        canDeleteMessage = false,
                        canEditMessage = false,
                        messageBody = "",
                        messageSendingState = MessageSendingState.Sending,
                        sentTimestamp = System.currentTimeMillis(),
                        isGroup = false
                    )
                    chatVM.insertMessage(id, message)
                }
                finish()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(IS_BUBBLE_SHOW_KEY, binding.bottomBubble.isVisible)
        //   outState.putParcelable("location", Location(myLocationOverlay.myLocation.latitude, myLocationOverlay?.myLocation.longitude))
    }

    companion object {
        const val IS_BUBBLE_SHOW_KEY = "is bubble show key"
        fun createIntent(context: Context) {}

        //= Intent(context, PickGeolocationActivity::class.java)
        const val LAT_RESULT = "com.xabber.android.ui.activity.LAT_RESULT"
        const val LON_RESULT = "com.xabber.android.ui.activity.LON_RESULT"

        private const val REQUEST_LOCATION_PERMISSION_CODE = 10
    }

}