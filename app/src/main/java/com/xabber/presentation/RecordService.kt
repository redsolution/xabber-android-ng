package com.xabber.presentation

import android.app.Service
import android.content.Context
import kotlin.jvm.Volatile
import android.content.Intent
import android.os.*
import android.util.Log


class RecordService : Service() {
    private val TAG = RecordService::class.java.name

    //Looper
    @Volatile
    private var mServiceLooper: Looper? = null

    @Volatile
    private var mServiceHandler: ServiceHandler? = null

    //private OpusPlayer mPlayer;
 //   private var mRecorder: OpusRecorder? = null

    //private OpusConverter mConverter;
    //private OpusTrackInfo mTrackInfo;
  //  private var mEvent: OpusEvent? = null
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
//        mEvent = OpusEvent(applicationContext)
//        mRecorder = OpusRecorder.getInstance()
//        mRecorder.setEventSender(mEvent)

        //start looper in onCreate() instead of onStartCommand()
        val thread = HandlerThread("RecordServiceHandler")
        thread.start()
        mServiceLooper = thread.looper
        mServiceHandler = ServiceHandler(mServiceLooper)
    }

    override fun onDestroy() {
        //quit looper
        mServiceLooper!!.quit()
    //    mRecorder.release()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val msg = mServiceHandler!!.obtainMessage()
        msg.arg1 = startId
        msg.obj = intent
        mServiceHandler!!.sendMessage(msg)
        return START_NOT_STICKY
    }

    fun onHandleIntent(intent: Intent?) {
        if (intent != null) {
            val action = intent.action
            if (ACTION_OPUSSERVICE == action) {
             //   mRecorder = OpusRecorder.getInstance()
                val request = intent.getIntExtra(EXTRA_CMD, 0)
                val fileName: String?
                when (request) {
                    CMD_RECORD -> {
                        fileName = intent.getStringExtra(EXTRA_FILE_NAME)
                        handleActionRecord(fileName)
                    }
                    CMD_STOP_RECORDING -> {
                        handleActionStopRecording()
                        stopSelf()
                    }
                    else -> Log.e(TAG, "Unknown intent CMD,discarded!")
                }
            } else {
                Log.e(TAG, "Unknown intent action,discarded!")
            }
        }
    }

    private fun handleActionRecord(fileName: String?) {
 //       mRecorder.startRecording(fileName)
    }

    private fun handleActionStopRecording() {
 //       mRecorder.stopRecording()
    }

    private inner class ServiceHandler(looper: Looper?) : Handler(looper!!) {
        override fun handleMessage(msg: Message) {
            onHandleIntent(msg.obj as Intent)
            //stopSelf()
        }
    }

    companion object {
        //This server
        private const val ACTION_OPUSSERVICE = "top.oply.opuslib.action.OPUSSERVICE"
        private const val EXTRA_FILE_NAME = "FILE_NAME"
        private const val EXTRA_FILE_NAME_OUT = "FILE_NAME_OUT"
        private const val EXTRA_OPUS_CODING_OPTION = "OPUS_CODING_OPTION"
        private const val EXTRA_CMD = "CMD"
        private const val EXTRA_SEEKFILE_SCALE = "SEEKFILE_SCALE"
        private const val CMD_PLAY = 10001
        private const val CMD_PAUSE = 10002
        private const val CMD_STOP_PLAYING = 10003
        private const val CMD_TOGGLE = 10004
        private const val CMD_SEEK_FILE = 10005
        private const val CMD_GET_TRACK_INFO = 10006
        private const val CMD_ENCODE = 20001
        private const val CMD_DECODE = 20002
        private const val CMD_RECORD = 30001
        private const val CMD_STOP_RECORDING = 30002
        private const val CMD_RECORD_TOGGLE = 30003
        fun record(context: Context, fileName: String?) {
            val intent = Intent(context, RecordService::class.java)
            intent.action = ACTION_OPUSSERVICE
            intent.putExtra(EXTRA_CMD, CMD_RECORD)
            intent.putExtra(EXTRA_FILE_NAME, fileName)
            context.startService(intent)
        }

        fun stopRecording(context: Context) {
            val intent = Intent(context, RecordService::class.java)
            intent.action = ACTION_OPUSSERVICE
            intent.putExtra(EXTRA_CMD, CMD_STOP_RECORDING)
            context.startService(intent)
        }
    }
}