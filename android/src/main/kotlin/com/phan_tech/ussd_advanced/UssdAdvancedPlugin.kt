package com.phan_tech.ussd_advanced

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.telephony.TelephonyManager.UssdResponseCallback
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.NonNull
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.phan_tech.ussd_advanced.Utils.isAccessibilitySettingsOn
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.*
import io.flutter.plugin.common.EventChannel.EventSink
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener

import java.util.concurrent.CompletableFuture


/** UssdAdvancedPlugin */
class UssdAdvancedPlugin: FlutterPlugin, MethodCallHandler, ActivityAware, BasicMessageChannel.MessageHandler<String?>, ActivityResultListener, EventChannel.StreamHandler  {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel
  private var context: Context? = null
  private var activity: Activity? = null
  private var senderActivity: Activity? = null
  private val ussdApi: USSDApi = USSDController
  private var event: AccessibilityEvent? = null


  private lateinit var basicMessageChannel: BasicMessageChannel<String>
  private lateinit var eventChannel: EventChannel

  private var pendingResult: Result? = null
  val REQUEST_CODE_FOR_ACCESSIBILITY = 167
  private var accessibilityReceiver: AccessibilityReceiver? = null
  private var mActivity: Activity? = null





  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "method.com.phan_tech/ussd_advanced")
    channel.setMethodCallHandler(this)
    this.context = flutterPluginBinding.applicationContext
    basicMessageChannel  = BasicMessageChannel(
      flutterPluginBinding.binaryMessenger,
      "message.com.phan_tech/ussd_advanced", StringCodec.INSTANCE
    )

    eventChannel = EventChannel(
            flutterPluginBinding.binaryMessenger,
            "event.com.phan_tech/ussd_advanced")
    eventChannel.setStreamHandler(this)

  }


  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
    if (requestCode == REQUEST_CODE_FOR_ACCESSIBILITY) {
      if (resultCode == Activity.RESULT_OK) {
        pendingResult!!.success(true)
      } else if (resultCode == Activity.RESULT_CANCELED) {
        pendingResult!!.success(Utils.isAccessibilitySettingsOn(context))
      } else {
        pendingResult!!.success(false)
      }
      return true
    }
    return false
  }

  override fun onListen(arguments: Any?, events: EventSink?) {
    if (isAccessibilitySettingsOn(context)) {
      /// Set up receiver
      val intentFilter = IntentFilter()
      intentFilter.addAction(AccessibilityListener.ACCESSIBILITY_INTENT)
      accessibilityReceiver = AccessibilityReceiver(events)
      context!!.registerReceiver(accessibilityReceiver, intentFilter)

      /// Set up listener intent
      val listenerIntent = Intent(context, AccessibilityListener::class.java)
      context!!.startService(listenerIntent)
      Log.i("AccessibilityPlugin", "Started the accessibility tracking service.")
    }
  }

  override fun onCancel(arguments: kotlin.Any?){
    context!!.unregisterReceiver(accessibilityReceiver)
    accessibilityReceiver = null
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
    mActivity = binding.activity
    binding.addActivityResultListener(this)
  }

  override fun onDetachedFromActivity() {
    activity = null
    mActivity = null
  }


  override fun onDetachedFromActivityForConfigChanges() {
    senderActivity = null
    mActivity = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    senderActivity = binding.activity
  }

  private fun setListener(){
    Log.d("onMethodCall","setListener")
    basicMessageChannel.setMessageHandler(this)
  }

  override fun onMessage(message: String?, reply: BasicMessageChannel.Reply<String?>) {
    if(message != null){
      USSDController.send2(message, event!!){
        event = AccessibilityEvent.obtain(it)
        try {
          if(it.text.isNotEmpty()) {
            reply.reply(it.text.first().toString())
          }else{
            reply.reply(null)
          }
        } catch (e: Exception){}

      }
    }
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    Log.d("onMethodCall","onMethodCall1")
    var subscriptionId:Int = 1
    var code:String? = ""
    pendingResult = result

    Log.d("onMethodCall","method=="+call.method.toString())
    if(call.method == "sendUssd" ||call.method == "sendAdvancedUssd" ||call.method == "multisessionUssd"){
      val subscriptionIdInteger = call.argument<Int>("subscriptionId")
        ?: throw RequestParamsException(
          "Incorrect parameter type: `subscriptionId` must be an int"
        )
      subscriptionId = subscriptionIdInteger
      if (subscriptionId < -1 ) {
        throw RequestParamsException(
          "Incorrect parameter value: `subscriptionId` must be >= -1"
        )
      }
      code = call.argument<String>("code")
      if (code == null) {
        throw RequestParamsException("Incorrect parameter type: `code` must be a String")
      }
      if (code.isEmpty()) {
        throw RequestParamsException(
          "Incorrect parameter value: `code` must not be an empty string"
        )
      }
    }
    Log.d("onMethodCall","onMethodCall2")

    when (call.method) {
      "sendUssd" -> {
        result.success(defaultUssdService(code!!, subscriptionId))

      }
      "sendAdvancedUssd" -> {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
          val res = singleSessionUssd(code!!, subscriptionId)
          if(res != null){

            res.exceptionally { e: Throwable? ->
              if (e is RequestExecutionException) {
                result.error(
                  RequestExecutionException.type, e.message, null
                )
              } else {
                result.error(RequestExecutionException.type, e?.message, null)
              }
              null
            }.thenAccept(result::success);

          }else{
            result.success(res);
          }
        }else{
          result.success(defaultUssdService(code!!, subscriptionId))
        }
      }
      "multisessionUssd" -> {
        Log.d("onMethodCall","multisessionUssd fun")
        // check permissions
        if (ContextCompat.checkSelfPermission(context!!, android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
          Log.d("onMethodCall","multisessionUssd mba 1")
          if (!ActivityCompat.shouldShowRequestPermissionRationale(activity!!, android.Manifest.permission.CALL_PHONE)) {
            Log.d("onMethodCall","multisessionUssd mba 2")
            ActivityCompat.requestPermissions(activity!!, arrayOf(android.Manifest.permission.CALL_PHONE), 2)
          }
        }else if (ContextCompat.checkSelfPermission(this.context!!, android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
          Log.d("onMethodCall","READ_PHONE_STATE=="+ContextCompat.checkSelfPermission(this.context!!, android.Manifest.permission.READ_PHONE_STATE).toString())
          Log.d("onMethodCall","multisessionUssd mba 3")
          if (!ActivityCompat.shouldShowRequestPermissionRationale(activity!!, android.Manifest.permission.READ_PHONE_STATE)) {
            Log.d("onMethodCall","multisessionUssd mba 4")
            ActivityCompat.requestPermissions(activity!!, arrayOf(android.Manifest.permission.READ_PHONE_STATE), 2)
          }
        }
        else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
          Log.d("onMethodCall","multisessionUssd mba 5")
          multisessionUssd(code!!, subscriptionId, result)

        }else{
          Log.d("onMethodCall","multisessionUssd else")
          result.success(defaultUssdService(code!!, subscriptionId))
        }

      }
      "multisessionUssdCancel" ->{
        multisessionUssdCancel()
      }
       "isAccessibilityPermissionEnabled" ->{
        result.success(isAccessibilitySettingsOn(context))
      }
      "requestAccessibilityPermission" ->{
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        mActivity!!.startActivityForResult(intent, REQUEST_CODE_FOR_ACCESSIBILITY)
      }
      else -> {
        result.notImplemented()
      }
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    eventChannel.setStreamHandler(null)
  }



  private class RequestExecutionException internal constructor(override var message: String) :
    Exception() {
    companion object {
      var type = "ussd_plugin_ussd_execution_failure"
    }
  }

  private class RequestParamsException internal constructor(override var message: String) :
    Exception() {
    companion object {
      var type = "ussd_plugin_incorrect__parameters"
    }
  }




  // for android 8+
  private fun singleSessionUssd(ussdCode:String, subscriptionId:Int) : CompletableFuture<String>?{
    // use defaulft sim
    val _useDefault: Boolean = subscriptionId == -1

    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
      var res:CompletableFuture<String> = CompletableFuture<String>()
      // check permissions
      if (ContextCompat.checkSelfPermission(this.context!!, android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity!!, android.Manifest.permission.CALL_PHONE)) {
        } else {
          ActivityCompat.requestPermissions(activity!!, arrayOf(android.Manifest.permission.CALL_PHONE), 2)
        }
      }

      // get TelephonyManager
      val tm = this.context!!.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

      val simManager: TelephonyManager = tm.createForSubscriptionId(subscriptionId)

      // callback
      val callback =
        object : UssdResponseCallback() {
          override fun onReceiveUssdResponse(
            telephonyManager: TelephonyManager, request: String, response: CharSequence
          ) {
            res.complete(response.toString())
          }

          override fun onReceiveUssdResponseFailed(
            telephonyManager: TelephonyManager, request: String, failureCode: Int
          ) {
            when (failureCode) {
              TelephonyManager.USSD_ERROR_SERVICE_UNAVAIL -> {
                res.completeExceptionally(RequestExecutionException("USSD_ERROR_SERVICE_UNAVAIL"))
              }
              TelephonyManager.USSD_RETURN_FAILURE -> {
                res.completeExceptionally(RequestExecutionException("USSD_RETURN_FAILURE"))
              }
              else -> {
                res.completeExceptionally(RequestExecutionException("unknown error"))
              }
            }
          }
        }

      if(_useDefault){
        tm.sendUssdRequest(
          ussdCode,
          callback,
          Handler(Looper.getMainLooper())
        )

      }else{
        simManager.sendUssdRequest(
          ussdCode,
          callback,
          Handler(Looper.getMainLooper())
        )
      }


      return res
    }else{
      // if sdk is less than 26
      defaultUssdService(ussdCode, subscriptionId)
      return  null
    }

  }

  private fun multisessionUssd(ussdCode:String, subscriptionId:Int, @NonNull result: Result){
    Log.d("onMethodCall","multisessionUssd cr 1")
    var slot = subscriptionId
    if(subscriptionId == -1){
      slot = 0
    }

    ussdApi.callUSSDInvoke(activity!!, ussdCode, slot, object : USSDController.CallbackInvoke {

      override fun responseInvoke(ev: AccessibilityEvent) {
        event = AccessibilityEvent.obtain(ev)
        Log.d("onMethodCall","responseInvoke setListener")
        setListener()

        try {
          if(ev.text.isNotEmpty()) {
            Log.d("onMethodCall","responseInvoke ev.text.isNotEmpty()"+ev.text.first().toString())
            result.success(ev.text.first().toString())
          }else{
            Log.d("onMethodCall","responseInvoke success null")
            result.success(null)
          }
        }catch (e: Exception){
          Log.d("onMethodCall","responseInvoke error "+e.toString())
        }
      }

      override fun over(message: String) {

        try {
          Log.d("onMethodCall","callUSSDInvoke over ")
          basicMessageChannel.setMessageHandler(null)
          basicMessageChannel.send(message)
          result.success("OVER:"+message)
        }catch (e: Exception){
          Log.d("onMethodCall","callUSSDInvoke over error=="+e.toString())
        }

      }
    })
  }

  private fun multisessionUssdCancel(){
    if(event != null){
      basicMessageChannel.setMessageHandler(null)
      ussdApi.cancel2(event!!);
    }
  }

  private val simSlotName = arrayOf(
    "extra_asus_dial_use_dualsim",
    "com.android.phone.extra.slot",
    "slot",
    "simslot",
    "sim_slot",
    "subscription",
    "Subscription",
    "phone",
    "com.android.phone.DialingMode",
    "simSlot",
    "slot_id",
    "simId",
    "simnum",
    "phone_type",
    "slotId",
    "slotIdx"
  )

  // multiple for all
  private fun defaultUssdService(ussdCode:String, subscriptionId:Int){
    if (ContextCompat.checkSelfPermission(this.context!!, android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
      if (ActivityCompat.shouldShowRequestPermissionRationale(activity!!, android.Manifest.permission.CALL_PHONE)) {
      } else {
        ActivityCompat.requestPermissions(activity!!, arrayOf(android.Manifest.permission.CALL_PHONE), 2)
      }
    }
    try {
      // use defaulft sim
      val _useDefault: Boolean = subscriptionId == -1

      val sim:Int = subscriptionId -1
      var number:String = ussdCode;
      number = number.replace("#", "%23");
      if (!number.startsWith("tel:")) {
        number = String.format("tel:%s", number);
      }
      val intent =
        Intent(if (isTelephonyEnabled()) Intent.ACTION_CALL else Intent.ACTION_VIEW)
      intent.data = Uri.parse(number)

      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);


      if(!_useDefault){
        intent.putExtra("com.android.phone.force.slot", true);
        intent.putExtra("Cdma_Supp", true);

        for (s in simSlotName) intent.putExtra(s, sim)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
          if (ContextCompat.checkSelfPermission(this.context!!, android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity!!, android.Manifest.permission.READ_PHONE_STATE)) {
            } else {
              ActivityCompat.requestPermissions(activity!!, arrayOf(android.Manifest.permission.READ_PHONE_STATE), 2)
            }
          }
          val telecomManager = this.context!!.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

          val phoneAccountHandleList = telecomManager.callCapablePhoneAccounts
          if (phoneAccountHandleList != null && phoneAccountHandleList.isNotEmpty())
            intent.putExtra("android.telecom.extra.PHONE_ACCOUNT_HANDLE",
              phoneAccountHandleList[sim]
            );
        }
      }


      this.context!!.startActivity(intent)

    } catch (e: Exception) {
      throw e
    }
  }

  private fun isTelephonyEnabled(): Boolean {
    val tm = this.context!!.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    return tm.phoneType != TelephonyManager.PHONE_TYPE_NONE

  }




}
