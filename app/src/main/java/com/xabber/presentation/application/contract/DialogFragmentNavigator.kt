package com.xabber.presentation.application.contract

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.navigation.NavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.R
import com.xabber.presentation.application.fragments.account.AccountFragment
import com.xabber.presentation.application.fragments.settings.ProfileSettingsFragment
import com.xabber.presentation.application.fragments.settings.SettingsFragment

class DialogFragmentNavigator : BottomSheetDialogFragment(), DialogNavigator {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_container, container, false)


        if (savedInstanceState == null) {
            showInitialFragment()
        }

        return view
    }


    private fun launchFragment(fragment: Fragment) {
        parentFragmentManager.commit {
            replace(R.id.application_container, fragment)
        }
    }
    private fun launchDetail(fragment: Fragment) {
        childFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.detail_container, fragment)
        }

    }
    private fun launchDetailInStack(fragment: Fragment) {
        childFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.detail_container, fragment).addToBackStack(null)
        }
    }


    private fun showInitialFragment() {
        val initialFragment = DialogFragmentNavigator()
        parentFragmentManager.beginTransaction()
            .replace(R.id.dialog_container, initialFragment)

            .commit()
    }
    override fun goBack() {
        if (childFragmentManager.backStackEntryCount > 0) {
            childFragmentManager.popBackStack()
        } else {
            dismiss() // Close the dialog if there's nothing to pop
        }
    }

    override fun showAccountSettingsPage() {
        val accountSettings = ProfileSettingsFragment()
        childFragmentManager.beginTransaction()
            .replace(R.id.dialog_container, accountSettings)
            .setReorderingAllowed(true)
            .addToBackStack(null) // Add to back stack for back navigation
            .commit()
    }

    override fun showProfileSettings() {
        launchDetailInStack(ProfileSettingsFragment())
    }

    override fun showAccount(jid: String) {
        launchFragment(AccountFragment.newInstance(jid))
    }
}

