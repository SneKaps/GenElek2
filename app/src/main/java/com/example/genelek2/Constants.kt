package com.example.genelek2

class Constants private constructor(){

    companion object{
        //const val INTENT_ACTION_DISCONNECT: String = BuildConfig.APPLICATION_ID + ".Disconnect"
        //const val NOTIFICATION_CHANNEL: String = BuildConfig.APPLICATION_ID + ".Channel"
        //const val INTENT_CLASS_MAIN_ACTIVITY: String = BuildConfig.APPLICATION_ID + ".MainActivity"
         //val INTENT_ACTION_DISCONNECT: String = fragment.context + ".Disconnect"
         const val INTENT_ACTION_DISCONNECT: String = "com.example.genelek2" + ".Disconnect"
        const val NOTIFICATION_CHANNEL: String = "com.example.genelek2" + ".Channel"
        const val INTENT_CLASS_MAIN_ACTIVITY: String = "com.example.genelek2" + ".MainActivity"




        // values have to be unique within each app
        const val NOTIFY_MANAGER_START_FOREGROUND_SERVICE: Int = 1001
    }



    private fun Constants() {}

}