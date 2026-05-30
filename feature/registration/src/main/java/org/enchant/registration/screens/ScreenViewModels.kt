package org.enchant.registration.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.enchant.registration.CountryData

class PhoneNumberEntryViewModel(
    private val initialState: PhoneNumberEntryState = PhoneNumberEntryState()
) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<PhoneNumberEntryState> = _state

    fun onEvent(event: PhoneNumberEntryEvent) {
        when (event) {
            is PhoneNumberEntryEvent.CountrySelected -> onCountrySelected(event.country)
            is PhoneNumberEntryEvent.CaptchaCompleted -> onCaptchaCompleted(event.token)
            is PhoneNumberEntryEvent.PhoneNumberChanged -> {
                _state.value = _state.value.copy(phoneNumber = event.phoneNumber)
            }
            PhoneNumberEntryEvent.Submit -> {}
        }
    }

    fun onCountrySelected(country: CountryData) {
        _state.value = _state.value.copy(selectedCountry = country)
    }

    fun onCaptchaCompleted(token: String) {
        _state.value = _state.value.copy(captchaToken = token)
    }

    class Factory : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            if (modelClass.isAssignableFrom(PhoneNumberEntryViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return PhoneNumberEntryViewModel() as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

sealed interface PhoneNumberEntryEvent {
    data class CountrySelected(val country: CountryData) : PhoneNumberEntryEvent
    data class CaptchaCompleted(val token: String) : PhoneNumberEntryEvent
    data class PhoneNumberChanged(val phoneNumber: String) : PhoneNumberEntryEvent
    data object Submit : PhoneNumberEntryEvent
}

data class PhoneNumberEntryState(
    val phoneNumber: String = "",
    val selectedCountry: CountryData? = null,
    val captchaToken: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class CountryCodePickerViewModel : ViewModel() {

    private val _state = MutableStateFlow(CountryCodePickerState())
    val state: StateFlow<CountryCodePickerState> = _state

    fun onEvent(event: CountryCodePickerEvent) {
        when (event) {
            is CountryCodePickerEvent.SearchQueryChanged -> {
                _state.value = _state.value.copy(query = event.query)
            }
            is CountryCodePickerEvent.CountrySelected -> {
                _state.value = _state.value.copy(selectedCountry = event.country)
            }
        }
    }

    fun onCountrySelected(country: CountryData) {
        _state.value = _state.value.copy(selectedCountry = country)
    }

    class Factory : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            if (modelClass.isAssignableFrom(CountryCodePickerViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return CountryCodePickerViewModel() as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

sealed interface CountryCodePickerEvent {
    data class SearchQueryChanged(val query: String) : CountryCodePickerEvent
    data class CountrySelected(val country: CountryData) : CountryCodePickerEvent
}

data class CountryCodePickerState(
    val query: String = "",
    val selectedCountry: CountryData? = null,
    val countries: List<CountryData> = emptyList()
)

class VerificationCodeViewModel : ViewModel() {

    private val _state = MutableStateFlow(VerificationCodeState())
    val state: StateFlow<VerificationCodeState> = _state

    fun onEvent(event: VerificationCodeEvent) {
        when (event) {
            is VerificationCodeEvent.CodeChanged -> {
                _state.value = _state.value.copy(code = event.code)
            }
            VerificationCodeEvent.ResendCode -> {}
            VerificationCodeEvent.CallMe -> {}
            is VerificationCodeEvent.Submit -> {}
        }
    }

    class Factory : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            if (modelClass.isAssignableFrom(VerificationCodeViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return VerificationCodeViewModel() as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

sealed interface VerificationCodeEvent {
    data class CodeChanged(val code: String) : VerificationCodeEvent
    data object ResendCode : VerificationCodeEvent
    data object CallMe : VerificationCodeEvent
    data object Submit : VerificationCodeEvent
}

data class VerificationCodeState(
    val code: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)
