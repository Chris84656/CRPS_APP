"""Execute the real GATT callbacks, readiness gate and operation queue with SDK adapters."""
from pathlib import Path
import os, json, re, subprocess

ROOT = Path(__file__).resolve().parents[1]
build = ROOT/'build/host-regression/connection'
build.mkdir(parents=True, exist_ok=True)
toolchain = Path(os.environ.get('CRPS_ANDROID_TOOLCHAIN', str(ROOT/'build/host-regression/toolchain')))
package = ROOT/'app/src/main/java/cn/ntit/crps_compose'
source = (package/'ble/BleManager.kt').read_text()
def function(name):
    match = re.search(r'^    private fun '+name+r'\([^\n]*\)[^\n]*\{\n.*?^    \}', source, re.M | re.S)
    assert match, name
    return match.group()
callbacks = source.split('    private val gattCallback =',1)[1].split('    // ==================== 内部方法',1)[0]
for rel in ['data/DynamicData.kt','data/StaticData.kt','data/TelemetryJson.kt','ble/JsonFrameBuffer.kt']:
    (build/Path(rel).name).write_text((package/rel).read_text())
adapter = r'''
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package cn.ntit.crps_compose.connectiontests
import cn.ntit.crps_compose.data.*
import cn.ntit.crps_compose.ble.JsonFrameBuffer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.json.JSONObject
import java.util.UUID
import java.util.Collections
object Log {fun w(t:String,m:String,e:Exception?=null){}}
object SystemClock {fun elapsedRealtime()=100L}
object BluetoothProfile {const val STATE_CONNECTED=2;const val STATE_DISCONNECTED=0}
class BluetoothGattCharacteristic(val uuid:UUID,var properties:Int=18){
 var value:ByteArray?=null;var writeType=0;var hasDescriptor=true
 val descriptor=BluetoothGattDescriptor(this)
 fun getDescriptor(id:UUID)=descriptor.takeIf{hasDescriptor}
 companion object{const val PROPERTY_NOTIFY=16;const val PROPERTY_WRITE=8;const val PROPERTY_READ=2}
}
class BluetoothGattDescriptor(val characteristic:BluetoothGattCharacteristic){
 var value=byteArrayOf();companion object{val ENABLE_NOTIFICATION_VALUE=byteArrayOf(1,0)}
}
class BluetoothGattService{
 val chars=mutableMapOf<UUID,BluetoothGattCharacteristic>()
 fun getCharacteristic(id:UUID)=chars[id]
}
class BluetoothGatt{
 companion object{const val GATT_SUCCESS=0}
 val service=BluetoothGattService();val operations=mutableListOf<String>()
 var servicePresent=true;var mtuResult=true;var discoverResult=true;var descriptorResult=true
 fun getService(id:UUID)=service.takeIf{servicePresent}
 fun requestMtu(mtu:Int):Boolean{operations+="mtu";return mtuResult}
 fun discoverServices():Boolean{operations+="discover";return discoverResult}
 fun setCharacteristicNotification(c:BluetoothGattCharacteristic,yes:Boolean)=true
 fun writeDescriptor(d:BluetoothGattDescriptor):Boolean{operations+="descriptor";return descriptorResult}
 fun readCharacteristic(c:BluetoothGattCharacteristic):Boolean{operations+="read";return true}
 fun disconnect(){operations+="disconnect"};fun close(){operations+="close"}
}
open class BluetoothGattCallback{
 open fun onConnectionStateChange(g:BluetoothGatt,status:Int,newState:Int){}
 open fun onMtuChanged(g:BluetoothGatt,mtu:Int,status:Int){}
 open fun onServicesDiscovered(g:BluetoothGatt,status:Int){}
 open fun onCharacteristicRead(g:BluetoothGatt,c:BluetoothGattCharacteristic,status:Int){}
 open fun onCharacteristicChanged(g:BluetoothGatt,c:BluetoothGattCharacteristic){}
 open fun onCharacteristicWrite(g:BluetoothGatt,c:BluetoothGattCharacteristic,status:Int){}
 open fun onDescriptorWrite(g:BluetoothGatt,d:BluetoothGattDescriptor,status:Int){}
}
class Harness{
 val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 val SERVICE_UUID=UUID.fromString("00001815-0000-1000-8000-00805f9b34fb")
 val CHAR_DATA_UUID=UUID.fromString("00002a56-0000-1000-8000-00805f9b34fb")
 val CHAR_CTRL_UUID=UUID.fromString("00002a57-0000-1000-8000-00805f9b34fb")
 val CHAR_STATIC_UUID=UUID.fromString("00002a58-0000-1000-8000-00805f9b34fb")
 val CCCD_UUID=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
 val STATE_CONNECTING=2;val STATE_CONNECTED=3;val STATE_DISCONNECTED=0
 val MTU_SIZE=247;val GATT_OP_TIMEOUT_MS=60000L;val TAG="test"
 val _connectionState=MutableStateFlow(STATE_CONNECTING)
 val _dynamicData=MutableStateFlow<DynamicData?>(null);val _staticData=MutableStateFlow<StaticData?>(null)
 val _staticPackets=MutableSharedFlow<Unit>(extraBufferCapacity=1)
 val _connectedDevice=MutableStateFlow<String?>(null)
 private val jsonBufferData=JsonFrameBuffer();private val jsonBufferStatic=JsonFrameBuffer()
 var gatt:BluetoothGatt?=BluetoothGatt();var sessionId=1L
 var servicesRequested=false;var servicesReady=false;var controlReady=false;var receivedDynamic=false
 var currentMtu=23;var reconnectAttempts=0;var userInitiatedDisconnect=false
 val pendingSubscriptions=mutableSetOf<UUID>()
 val gattQueue=Collections.synchronizedList(ArrayList<Runnable>());var gattBusy=false
 var reconnectJob:Job?=null;var reconnectTimeoutJob:Job?=null;var gattOpTimeoutJob:Job?=null
 var powerWriteCallback:((Boolean)->Unit)?=null
 val errors=mutableListOf<String>()
 fun setConnectionState(s:Int){_connectionState.value=s}
 fun updateConnectedDevice(){_connectedDevice.value="peer"}
 fun postError(message:String){errors+=message}
 fun handleUnexpectedDisconnect(){failConnection("disconnected")}
 init{for(id in listOf(CHAR_DATA_UUID,CHAR_STATIC_UUID,CHAR_CTRL_UUID))gatt!!.service.chars[id]=BluetoothGattCharacteristic(id)
  gatt!!.service.chars[CHAR_CTRL_UUID]!!.properties=10}
 fun callback()=gattCallback
 fun start(){val g=gatt!!;gattCallback.onConnectionStateChange(g,0,2);gattCallback.onMtuChanged(g,23,0);gattCallback.onServicesDiscovered(g,0)}
 fun dynamic(){val g=gatt!!;val c=g.service.chars[CHAR_DATA_UUID]!!
  val j=JSONObject();for(k in listOf("vin","iin","vout","iout","pout","eff","fan","t1","t2","rt","pwr","si","sn","st","sf"))j.put(k,0)
  c.value=j.toString().toByteArray();gattCallback.onCharacteristicChanged(g,c)}
 fun subscribed(){val g=gatt!!;for(id in listOf(CHAR_DATA_UUID,CHAR_STATIC_UUID))gattCallback.onDescriptorWrite(g,g.service.chars[id]!!.descriptor,0)}
 fun enqueue(r:Runnable)=enqueueGattOperation(r)
 fun next()=processNextGattOperation()
 fun drop(){disconnectGatt();setConnectionState(STATE_DISCONNECTED)}
 private val gattCallback = '''+callbacks+'\n'+'\n'.join(function(n) for n in [
 'disconnectGatt','discoverServices','failConnection','markReady','enableNotification','tryParseAndDispatch',
 'parseAndDispatch','enqueueGattOperation','processNextGattOperation'])+r'''
}
fun main()=runTest{
 Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
 val all=mutableListOf<Harness>()
 fun harness()=Harness().also{all+=it}
 try{
  val h=harness();val g=h.gatt!!;h.start();h.dynamic()
  check(h._connectionState.value==2&&g.operations.count{it=="descriptor"}==1)
  h.subscribed();check(h._connectionState.value==2&&g.operations.last()=="read")
  advanceTimeBy(40000);check(h._connectionState.value==2)
  h.callback().onCharacteristicRead(g,g.service.chars[h.CHAR_CTRL_UUID]!!,0)
  check(h._connectionState.value==3)
  println("PASS Android real callback chain: MTU/services/CCCD/first packet/protected read gate; 40-second pairing remains connecting")
  for(failure in listOf("service","descriptor","authentication")){
   val x=harness();val peer=x.gatt!!
   if(failure=="service")peer.servicePresent=false
   if(failure=="descriptor")peer.descriptorResult=false
   x.start()
   if(failure=="authentication"){x.dynamic();x.subscribed();x.callback().onCharacteristicRead(peer,peer.service.chars[x.CHAR_CTRL_UUID]!!,5)}
   check(x.gatt==null&&x._connectionState.value==0&&x.errors.isNotEmpty())
  }
  val stale=harness();val old=stale.gatt!!;stale.start();stale.drop()
  stale.callback().onMtuChanged(old,247,0);stale.callback().onServicesDiscovered(old,0)
  stale.callback().onCharacteristicRead(old,old.service.chars[stale.CHAR_CTRL_UUID]!!,0)
  check(stale._connectionState.value==0&&stale.gatt==null)
  val legacy=harness();val peer=legacy.gatt!!;peer.mtuResult=false
  peer.service.chars[legacy.CHAR_CTRL_UUID]!!.properties=8
  peer.service.chars.values.forEach{it.hasDescriptor=false}
  legacy.start();check(legacy._connectionState.value==2);legacy.dynamic();check(legacy._connectionState.value==3)
  check(peer.operations.count{it=="discover"}==1&&"read" !in peer.operations)
  println("PASS Android failed setup/auth and stale callbacks cannot become ready; legacy first-packet gate and MTU fallback remain")
  val queue=harness();queue.gattBusy=true;queue.enqueue(Runnable{queue.next()});queue.enqueue(Runnable{})
  queue.next();advanceTimeBy(59000);queue.next();queue.enqueue(Runnable{})
  advanceTimeBy(1001);check(queue.gatt!=null)
  advanceTimeBy(59000);check(queue.gatt==null&&queue.errors.isNotEmpty())
  println("PASS synchronous operation failure does not orphan a deadline that closes a later operation; real timeout disconnects")
 }finally{all.forEach{it.scope.cancel()};Dispatchers.resetMain()}
}
'''
(build/'ConnectionChecks.kt').write_text(adapter)
manifest = json.loads((toolchain/'dependency-manifest.json').read_text())
java = manifest['java']; cp = os.pathsep.join(row['path'] for row in manifest['jars'])
jar = build/'checks.jar'
result = subprocess.run([java,'-cp',cp,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect',
    '-classpath',cp,'-jvm-target','11','-d',str(jar)]+[str(p) for p in build.glob('*.kt')],text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
(build/'compile.log').write_text(result.stdout)
if result.returncode: print(result.stdout)
assert result.returncode == 0
subprocess.run([java,'-ea','-cp',str(jar)+os.pathsep+cp,'cn.ntit.crps_compose.connectiontests.ConnectionChecksKt'],check=True,timeout=60)
