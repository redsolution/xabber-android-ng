package com.xabber.presentation.onboarding.fragments.start

import androidx.lifecycle.ViewModel
import com.xabber.data.dto.HostListDto
import com.xabber.data.repository.AccountRepository
import io.reactivex.rxjava3.core.Single

class StartViewModel: ViewModel() {

    val accountRepository = AccountRepository()

    fun getHost(): Single<HostListDto> = accountRepository.getHostList()
}