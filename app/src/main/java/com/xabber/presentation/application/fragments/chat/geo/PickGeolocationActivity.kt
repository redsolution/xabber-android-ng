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
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.xabber.R
import com.xabber.data_base.models.messages.MessageReferenceStorageItem
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.databinding.ActivityPickGeolocationBinding
import com.xabber.presentation.application.fragments.chat.CustomMyLocationOsmOverlay
import com.xabber.utils.custom.SearchToolbar
import com.xabber.utils.getBitmap
import com.xabber.utils.hideSoftKeyboard
import com.xabber.utils.showToast
import io.reactivex.rxjava3.subjects.PublishSubject
import io.realm.kotlin.ext.realmListOf
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

class PickGeolocationActivity : AppCompatActivity() {
    private val chatVM: PickGeolocationViewModel by viewModels()
    private val binding: ActivityPickGeolocationBinding by lazy {
        ActivityPickGeolocationBinding.inflate(layoutInflater)
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
    private var selectedLocation: GeoPoint? = null
    private var isMyLocation = true

    // Search functionality disabled (Retrofit removed)
    // private val foundPlacesAdapter = ...

    init {
        // Search subscription commented out (Retrofit removed)
        /*
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
        */
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
        setupEdgeToEdge()
        if (savedInstanceState != null) {
            isBubbleShow = savedInstanceState.getBoolean(IS_BUBBLE_SHOW_KEY)
        }
        if (savedInstanceState == null && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            tryToGetMyLocation()
        }
        binding.bottomBubble.isVisible = isBubbleShow
        binding.frameSnack.isVisible = !isBubbleShow
        initToolbarActions()
        // initSearchRecycler() commented out (search disabled)
        setupMap()
        initMapButtons()
        initSendButton()
    }

    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.searchToolbar.setPadding(0, systemBars.top, 0, 0)
            windowInsets
        }
    }

    private fun initToolbarActions() {
        addSearchToolbarBackPressedListener()
        addColorChangerToolbar()
        // Search text listener commented out (Retrofit removed)
        /*
        binding.searchToolbar.onTextChangedListener = SearchToolbar.OnTextChangedListener {
            searchObservable.onNext(it)
        }
        */
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

    // initSearchRecycler() commented out (search disabled)
    /*
    private fun initSearchRecycler() {
        ...
    }
    */

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
        binding.imMyGeolocation.setOnClickListener {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                tryToGetMyLocation()
            } else requestGeolocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // setupSearchList commented out (Retrofit removed)
    /*
    private fun setupSearchList(list: List<Place>) { ... }
    */

    private fun tryToGetMyLocation() {
        fun createMyLocationsOverlay() {
            val locationsProvider = ObservableOsmLocationProvider(binding.mapView.context)
            val pointer = ContextCompat.getDrawable(this, R.drawable.ic_my_location_circle)!!.getBitmap()
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
                        selectedLocation = GeoPoint(
                            myLocationOverlay!!.myLocation.latitude,
                            myLocationOverlay!!.myLocation.longitude
                        )
                        isMyLocation = true
                        val locStr = myLocationOverlay?.myLocation.toString()
                        val parts = locStr.split(",")
                        val lonPart = parts[1].split(".")
                        val latPart = parts[0].split(".")
                        val lonFormatted = lonPart[0] + "," + lonPart[1].substring(0, minOf(3, lonPart[1].length - 1))
                        val latFormatted = latPart[0] + "," + latPart[1].substring(0, minOf(3, latPart[1].length - 1))
                        binding.tvLocationCoordinates.text = "$lonFormatted, $latFormatted"
                        binding.tvLocationCoordinates.setTextColor(
                            ContextCompat.getColor(this@PickGeolocationActivity, R.color.blue_400)
                        )
                        binding.frameSnack.isVisible = false
                        if (binding.tvLocationTitle.text.isNotEmpty() && binding.tvLocationCoordinates.text.isNotEmpty()) {
                            binding.bottomBubble.isVisible = true
                        }
                    }
                    delay(300)
                }
            }

            if (isLocationAllowed()) {
                if (myLocationOverlay == null) {
                    createMyLocationsOverlay()
                }
            } else {
                showDialogNeedToEnableLocations()
            }
        }

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (myLocationOverlay == null) {
                createMyLocationsOverlay()
            }
            centerOnMyLocation()
        } else {
            showDialogNeedToEnableLocations()
        }
    }

    private fun showDialogNeedToEnableLocations() {
        AlertDialog.Builder(this)
            .setMessage(R.string.enable_geolocation_dialog_body)
            .setPositiveButton(R.string.use_external_dialog_enable_button) { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton(R.string.dialog_button_cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun onStop() {
        myLocationOverlay?.disableFollowLocation()
        myLocationOverlay?.disableMyLocation()
        super.onStop()
    }

    private fun setupMap() {
        Configuration.getInstance()
            .load(this, androidx.preference.PreferenceManager.getDefaultSharedPreferences(this))
        binding.mapView.apply {
            setUseDataConnection(true)
            overlays.add(
                MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                        p?.let { updatePickMarker(it) }
                        return true
                    }

                    override fun longPressHelper(p: GeoPoint?): Boolean = false
                })
            )
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            binding.imZoomIn.setOnClickListener { controller.zoomIn() }
            binding.imZoomOut.setOnClickListener { controller.zoomOut() }
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

    private fun updatePickMarker(newLocation: GeoPoint) {
        if (pickMarker == null) {
            pickMarker = Marker(binding.mapView).apply {
                icon = ContextCompat.getDrawable(this@PickGeolocationActivity, R.drawable.ic_location)
                setOnMarkerClickListener { _, _ -> false }
            }
            binding.mapView.overlays.add(pickMarker)
        }
        pickMarker?.position = newLocation
        selectedLocation = newLocation
        binding.mapView.invalidate()
        binding.frameSnack.isVisible = false
        updateLocationInfoBubble(newLocation)
    }

    @SuppressLint("SetTextI18n")
    private fun updateLocationInfoBubble(newLocation: GeoPoint?) {
        selectedLocation = newLocation
        isMyLocation = false
        if (newLocation != null) {
            // Reverse geocoding commented out (Retrofit removed)
            /*
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
                ...
            }
            */
            binding.tvLocationTitle.text = "Selected location"
            val coordinateFormatString = "%.4f"
            binding.tvLocationCoordinates.setTextColor(
                ContextCompat.getColor(this@PickGeolocationActivity, R.color.grey_600)
            )
            binding.tvLocationCoordinates.text =
                "${coordinateFormatString.format(newLocation.longitude)}, ${
                    coordinateFormatString.format(newLocation.latitude)
                }"
            binding.bottomBubble.isVisible = true
        } else {
            binding.bottomBubble.isVisible = false
        }
    }

    private fun initSendButton() {
        binding.imSendLocation.setOnClickListener {
            val sendingLocation = selectedLocation
            if (sendingLocation == null) {
                showToast("Пожалуйста, подождите пока карта загрузится")
                return@setOnClickListener
            }

            val lat = sendingLocation.latitude
            val lon = sendingLocation.longitude

            val chatId = intent.getStringExtra("id") ?: return@setOnClickListener
            val chat = chatVM.getChat(chatId) ?: return@setOnClickListener

            val geoReference = MessageReferenceStorageItem().apply {
                isGeo = true
                latitude = lat
                longitude = lon
            }

            val currentTime = System.currentTimeMillis()
            val messageId = currentTime.toString()
            val message = MessageStorageItem().apply {
                this.messageId = messageId
                primary = MessageStorageItem.genPrimary(messageId, chat.owner)
                owner = chat.owner
                opponent = chat.opponentJid
                outgoing = true
                body = ""
                references = realmListOf(geoReference)
                sentDate = currentTime
                date = currentTime
                state = MessageSendingState.NotSent
                isRead = true
            }

            chatVM.insertMessage(chatId, message)
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(IS_BUBBLE_SHOW_KEY, binding.bottomBubble.isVisible)
    }

    companion object {
        const val IS_BUBBLE_SHOW_KEY = "is bubble show key"
    }
}