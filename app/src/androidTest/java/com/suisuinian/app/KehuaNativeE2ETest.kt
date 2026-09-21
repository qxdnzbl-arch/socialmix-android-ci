package com.suisuinian.app

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KehuaNativeE2ETest {
 @Test fun realBackendCoreFlow()=runBlocking{
  val base=InstrumentationRegistry.getInstrumentation().targetContext
  val suffix=System.currentTimeMillis().toString().takeLast(8)
  val a=KehuaProdApi(ContextWrapper(base),"_qa_a_$suffix")
  val b=KehuaProdApi(ContextWrapper(base),"_qa_b_$suffix")
  val loginA="qa${suffix}a";val loginB="qa${suffix}b";val pw="Qa_${suffix}_pass"
  try{
   assertTrue(a.register(loginA,pw,"验收A").isSuccess);assertTrue(b.register(loginB,pw,"验收B").isSuccess)
   assertTrue(a.createPost("A-$suffix").isSuccess);assertTrue(b.createPost("B-$suffix").isSuccess)
   val home=a.home().getOrThrow();assertTrue(home.posts.any{it.content=="A-$suffix"})
   val r=home.resonances.firstOrNull{it.content=="B-$suffix"};assertNotNull("没有生成真实共鸣",r);assertNull("点亮前泄露身份",r!!.authorId)
   val peer=a.light(r.id).getOrThrow();assertEquals("验收B",peer.nickname)
   assertTrue(a.sendMessage(peer.id,"MSG-$suffix").isSuccess)
   val aId=a.me().getOrThrow().id;assertTrue(b.chat(aId).getOrThrow().second.any{it.body=="MSG-$suffix"})
   assertTrue(a.friendRequest(peer.id).isSuccess)
   val req=b.incomingRequests().getOrThrow().firstOrNull{it.fromUser==aId};assertNotNull("B未收到好友申请",req)
   assertEquals("accepted",b.respondRequest(req!!.id,true).getOrThrow())
   assertTrue(a.friends().getOrThrow().any{it.id==peer.id})
  } finally { runCatching{a.deleteAccount()};runCatching{b.deleteAccount()} }
 }
}
