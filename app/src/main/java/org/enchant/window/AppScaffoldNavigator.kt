package org.enchant.window

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AppScaffoldNavigator<T> {
    val canNavigateBack: StateFlow<Boolean>
    val currentDetail: StateFlow<T?>
    val scaffoldDirective: ScaffoldDirective

    fun navigateTo(detail: T)
    fun navigateBack(): Boolean
    fun showList()
}

data class ScaffoldDirective(
    val maxHorizontalPartitions: Int = 1,
    val hasFoldable: Boolean = false,
    val defaultPanePreferredWidth: Int = 400
)

class AppScaffoldNavigatorImpl<T : Any> : AppScaffoldNavigator<T> {

    private val _currentDetail = MutableStateFlow<T?>(null)
    override val currentDetail: StateFlow<T?> = _currentDetail.asStateFlow()

    private val _canNavigateBack = MutableStateFlow(false)
    override val canNavigateBack: StateFlow<Boolean> = _canNavigateBack.asStateFlow()

    override val scaffoldDirective: ScaffoldDirective = ScaffoldDirective(maxHorizontalPartitions = 1)

    override fun navigateTo(detail: T) {
        _currentDetail.value = detail
        _canNavigateBack.value = true
    }

    override fun navigateBack(): Boolean {
        _currentDetail.value = null
        _canNavigateBack.value = false
        return true
    }

    override fun showList() {
        _currentDetail.value = null
        _canNavigateBack.value = false
    }
}