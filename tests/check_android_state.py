from pathlib import Path
import json,subprocess,hashlib,os
ROOT=Path(__file__).resolve().parents[1]
base=ROOT/'build/host-regression/state';base.mkdir(parents=True,exist_ok=True)
toolchain=Path(os.environ.get('CRPS_ANDROID_TOOLCHAIN',str(ROOT/'build/host-regression/toolchain')))
src=base/'kotlin-src';src.mkdir(exist_ok=True)
root=ROOT/'app/src/main/java/cn/ntit/crps_compose'
files={}
for name,rel in [('MainViewModel.kt','viewmodel/MainViewModel.kt'),('DynamicData.kt','data/DynamicData.kt'),('StaticData.kt','data/StaticData.kt'),('TelemetryJson.kt','data/TelemetryJson.kt')]:
 text=(root/rel).read_text();(src/name).write_text(text);files[rel]=hashlib.sha256(text.encode()).hexdigest()
frame=(root/'ble/JsonFrameBuffer.kt').read_text();(src/'JsonFrameBuffer.kt').write_text(frame)
files['ble/JsonFrameBuffer.kt']=hashlib.sha256(frame.encode()).hexdigest()
def put(name,s): (src/name).write_text(s)
put('Context.kt',r'''
package android.content
open class Context {companion object {const val MODE_PRIVATE=0}}
class SharedPreferences {
 private val values=mutableMapOf<String,String?>()
 fun getString(k:String,d:String?)=values[k]?:d
 fun edit()=Editor()
 inner class Editor {fun putString(k:String,v:String?):Editor{values[k]=v;return this};fun apply(){}}
}
''')
put('Application.kt',r'''
package android.app
import android.content.*
open class Application:Context(){private val prefs=SharedPreferences();fun getSharedPreferences(n:String,m:Int)=prefs}
''')
put('Clock.kt',r'''
package android.os
object SystemClock {var now=0L;fun elapsedRealtime()=now;fun uptimeMillis()=now}
''')
put('Lifecycle.kt',r'''
package androidx.lifecycle
import android.app.Application
import kotlinx.coroutines.CoroutineScope
object ValidationRuntime {lateinit var scope:CoroutineScope}
open class AndroidViewModel(private val app:Application){fun <T:Application> getApplication():T=app as T}
val AndroidViewModel.viewModelScope:CoroutineScope get()=ValidationRuntime.scope
''')
put('Theme.kt',r'''
package cn.ntit.crps_compose.theme
import android.app.Application
object ThemeStore{fun loadPresetIndex(a:Application)=0;fun loadDarkMode(a:Application)=0;fun savePresetIndex(a:Application,v:Int){};fun saveDarkMode(a:Application,v:Int){}}
''')
ble=(root/'ble/BleManager.kt').read_text()
send=ble.split('    fun sendPowerCommand',1)[1].split('    // ==================== GATT 回调',1)[0]
parse=ble.split('    private fun tryParseAndDispatch',1)[1].split('    private fun handleUnexpectedDisconnect',1)[0]
stub=r'''
package cn.ntit.crps_compose.ble
import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.ValidationRuntime
import cn.ntit.crps_compose.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.util.UUID
object Log {fun w(t:String,m:String,e:Exception?=null){}}
class BluetoothGattCharacteristic(val device:String){var value=byteArrayOf();var writeType=0;companion object{const val WRITE_TYPE_DEFAULT=2}}
class BluetoothGattService(val device:String){val char=BluetoothGattCharacteristic(device);fun getCharacteristic(u:UUID)=char}
class FakeGatt(val device:String,val ready:Boolean){val writes=mutableListOf<Int>();fun getService(u:UUID)=if(ready)BluetoothGattService(device) else null;fun writeCharacteristic(c:BluetoothGattCharacteristic):Boolean{writes+=c.value[0].toInt();return true}}
typealias BluetoothGatt = FakeGatt
class BleManager {
 companion object {
  const val STATE_DISCONNECTED=0;const val STATE_SCANNING=1;const val STATE_CONNECTING=2;const val STATE_CONNECTED=3
  private val SERVICE_UUID=UUID.fromString("00001815-0000-1000-8000-00805f9b34fb")
  private val CHAR_CTRL_UUID=UUID.fromString("00002a57-0000-1000-8000-00805f9b34fb")
  private val CHAR_DATA_UUID=UUID.fromString("00002a56-0000-1000-8000-00805f9b34fb")
  private val CHAR_STATIC_UUID=UUID.fromString("00002a58-0000-1000-8000-00805f9b34fb")
  const val TAG="validation"
  var instance=BleManager();fun getInstance(a:Application)=instance
 }
 data class ScannedDevice(val name:String,val address:String,val rssi:Int)
 val connectionState=MutableStateFlow(STATE_DISCONNECTED)
 private val _connectionState=connectionState
 private var receivedDynamic=false
 private fun markReady(){}
 private var powerWriteCallback:((Boolean)->Unit)?=null
 val scannedDevices=MutableStateFlow<List<ScannedDevice>>(emptyList())
 val connectedDevice=MutableStateFlow<ScannedDevice?>(null)
 private val _dynamicData=MutableStateFlow<DynamicData?>(null);val dynamicData=_dynamicData.asStateFlow()
 private val _staticData=MutableStateFlow<StaticData?>(null);val staticData=_staticData.asStateFlow()
 private val _staticPackets=MutableSharedFlow<Unit>(extraBufferCapacity=1);val staticPackets=_staticPackets.asSharedFlow()
 var sessionId=0L
 val errorEvents=MutableSharedFlow<String>(extraBufferCapacity=16)
 private val scope get()=ValidationRuntime.scope
 private val dataBuffer=JsonFrameBuffer();private val staticBuffer=JsonFrameBuffer()
 var gatt:FakeGatt?=null;private var address:String?=null
 fun getLastConnectedAddress()=address;fun setLastConnectedAddress(s:String){address=s}
 fun isBluetoothEnabled()=true;fun startScan(){connectionState.value=STATE_SCANNING};fun stopScan(){connectionState.value=STATE_DISCONNECTED}
 fun connect(s:String){sessionId++;_dynamicData.value=null;_staticData.value=null;address=s;gatt=FakeGatt(s,true);connectedDevice.value=ScannedDevice(s,s,0);connectionState.value=STATE_CONNECTED}
 fun disconnect(){sessionId++;powerWriteCallback?.invoke(false);powerWriteCallback=null;_dynamicData.value=null;_staticData.value=null;gatt=null;connectionState.value=STATE_DISCONNECTED;dataBuffer.clear();staticBuffer.clear();connectedDevice.value=null}
 var deferWrites=false;val queued=mutableListOf<Runnable>()
 var autoCompleteWrites=true
 fun completeWrite(success:Boolean=true){val callback=powerWriteCallback;powerWriteCallback=null;callback?.invoke(success)}
 fun flushWrites(){val work=queued.toList();queued.clear();work.forEach{it.run();if(autoCompleteWrites)completeWrite()}}
 private fun enqueueGattOperation(r:Runnable){if(deferWrites)queued+=r else {r.run();if(autoCompleteWrites)completeWrite()}}
 private fun processNextGattOperation(){}
 private fun postError(s:String){errorEvents.tryEmit(s)}
 fun receiveStatic(s:String){tryParseAndDispatch(CHAR_STATIC_UUID,staticBuffer,s.toByteArray(),gatt)}
 fun receiveDynamic(s:String){tryParseAndDispatch(CHAR_DATA_UUID,dataBuffer,s.toByteArray(),gatt)}
 fun sendPowerCommand'''+send+'    private fun tryParseAndDispatch'+parse+'\n}\n'
put('BleAdapter.kt',stub)
put('ImpactChecks.kt',r'''
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.ValidationRuntime
import cn.ntit.crps_compose.ble.BleManager
import cn.ntit.crps_compose.ble.FakeGatt
import cn.ntit.crps_compose.ble.JsonFrameBuffer
import cn.ntit.crps_compose.viewmodel.MainViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.json.JSONObject
fun dynamic(rt:Int,pwr:Int=1,vout:Float=12f)=JSONObject().put("vin",230).put("iin",2).put("vout",vout).put("iout",10).put("pout",120).put("eff",26).put("fan",1000).put("t1",40).put("t2",42).put("rt",rt).put("pwr",pwr).put("si",0).put("sn",0).put("st",0).put("sf",0).toString()
fun stat(maker:String="ACME")=JSONObject().put("mid",maker).put("mm","CRPS").put("msn","123").put("mloc","rack").put("mdate","20260101").put("mrev","A1").put("prev",34).put("rimax",100).put("rpmax",1200).put("rt1",70).put("rt2",80).toString()
fun scenario(block:suspend TestScope.(MainViewModel,BleManager)->Unit)=runTest{
 Dispatchers.setMain(StandardTestDispatcher(testScheduler))
 ValidationRuntime.scope=backgroundScope
 BleManager.instance=BleManager();val b=BleManager.instance;val v=MainViewModel(Application())
 try{runCurrent();b.connect("A");runCurrent();block(v,b)}finally{Dispatchers.resetMain()}
}
fun frameChecks(){
 val sample=stat("ACME{Lab} \\\" 中文🙂").toByteArray()
 for(chunkSize in listOf(1,2,20,244,512)){
  val f=JsonFrameBuffer();val frames=mutableListOf<String>()
  for(part in sample.toList().chunked(chunkSize))f.consume(part.toByteArray(),100){frames+=it}
  check(frames==listOf(String(sample,Charsets.UTF_8)))
 }
 val f=JsonFrameBuffer();val frames=mutableListOf<String>()
 fun feed(bytes:ByteArray,time:Long=100){f.consume(bytes,time){frames+=it}}
 val atLimit=("{\"x\":\""+"a".repeat(1016)+"\"}").toByteArray();check(atLimit.size==1024)
 feed(atLimit);check(frames.size==1);frames.clear()
 val oversized=("{\"x\":\""+"a".repeat(1017)+"\"}").toByteArray()
 for(part in oversized.toList().chunked(20))feed(part.toByteArray())
 check(frames.isEmpty());feed(sample);check(frames.size==1);frames.clear()
 feed("{".repeat(100000).toByteArray());feed(sample);check(frames.size==1);frames.clear()
 repeat(200){feed("{".repeat(20).toByteArray())};check(frames.isEmpty())
 feed(sample,2100);check(frames.size==1);frames.clear()
 feed("{\"x\":\"".toByteArray()+byteArrayOf(0xFF.toByte())+"\"}".toByteArray())
 check(frames.isEmpty());feed(sample);check(frames.size==1);frames.clear()
 feed("{\"x\":\"unfinished".toByteArray(),0);feed(sample,2000);check(frames.size==1);frames.clear()
 feed("{".toByteArray());f.clear();feed(sample);check(frames.size==1);frames.clear()
 feed(sample+sample);check(frames.size==2)
 println("PASS F06 byte framing: all UTF-8 split boundaries, escaped braces, exact limit, overflow/timeout recovery, invalid encoding and disconnect clear")
}
fun main(){
 frameChecks()
 scenario {v,b->
  for(i in 0 until 8402){
   SystemClock.now=i*250L;b.receiveDynamic(dynamic(i/4,1,12f+(i%2)*0.01f));runCurrent()
   check(v.xTimes.size==v.yVout.size && v.xTimes.size==v.yT2.size)
   check(v.xTimes.size<=7200)
   if(i==7200)check(v.xTimes.size==6000&&v.xTimes.first()==300.25f)
  }
  check(v.xTimes.size==6000&&v.xTimes.first()==600.5f)
  println("PASS F04 original VM trims X and all Y together at both thresholds")
 }
 scenario {v,b->
  b.receiveStatic(stat());runCurrent()
  repeat(6){advanceTimeBy(5000);b.receiveStatic(stat());runCurrent();check(!v.staticInterrupted.value)}
  advanceTimeBy(10001);runCurrent();check(v.staticInterrupted.value)
  b.receiveStatic(stat());runCurrent();check(!v.staticInterrupted.value)
  println("PASS F05 identical static packets refresh watchdog; real silence alarms and next identical packet clears it")
 }
 scenario {v,b->
  advanceTimeBy(10001);runCurrent();check(v.dataInterrupted.value&&v.staticInterrupted.value)
  b.receiveDynamic(dynamic(1));b.receiveStatic(stat());runCurrent()
  check(!v.dataInterrupted.value&&!v.staticInterrupted.value)
  v.sendPower(true);runCurrent();check(b.gatt!!.writes==listOf(1));b.receiveDynamic(dynamic(2));runCurrent()
  check(!v.powerPending.value)
  println("PASS F10 first-packet watchdog starts at connection and healthy data/control recover")
 }
 scenario {v,b->
  b.receiveDynamic(dynamic(1));b.receiveStatic(stat("A-MAKER"));runCurrent();check(v.xTimes.size==1)
  v.sendPower(false);runCurrent();advanceTimeBy(5001);runCurrent();check(v.powerTimeout.value)
  v.disconnect();runCurrent();v.connect("B");runCurrent()
  check(v.dynamicData.value==null&&v.staticData.value==null&&v.xTimes.isEmpty())
  check(!v.powerTimeout.value&&!v.powerPending.value)
  v.confirmPowerRetry();runCurrent();check(b.gatt!!.writes.isEmpty())
  b.receiveDynamic(dynamic(3));runCurrent();v.sendPower(true);runCurrent();check(b.gatt!!.writes==listOf(1))
  println("PASS F11 switch clears telemetry/history/pending retry; new explicit command works")
 }
 scenario {v,b->
  b.receiveStatic(stat("OLD"));v.disconnect();v.connect("B");runCurrent()
  check(v.staticData.value==null)
  b.receiveDynamic(dynamic(3));runCurrent();b.deferWrites=true;val oldGatt=b.gatt!!;v.sendPower(true);check(b.queued.size==1)
  v.disconnect();v.connect("C");runCurrent();b.flushWrites()
  check(b.gatt!!.writes.isEmpty()&&oldGatt.writes.isEmpty())
  println("PASS F11 late parse and queued old-GATT command cannot reach the new session")
 }
 scenario {v,b->
  b.receiveStatic(stat("ACME{Lab"));runCurrent();check(v.staticData.value!!.mid=="ACME{Lab")
  b.receiveStatic("{".repeat(5000));runCurrent();b.receiveStatic(stat("Recovered 中文"));runCurrent()
  check(v.staticData.value!!.mid=="Recovered 中文")
  println("PASS F06 original parser dispatches brace-containing text and valid frames after oversized input")
 }
 scenario {v,b->
  b.receiveDynamic(dynamic(1));runCurrent();v.sendPower(false);runCurrent()
  check(v.powerPending.value)
  for(bad in listOf("{}", "{\"pwr\":0}", JSONObject(dynamic(2,0)).put("pwr","0").toString(),
    JSONObject(dynamic(2,0)).put("pwr",JSONObject.NULL).toString(), JSONObject(dynamic(2,0)).put("st",0.5).toString(),
    JSONObject(dynamic(2,0)).put("proto",2).toString(), JSONObject(dynamic(2,0)).put("vin","NaN").toString())) {
   b.receiveDynamic(bad);runCurrent();check(v.powerPending.value&&v.dynamicData.value!!.pwr==1)
  }
  b.receiveDynamic(dynamic(2,0));runCurrent();check(!v.powerPending.value)
  println("PASS A03 missing, mistyped and unsupported packets cannot confirm OFF; complete telemetry can")
 }
 scenario {v,b->
  b.receiveStatic("{}");runCurrent();check(v.staticData.value==null)
  val partial=JSONObject(dynamic(1)).put("valid",false).put("seq",1).put("vout",JSONObject.NULL).put("st",JSONObject.NULL)
  b.receiveDynamic(partial.toString());runCurrent()
  check(v.dynamicData.value!!.vout==null&&v.dynamicData.value!!.vin==230f&&v.dynamicData.value!!.st==null)
  check(v.yVout.single().isNaN())
  advanceTimeBy(10001);b.receiveDynamic(partial.put("rt",2).toString());runCurrent();check(v.dataInterrupted.value)
  b.receiveDynamic(JSONObject(dynamic(3)).put("valid",true).put("seq",2).toString());runCurrent()
  check(!v.dataInterrupted.value&&v.yVout.last().isFinite())
  println("PASS F01/A03 partial quality preserves known fields, gaps unknown chart values, stale sequence expires and new sample recovers")
 }
 scenario {v,b->
  fun packet(seq:Int,power:Int,ack:Int)=JSONObject(dynamic(seq,power)).put("seq",seq).put("ack",ack).toString()
  b.receiveDynamic(packet(1,1,0));runCurrent();b.deferWrites=true;v.sendPower(false);runCurrent()
  b.receiveDynamic(packet(2,0,1));runCurrent();check(v.powerPending.value&&b.gatt!!.writes.isEmpty())
  b.autoCompleteWrites=false;b.flushWrites();runCurrent()
  b.receiveDynamic(packet(3,0,1));runCurrent();check(v.powerPending.value)
  b.completeWrite();b.receiveDynamic(packet(4,0,0));runCurrent();check(v.powerPending.value)
  b.receiveDynamic(packet(5,0,1));runCurrent();check(!v.powerPending.value)
  println("PASS command confirmation requires completed write, matching command token and observed target state")
 }
 scenario {v,b->
  b.receiveDynamic(JSONObject(dynamic(1,0)).put("ack",0).toString());runCurrent()
  b.deferWrites=true;v.sendPower(true);v.sendPower(false);runCurrent()
  check(b.queued.size==2);b.flushWrites();runCurrent();check(b.gatt!!.writes==listOf(1,0))
  b.receiveDynamic(JSONObject(dynamic(2,0)).put("ack",1).toString());runCurrent();check(v.powerPending.value)
  b.receiveDynamic(JSONObject(dynamic(3,0)).put("ack",2).toString());runCurrent();check(!v.powerPending.value)
  println("PASS rapid ON/OFF retains last intent; earlier acknowledgement cannot confirm cancellation")
 }
 scenario {v,b->
  val repeated=JSONObject(stat()).put("seq",1).toString()
  b.receiveStatic(repeated);runCurrent()
  repeat(10){advanceTimeBy(5000);b.receiveStatic(repeated);runCurrent()}
  check(v.staticInterrupted.value)
  b.receiveStatic(JSONObject(stat()).put("seq",2).toString());runCurrent();check(!v.staticInterrupted.value)
  println("PASS static sampling sequence expires at 45 seconds; a new sweep restores identity")
 }
 println("Original VM/data/selected BLE functions tested; Android and GATT are adapters. No physical device or Compose rendering.")
}
''')
manifest=json.loads((toolchain/'dependency-manifest.json').read_text());java=manifest['java']
jars=[r['path'] for r in manifest['jars']];cp=os.pathsep.join(jars)
out=base/'impact-tests.jar'
cmd=[java,'-cp',cp,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',cp,'-jvm-target','11','-d',str(out)]+[str(p) for p in src.glob('*.kt')]
compile_result=subprocess.run(cmd,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
(base/'kotlin-compile.log').write_text(compile_result.stdout);print(compile_result.stdout,flush=True)
assert compile_result.returncode==0
run=subprocess.run([java,'-ea','-cp',str(out)+os.pathsep+cp,'ImpactChecksKt'],text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=60)
(base/'kotlin-impact-results.txt').write_text(run.stdout);print(run.stdout,flush=True)
(base/'source-extraction-hashes.json').write_text(json.dumps(files,indent=2))
assert run.returncode==0
