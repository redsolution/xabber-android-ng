package com.xabber.presentation.custom

import android.content.Context
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.xabber.R

class SearchToolbar : ConstraintLayout {

    @ColorInt
    var color: Int = 0
        set(value) {
            field = value
            setBackgroundColor(color)
        }

    var title: String? = null
        set(value) {
            field = value
            findViewById<TextView>(R.id.search_toolbar_title).text = value
        }

    var searchText: String? = null
        private set

    var onBackPressedListener: OnBackPressedListener? = null
    var onTextChangedListener: OnTextChangedListener? = null

    private lateinit var greetingsView: ConstraintLayout
    private lateinit var searchView: ConstraintLayout
    lateinit var searchEditText: EditText
    private lateinit var clearButton: ImageView
    private lateinit var searchButton: ImageView
    private lateinit var backButton: ImageView
    private var isSearch = false

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(context)
    }

    fun interface OnBackPressedListener {
        fun onBackPressed()
    }

    fun interface OnTextChangedListener {
        fun onTextChanged(text: String)
    }

    fun collapseSearchBar(collapse: Boolean) {
        if (collapse) {
            searchEditText.text.clear()
            greetingsView.isVisible = true
            searchView.isVisible = false
            isSearch = false
        } else {
            greetingsView.isVisible = false
            searchView.isVisible = true
            isSearch = true
        }
    }

    fun isOpenSearchBar(): Boolean = searchEditText.isFocused

    override fun onRestoreInstanceState(state: Parcelable?) {
        val savedState = state as SavedState
        super.onRestoreInstanceState(savedState.superState)
        isSearch = savedState.isSearchMode
        collapseSearchBar(!isSearch)
    }

    private fun init(context: Context) {
        inflate(context, R.layout.search_toolbar, this)
        greetingsView = findViewById(R.id.search_toolbar_greetings_view)
        searchView = findViewById(R.id.search_toolbar_search_view)
        searchEditText = findViewById(R.id.search_toolbar_edittext)
        clearButton = findViewById(R.id.search_toolbar_clear_button)
        backButton = findViewById(R.id.toolbar_search_back_button)
        searchButton = findViewById(R.id.search_toolbar_search_button)
        addListenersForButtons()
        addEditTextListener()
    }

    private fun addListenersForButtons() {
        clearButton.setOnClickListener { searchEditText.text.clear() }

        backButton.setOnClickListener {
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
                searchEditText.windowToken, 0
            )
            onBackPressedListener?.onBackPressed()
        }

        searchButton.setOnClickListener { setSearch() }
    }

    private fun addEditTextListener() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                clearButton.visibility = if (s?.isNotEmpty() == true) View.VISIBLE else View.GONE
                onTextChangedListener?.onTextChanged(s.toString())
                searchText = s.toString()
            }
        })
    }

    private fun setSearch(isActiveSearch: Boolean = true) {
        collapseSearchBar(!isActiveSearch)
        if (isActiveSearch) {
            searchEditText.requestFocusFromTouch()
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(
                searchEditText, InputMethodManager.SHOW_IMPLICIT
            )
        } else {
            collapseSearchBar(true)
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
                searchEditText.windowToken, 0
            )
        }
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()!!
        val savedState = SavedState(superState)
        savedState.isSearchMode = isSearch
        return savedState
    }


    class SavedState : BaseSavedState {
        var isSearchMode = false

        constructor(superState: Parcelable) : super(superState)

        @RequiresApi(Build.VERSION_CODES.Q)
        constructor(parcel: Parcel) : super(parcel) {
            isSearchMode = parcel.readBoolean()

        }

        @RequiresApi(Build.VERSION_CODES.Q)
        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeBoolean(isSearchMode)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                @RequiresApi(Build.VERSION_CODES.Q)
                override fun createFromParcel(source: Parcel): SavedState {
                    return SavedState(source)
                }

                override fun newArray(size: Int): Array<SavedState?> {
                    return Array(size) { null }
                }
            }
        }
    }

}
