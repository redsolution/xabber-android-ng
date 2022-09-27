package com.xabber.presentation.application.fragments.chat.message

/**
 * Base interface common for the registered manager.
 *
 * @author alexander.ivanov
 */
interface BaseManagerInterface

interface OnClearListener : BaseManagerInterface {
    /**
     * Clear all local data.
     * WILL BE CALLED FROM BACKGROUND THREAD. DON'T CHANGE OR ACCESS
     * APPLICATION'S DATA HERE!
     */
    fun onClear()
}

interface OnCloseListener : BaseManagerInterface {
    /**
     * Called after service have been stopped.
     * This function will be call from UI thread.
     */
    fun onClose()
}

interface OnInitializedListener : BaseManagerInterface {
    /**
     * Called once on service start and all data were loaded.
     * Called from UI thread.
     */
    fun onInitialized()
}

interface OnLoadListener : BaseManagerInterface {
    /**
     * Called after service has been started before
     * [OnInitializedListener].
     * WILL BE CALLED FROM BACKGROUND THREAD. DON'T CHANGE OR ACCESS
     * APPLICATION'S DATA HERE!
     * Used to load data from DB and post request to UI thread to update data.
     */
    fun onLoad()
}

interface OnLowMemoryListener : BaseManagerInterface {
    /**
     * Clears all caches.
     */
    fun onLowMemory()
}

interface OnTimerListener : BaseManagerInterface {
    /**
     * Called after at least [.DELAY] milliseconds.
     */
    fun onTimer()

    companion object {
        const val DELAY = 1000
    }
}

interface OnUnloadListener : BaseManagerInterface {
    /**
     * Called before application to be killed after
     * [OnCloseListener.onClose] has been called.
     * WILL BE CALLED FROM BACKGROUND THREAD. DON'T CHANGE OR ACCESS
     * APPLICATION'S DATA HERE!
     */
    fun onUnload()
}

interface OnWipeListener : BaseManagerInterface {
    /**
     * Wipe all sensitive application data.
     * WILL BE CALLED FROM BACKGROUND THREAD. DON'T CHANGE OR ACCESS
     * APPLICATION'S DATA HERE!
     */
    fun onWipe()
}
