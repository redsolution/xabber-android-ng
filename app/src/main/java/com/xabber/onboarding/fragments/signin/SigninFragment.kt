package com.xabber.onboarding.fragments.signin

import android.content.Context
import android.os.Bundle
import android.text.*
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.xabber.R
import com.xabber.databinding.FragmentSigninBinding
import com.xabber.onboarding.contract.navigator
import com.xabber.onboarding.contract.toolbarChanger
import com.xabber.onboarding.fragments.signin.feature.FeatureAdapter
import com.xabber.onboarding.fragments.signin.feature.State
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SigninFragment() : Fragment() {
    protected val compositeDisposable = CompositeDisposable()
    private var binding: FragmentSigninBinding? = null
    private val login = "1"
    private val password = "1"
    private val featureAdapter = FeatureAdapter()
    private val viewModel = SigninViewModel()
    var host: String = "dev.xabber.org"


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSigninBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        toolbarChanger().setShowBack(true)
        toolbarChanger().setTitle(R.string.signin_toolbar_title_1)
        initEditText()
        initButton()
        initRecyclerView()
        binding?.signinSubtitle1?.text = getSubtitleClickableSpan()
        binding?.signinSubtitle1?.movementMethod = LinkMovementMethod.getInstance()

    }

    private fun initEditText() {

        binding?.editTextLogin?.setOnFocusChangeListener { _, hasFocused ->
            if (hasFocused) {
                binding?.editTextLogin?.background = resources.getDrawable(R.drawable.frame_blue)
            } else {
                binding?.editTextLogin?.background = resources.getDrawable(R.drawable.frame_normal)
            }
        }


        binding?.editTextPassword?.setOnFocusChangeListener { _, hasFocused ->
            if (hasFocused) {
                binding?.editTextPassword?.background = resources.getDrawable(R.drawable.frame_blue)
            } else {
                binding?.editTextPassword?.background =
                    resources.getDrawable(R.drawable.frame_normal)

            }

        }

        binding?.editTextLogin?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun afterTextChanged(p0: Editable?) {
                var jidText = p0.toString()
                if (!jidText.contains('@'))
                    jidText += "@$host"
                binding?.btnConnect?.isEnabled =
                    viewModel.isJidValid(jidText) && binding?.editTextPassword?.text!!.isNotEmpty()
                binding?.signinSubtitle1?.setTextColor(
                    ResourcesCompat.getColor(
                        resources,
                        R.color.grey_text_3,
                        requireContext().theme
                    )
                )
                binding?.signinSubtitle1?.text = getSubtitleClickableSpan()
                binding?.signinSubtitle1?.movementMethod = LinkMovementMethod.getInstance()
            }
        })
        binding?.editTextPassword?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun afterTextChanged(p0: Editable?) {
                var jidText = binding?.editTextLogin?.text.toString()
                // if (!jidText.contains('@'))
                //    jidText += "@$host"
                binding?.btnConnect?.isEnabled = p0.toString().isNotEmpty()
                //&& viewModel.isJidValid(jidText)
                binding?.signinSubtitle1?.setTextColor(
                    ResourcesCompat.getColor(
                        resources,
                        R.color.grey_text_3,
                        requireContext().theme
                    )
                )
                binding?.signinSubtitle1!!.text = getSubtitleClickableSpan()
                binding?.signinSubtitle1!!.movementMethod = LinkMovementMethod.getInstance()
            }
        })
        binding?.editTextPassword?.setOnEditorActionListener { _, i, _ ->
            if (i == EditorInfo.IME_ACTION_DONE) {
                if (binding?.btnConnect!!.isEnabled)
                    binding?.btnConnect!!.performClick()
                closeKeyboard()
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }

    }


    private fun initButton() {
        with(binding!!) {
            btnConnect.setOnClickListener {
                if (editTextLogin.text.trim().toString() != login || editTextPassword.text.trim()
                        .toString(
                        ) != password
                ) {
                    signinSubtitle1.setTextColor(
                        ResourcesCompat.getColor(
                            resources,
                            R.color.red_600,
                            requireContext().theme
                        )
                    )
                    signinSubtitle1.text =
                        resources.getString(R.string.signin_subtitle_error_message)
                } else {
                    textEnabled()
                    btnConnect.isEnabled = false
                    binding?.btnConnect!!.text = "Connecting..."

                    val spannable =
                        SpannableStringBuilder(resources.getString(R.string.signin_subtitle_label_1))
                    spannable.setSpan(
                        ForegroundColorSpan(
                            ResourcesCompat.getColor(
                                resources,
                                R.color.grey_400,
                                requireContext().theme
                            )
                        ),
                        34,
                        44,
                        Spannable.SPAN_EXCLUSIVE_INCLUSIVE
                    )
                 signinSubtitle1.text = spannable
                    signinSubtitle1.movementMethod = null

                    signinScrollView.visibility = View.VISIBLE
                    rvFeature.visibility = View.VISIBLE
                    closeKeyboard()
                    if (viewModel.isJidValid(editTextLogin.text.toString()) || editTextPassword.text.length > 5) {
                        compositeDisposable.add(viewModel.features
                            .doOnNext { list ->
                                if (list.filter { it.nameResId == R.string.feature_name_4 }
                                        .count() == 1) {
                                    toolbarChanger().setTitle(R.string.signin_toolbar_title_2)
                                    toolbarChanger().setShowBack(false)
                                    signinTitle.text = String.format(
                                        resources.getString(R.string.signin_title_label_template_2),
                                        host
                                    )
                                    editTextLogin.visibility = View.GONE
                                    editTextPassword.visibility = View.GONE
                                    signinSubtitle1.isVisible = false
                                    btnConnect.isVisible = false
                                }
                                if (list.all { it.state != State.Error } || viewModel.isServerFeatures) {
                                    featureAdapter.submitList(list)
                                    featureAdapter.notifyItemChanged(list.lastIndex)
                                }
                                lifecycleScope.launch {
                                    delay(150)
                                    list[list.lastIndex].state =
//                                if ((0..3).random() > 1)
                                        State.Success
//                                else
//                                    State.Error
                                    if (viewModel.isServerFeatures) {
                                        featureAdapter?.submitList(list)
                                        featureAdapter?.notifyItemChanged(list.lastIndex)
                                    }
                                    if (list.filter { it.nameResId == R.string.feature_name_10 }
                                            .count() == 1) {
                                        signinSubtitle2.isVisible = true

                                        btnRock.isVisible = true
                                        btnRock.setOnClickListener {
                                            navigator().goToApplicationActivity()
                                        }
                                    }
                                    if (viewModel._features.filter { it.state == State.Error }
                                            .count() <= 1 &&
                                        viewModel._features[list.lastIndex].state == State.Error
                                    ) {
                                        featureAdapter?.submitList(list)
                                        featureAdapter?.notifyItemChanged(list.lastIndex)
                                    }
                                    if (viewModel._features[list.lastIndex].state == State.Success &&
                                        viewModel._features.filter { it.state == State.Error }
                                            .count() == 0
                                    ) {
                                        featureAdapter?.submitList(list)
                                        featureAdapter?.notifyItemChanged(list.lastIndex)
                                    }
                                }
                            }
                            .subscribe({}, {})
                        )
                    } else {
                        signinSubtitle1.setTextColor(
                            ResourcesCompat.getColor(
                                resources,
                                R.color.red_600,
                                requireContext().theme
                            )
                        )


                    }
                }

            }
        }
    }


    private fun initRecyclerView() {
        binding?.rvFeature?.adapter = featureAdapter
    }

    private fun closeKeyboard() {
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
            binding?.editTextPassword?.windowToken,
            0
        )
    }


    private fun textEnabled() {
        binding?.signinSubtitle1?.movementMethod = null

    }

    fun getSubtitleClickableSpan(): Spannable {
        val spannable =
            SpannableStringBuilder(resources.getString(R.string.signin_subtitle_label_1))
        spannable.setSpan(
            ForegroundColorSpan(
                ResourcesCompat.getColor(
                    resources,
                    R.color.blue_600,
                    requireContext().theme
                )
            ),
            34,
            44,
            Spannable.SPAN_EXCLUSIVE_INCLUSIVE
        )
        spannable.setSpan(
            object : ClickableSpan() {
                override fun onClick(p0: View) {
                    navigator().startSignupNicknameFragment()
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.isUnderlineText = false
                }
            },
            34,
            44,
            Spannable.SPAN_EXCLUSIVE_INCLUSIVE
        )
        return spannable
    }


}
