package com.suisuinian.app

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KehuaNativeE2ETest {
 @Test fun realBackendCoreFlow()=runBlocking{
  val base=InstrumentationRegistry.getInstrumentation().targetContext
  val suffix=System.currentTimeMillis().toString().takeLast(8)
  val a=KehuaProdApi(ContextWrapper(base),"_qa_a_"+suffix)
  val b=KehuaProdApi(ContextWrapper(base),"_qa_b_"+suffix)
  val loginA="qa"+suffix+"a"; val loginB="qa"+suffix+"b"; val pw="Qa_"+suffix+"_pass"
  try{
   val ra=a.register(loginA,pw,"验收A")
   assertTrue("STEP_A_REGISTER: "+ra.exceptionOrNull()?.message,ra.isSuccess)
   val rb=b.register(loginB,pw,"验收B")
   assertTrue("STEP_B_REGISTER: "+rb.exceptionOrNull()?.message,rb.isSuccess)

   val pa=a.createPost("PAIR-"+suffix+"-A")
   assertTrue("STEP_A_POST: "+pa.exceptionOrNull()?.message,pa.isSuccess)
   val pb=b.createPost("PAIR-"+suffix+"-B")
   assertTrue("STEP_B_POST: "+pb.exceptionOrNull()?.message,pb.isSuccess)

   var home: NativeHome?=null
   var resonance: NativeResonance?=null
   repeat(5){
    val hr=a.home()
    assertTrue("STEP_A_HOME: "+hr.exceptionOrNull()?.message,hr.isSuccess)
    home=hr.getOrThrow()
    resonance=home!!.resonances.firstOrNull{it.content=="PAIR-"+suffix+"-B"}
    if(resonance==null) delay(500)
   }
   assertTrue("STEP_A_OWN_POST_MISSING",home!!.posts.any{it.content=="PAIR-"+suffix+"-A"})
   assertNotNull("STEP_RESONANCE_MISSING: "+home!!.resonances.map{it.content},resonance)
   assertNull("STEP_IDENTITY_LEAKED",resonance!!.authorId)

   val lit=a.light(resonance!!.id)
   assertTrue("STEP_LIGHT: "+lit.exceptionOrNull()?.message,lit.isSuccess)
   val peer=lit.getOrThrow()
   assertEquals("STEP_WRONG_PEER","验收B",peer.nickname)

   val sm=a.sendMessage(peer.id,"MSG-"+suffix)
   assertTrue("STEP_SEND: "+sm.exceptionOrNull()?.message,sm.isSuccess)
   val me=a.me()
   assertTrue("STEP_A_ME: "+me.exceptionOrNull()?.message,me.isSuccess)
   val aId=me.getOrThrow().id
   val bChat=b.chat(aId)
   assertTrue("STEP_B_CHAT: "+bChat.exceptionOrNull()?.message,bChat.isSuccess)
   assertTrue("STEP_B_MESSAGE_MISSING",bChat.getOrThrow().second.any{it.body=="MSG-"+suffix})

   val fr=a.friendRequest(peer.id)
   assertTrue("STEP_FRIEND_REQUEST: "+fr.exceptionOrNull()?.message,fr.isSuccess)
   val requests=b.incomingRequests()
   assertTrue("STEP_INCOMING: "+requests.exceptionOrNull()?.message,requests.isSuccess)
   val req=requests.getOrThrow().firstOrNull{it.fromUser==aId}
   assertNotNull("STEP_REQUEST_MISSING",req)

   val accept=b.respondRequest(req!!.id,true)
   assertTrue("STEP_ACCEPT: "+accept.exceptionOrNull()?.message,accept.isSuccess)
   assertEquals("STEP_NOT_ACCEPTED","accepted",accept.getOrThrow())
   val friends=a.friends()
   assertTrue("STEP_A_FRIENDS: "+friends.exceptionOrNull()?.message,friends.isSuccess)
   assertTrue("STEP_FRIEND_NOT_VISIBLE",friends.getOrThrow().any{it.id==peer.id})
  } finally {
   runCatching{a.deleteAccount()}
   runCatching{b.deleteAccount()}
  }
 }
}
