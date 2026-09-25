import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Animated,
  Easing,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  SafeAreaView,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  TextInput,
  useColorScheme,
  useWindowDimensions,
  View,
} from 'react-native';

const T = {
  pageX: 24, xs: 8, sm: 12, md: 20, lg: 32, xl: 48,
  radiusSm: 10, radiusMd: 16, radiusLg: 24, pill: 999,
  greeting: 21, body: 17, meta: 13, nav: 10.5,
  buttonH: 52, inputMin: 116, navH: 58, icon: 23,
  fast: 120, base: 220, slow: 360,
};
const LIGHT = {
  bg:'#F7F6F3', surface:'#FFFFFF', surfaceAlt:'#FCFBF8',
  ink:'#22252A', secondary:'#96988F', tertiary:'#C7C6BF',
  divider:'rgba(34,37,42,.08)', accent:'#4B6E63',
  accentContrast:'#FFFFFF', disabledBg:'#E7E5DE', disabledFg:'#B6B4AC'
};
const DARK = {
  bg:'#16171A', surface:'#1E2022', surfaceAlt:'#202325',
  ink:'#ECEDE8', secondary:'#85877F', tertiary:'#55564F',
  divider:'rgba(255,255,255,.07)', accent:'#86A99C',
  accentContrast:'#10201A', disabledBg:'#292B2B', disabledFg:'#55564F'
};
const WEEK = ['周日','周一','周二','周三','周四','周五','周六'];
const ease = Easing.bezier(.22,.1,.18,1);

const RESONANCES = [
  {
    id:'r1',
    person:'一个也喜欢安静陪伴的人',
    reason:'你们都更喜欢低压力、不需要持续回应的陪伴方式。',
    mine:'我喜欢光遇，但我不喜欢强社交，更喜欢安静地和别人待在一起。',
    theirs:'我喜欢和一个人各做各的，不需要一直聊天。',
    expressions:[
      '下雨天我喜欢把灯关掉一点，听窗外的声音。',
      '有时候两个人不说话也不会尴尬，反而很舒服。',
      '我很喜欢慢慢认识一个人，不想一开始就问很多问题。'
    ]
  },
  {
    id:'r2',
    person:'一个会追问“为什么”的人',
    reason:'你们都不太接受只有结论、没有依据的说法。',
    mine:'如果你说是真的，那请你拿出证据来。',
    theirs:'我最怕别人只丢给我一个结论，我会想知道它到底是怎么来的。',
    expressions:[
      '我喜欢看调查和推理，不是为了反转，是想知道事情究竟怎么发生。',
      '一个观点越笃定，我越想看它的证据在哪里。',
      '我更喜欢把事情说清楚，而不是互相说服。'
    ]
  },
  {
    id:'r3',
    person:'一个也不喜欢强目的性交友的人',
    reason:'你们都更希望关系从真实表达里自然长出来。',
    mine:'我不想为了交朋友而硬聊，感觉目的性太强会很尴尬。',
    theirs:'我更喜欢先看一个人平时在想什么，熟悉以后再决定要不要认识。',
    expressions:[
      '我不太会主动找话题，但看到真正想回的话会自然回复。',
      '兴趣一样不代表一定聊得来，我更在意为什么喜欢。',
      '认识人这件事如果变成任务，我反而会退开。'
    ]
  }
];

const MY_EXPRESSIONS = [
  '我喜欢光遇，但我不喜欢强社交，更喜欢安静地和别人待在一起。',
  '如果你说是真的，那请你拿出证据来。',
  '我觉得一个东西好不好，不是看多少人喜欢，而是它有没有真的解决问题。'
];

function dateText(){
  const d=new Date();
  return `${d.getMonth()+1}月${d.getDate()}日 · ${WEEK[d.getDay()]}`;
}
function ExpressIcon({color}) {
  return <View style={{width:T.icon,height:T.icon,justifyContent:'center'}}>
    <View style={[s.line,{top:5,width:12,backgroundColor:color}]}/>
    <View style={[s.line,{top:11,width:9,backgroundColor:color}]}/>
    <View style={[s.line,{top:17,width:5,backgroundColor:color}]}/>
  </View>;
}
function EchoIcon({color}) {
  return <View style={{width:T.icon,height:T.icon,alignItems:'center',justifyContent:'center'}}>
    <View style={[s.outerRing,{borderColor:color}]}/>
    <View style={[s.midRing,{borderColor:color}]}/>
    <View style={[s.dot,{backgroundColor:color}]}/>
  </View>;
}
function MeIcon({color}) {
  return <View style={{width:T.icon,height:T.icon,alignItems:'center'}}>
    <View style={[s.head,{borderColor:color}]}/>
    <View style={[s.shoulders,{borderColor:color}]}/>
  </View>;
}
function BackIcon({color}) {
  return <View style={{width:24,height:24,justifyContent:'center'}}>
    <View style={{width:10,height:10,borderLeftWidth:1.7,borderBottomWidth:1.7,borderColor:color,transform:[{rotate:'45deg'}],marginLeft:6}}/>
  </View>;
}
function Tab({active,label,colors,icon:Icon,onPress}) {
  const scale=useRef(new Animated.Value(1)).current;
  const animate=(to)=>Animated.timing(scale,{toValue:to,duration:T.fast,useNativeDriver:true}).start();
  const color=active?colors.accent:colors.tertiary;
  return <Pressable
    accessibilityRole="tab"
    accessibilityState={{selected:active}}
    onPress={onPress}
    onPressIn={()=>animate(.93)}
    onPressOut={()=>animate(1)}
    style={s.tabPress}
  >
    <Animated.View style={[s.tabInner,{transform:[{scale}]}]}>
      <Icon color={color}/>
      <Text style={[s.navLabel,{color}]}>{label}</Text>
    </Animated.View>
  </Pressable>;
}

function Top({colors,rightDot=true,title=null,back=null}) {
  return <View style={s.top}>
    {back ? (
      <Pressable onPress={back} accessibilityRole="button" accessibilityLabel="返回" hitSlop={12} style={s.backBtn}>
        <BackIcon color={colors.ink}/>
      </Pressable>
    ) : (
      <Text style={[s.date,{color:colors.secondary}]}>{title || dateText()}</Text>
    )}
    {back ? <Text style={[s.topTitle,{color:colors.ink}]} numberOfLines={1}>{title}</Text> : null}
    {rightDot ? <View style={[s.moodDot,{backgroundColor:colors.accent}]}/> : <View style={{width:24}}/>}
  </View>;
}

function TabBar({tab,setTab,colors}) {
  return <View style={[s.tabbar,{borderTopColor:colors.divider,backgroundColor:colors.bg}]}>
    <Tab active={tab==='express'} label="表达" colors={colors} icon={ExpressIcon} onPress={()=>setTab('express')}/>
    <Tab active={tab==='echo'} label="共鸣" colors={colors} icon={EchoIcon} onPress={()=>setTab('echo')}/>
    <Tab active={tab==='me'} label="我" colors={colors} icon={MeIcon} onPress={()=>setTab('me')}/>
  </View>;
}

function ExpressScreen({colors,focused,setFocused,onNavigate}) {
  const {height}=useWindowDimensions();
  const [text,setText]=useState('');
  const [inputH,setInputH]=useState(T.inputMin);
  const inputRef=useRef(null);
  const prompt=useRef(new Animated.Value(0)).current;
  const action=useRef(new Animated.Value(0)).current;
  const tabs=useRef(new Animated.Value(1)).current;
  const toast=useRef(new Animated.Value(0)).current;
  const publishScale=useRef(new Animated.Value(1)).current;
  const timer=useRef(null);
  const has=text.trim().length>0;
  const showAction=focused||has;
  const maxInput=Math.max(T.inputMin,Math.round(height*.46));

  useEffect(()=>{
    Animated.timing(action,{toValue:showAction?1:0,duration:T.base,easing:ease,useNativeDriver:true}).start();
  },[showAction,action]);
  useEffect(()=>()=>timer.current&&clearTimeout(timer.current),[]);

  function focus(next){
    setFocused(next);
    Animated.parallel([
      Animated.timing(prompt,{toValue:next?1:0,duration:next?T.slow:T.base,easing:ease,useNativeDriver:false}),
      Animated.timing(tabs,{toValue:next?0:1,duration:next?T.slow:T.base,easing:ease,useNativeDriver:true}),
    ]).start();
  }
  function publish(){
    if(!has)return;
    inputRef.current?.blur();
    focus(false);
    Animated.timing(toast,{toValue:1,duration:T.base,easing:ease,useNativeDriver:true}).start();
    if(timer.current)clearTimeout(timer.current);
    timer.current=setTimeout(()=>{
      setText('');
      setInputH(T.inputMin);
      Animated.timing(toast,{toValue:0,duration:T.base,easing:ease,useNativeDriver:true}).start();
    },900);
  }
  const promptSize=prompt.interpolate({inputRange:[0,1],outputRange:[T.greeting,T.meta]});
  const promptY=prompt.interpolate({inputRange:[0,1],outputRange:[0,-2]});
  const promptMb=prompt.interpolate({inputRange:[0,1],outputRange:[T.md,T.sm]});
  const actionY=action.interpolate({inputRange:[0,1],outputRange:[6,0]});
  const tabsY=tabs.interpolate({inputRange:[0,1],outputRange:[T.navH,0]});
  const toastY=toast.interpolate({inputRange:[0,1],outputRange:[-8,0]});
  const focusedShadow=useMemo(()=>Platform.select({
    ios:{shadowColor:'#000',shadowOffset:{width:0,height:8},shadowOpacity:.06,shadowRadius:12},
    android:{elevation:1},
    default:{}
  }),[]);

  return <>
    <Top colors={colors}/>
    <View style={s.compose}>
      <Animated.Text style={[
        s.prompt,
        {fontSize:promptSize,marginBottom:promptMb,transform:[{translateY:promptY}],color:focused?colors.secondary:colors.ink,fontWeight:focused?'500':'600'}
      ]}>今天，想说点什么</Animated.Text>

      <View style={[
        s.inputWrap,
        {backgroundColor:focused?colors.surface:colors.surfaceAlt,borderColor:focused?colors.divider:'transparent'},
        focused&&focusedShadow
      ]}>
        <TextInput
          ref={inputRef}
          accessibilityLabel="表达内容"
          style={[s.textarea,{color:colors.ink,height:Math.min(Math.max(inputH,T.inputMin),maxInput),maxHeight:maxInput}]}
          placeholder="写下此刻的想法……"
          placeholderTextColor={colors.tertiary}
          selectionColor={colors.accent}
          multiline
          textAlignVertical="top"
          value={text}
          onChangeText={setText}
          onFocus={()=>focus(true)}
          onBlur={()=>focus(false)}
          onContentSizeChange={e=>setInputH(Math.max(T.inputMin,Math.min(e.nativeEvent.contentSize.height,maxInput)))}
        />
      </View>
    </View>

    <Animated.View pointerEvents={showAction?'auto':'none'} style={[s.actionBar,{opacity:action,transform:[{translateY:actionY}]}]}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="发布"
        accessibilityState={{disabled:!has}}
        disabled={!has}
        onPress={publish}
        onPressIn={()=>Animated.timing(publishScale,{toValue:.96,duration:T.fast,useNativeDriver:true}).start()}
        onPressOut={()=>Animated.timing(publishScale,{toValue:1,duration:T.fast,useNativeDriver:true}).start()}
      >
        <Animated.View style={[s.publishButton,{backgroundColor:has?colors.accent:colors.disabledBg,transform:[{scale:publishScale}]}]}>
          <Text style={[s.publishLabel,{color:has?colors.accentContrast:colors.disabledFg}]}>发布</Text>
        </Animated.View>
      </Pressable>
    </Animated.View>

    <Animated.View pointerEvents={focused?'none':'auto'} style={[{opacity:tabs,transform:[{translateY:tabsY}]}]}>
      <TabBar tab="express" setTab={onNavigate} colors={colors}/>
    </Animated.View>

    <Animated.View pointerEvents="none" style={[s.toastLayer,{opacity:toast,transform:[{translateY:toastY}]}]}>
      <View style={[s.toast,{backgroundColor:colors.ink}]}>
        <Text style={[s.toastText,{color:colors.bg}]}>已表达</Text>
      </View>
    </Animated.View>
  </>;
}

function QuoteBlock({label,text,colors}) {
  return <View style={s.quoteBlock}>
    <Text style={[s.quoteLabel,{color:colors.secondary}]}>{label}</Text>
    <Text style={[s.quoteText,{color:colors.ink}]}>{text}</Text>
  </View>;
}

function EchoScreen({colors,onNavigate,onOpenPerson}) {
  return <>
    <Top colors={colors} title="共鸣"/>
    <ScrollView style={s.flex} contentContainerStyle={s.scrollContent} showsVerticalScrollIndicator={false}>
      <Text style={[s.sectionTitle,{color:colors.ink}]}>今天发现的重合</Text>
      <Text style={[s.sectionSub,{color:colors.secondary}]}>不是“谁喜欢了你”，而是你们具体哪里像。</Text>
      {RESONANCES.map(item=>(
        <Pressable
          key={item.id}
          onPress={()=>onOpenPerson(item)}
          style={({pressed})=>[
            s.resonanceCard,
            {backgroundColor:colors.surfaceAlt,borderColor:colors.divider,opacity:pressed?.96:1}
          ]}
        >
          <Text style={[s.cardPerson,{color:colors.secondary}]}>{item.person}</Text>
          <Text style={[s.cardReason,{color:colors.ink}]}>{item.reason}</Text>
          <View style={[s.cardDivider,{backgroundColor:colors.divider}]}/>
          <QuoteBlock label="你说" text={item.mine} colors={colors}/>
          <QuoteBlock label="对方说" text={item.theirs} colors={colors}/>
          <Text style={[s.viewMore,{color:colors.accent}]}>看看她还说过什么</Text>
        </Pressable>
      ))}
    </ScrollView>
    <TabBar tab="echo" setTab={onNavigate} colors={colors}/>
  </>;
}

function MeScreen({colors,onNavigate}) {
  return <>
    <Top colors={colors} title="我"/>
    <ScrollView style={s.flex} contentContainerStyle={s.scrollContent} showsVerticalScrollIndicator={false}>
      <View style={s.meIntro}>
        <Text style={[s.sectionTitle,{color:colors.ink}]}>慢慢长出来的我</Text>
        <Text style={[s.sectionSub,{color:colors.secondary}]}>这里不急着给你贴标签，只留下你真正说过的话。</Text>
      </View>
      <View style={[s.profileSummary,{backgroundColor:colors.surfaceAlt,borderColor:colors.divider}]}>
        <Text style={[s.profileKicker,{color:colors.secondary}]}>目前看见的几个方向</Text>
        <Text style={[s.profileLine,{color:colors.ink}]}>喜欢低压力、自然长出来的关系</Text>
        <Text style={[s.profileLine,{color:colors.ink}]}>在意真实依据，不喜欢只听结论</Text>
        <Text style={[s.profileLine,{color:colors.ink}]}>更关心东西有没有真的解决问题</Text>
      </View>
      <Text style={[s.listTitle,{color:colors.secondary}]}>我的表达</Text>
      {MY_EXPRESSIONS.map((item,i)=>(
        <View key={i} style={[s.expressionRow,{borderBottomColor:colors.divider}]}>
          <Text style={[s.expressionText,{color:colors.ink}]}>{item}</Text>
        </View>
      ))}
    </ScrollView>
    <TabBar tab="me" setTab={onNavigate} colors={colors}/>
  </>;
}

function PersonScreen({colors,item,onBack}) {
  return <>
    <Top colors={colors} title={item.person} rightDot={false} back={onBack}/>
    <ScrollView style={s.flex} contentContainerStyle={s.personContent} showsVerticalScrollIndicator={false}>
      <Text style={[s.personHint,{color:colors.secondary}]}>你是从这个重合点看到她的</Text>
      <View style={[s.reasonPanel,{backgroundColor:colors.surfaceAlt,borderColor:colors.divider}]}>
        <Text style={[s.cardReason,{color:colors.ink}]}>{item.reason}</Text>
      </View>
      <Text style={[s.listTitle,{color:colors.secondary}]}>她公开说过的话</Text>
      {item.expressions.map((text,i)=>(
        <View key={i} style={[s.personExpression,{borderBottomColor:colors.divider}]}>
          <Text style={[s.expressionText,{color:colors.ink}]}>{text}</Text>
        </View>
      ))}
      <Text style={[s.personFooter,{color:colors.secondary}]}>先看表达，再决定要不要靠近。</Text>
    </ScrollView>
  </>;
}

export default function App(){
  const scheme=useColorScheme();
  const colors=scheme==='dark'?DARK:LIGHT;
  const [tab,setTab]=useState('express');
  const [focused,setFocused]=useState(false);
  const [person,setPerson]=useState(null);

  function navigate(next){
    setFocused(false);
    setPerson(null);
    setTab(next);
  }

  return <SafeAreaView style={[s.safe,{backgroundColor:colors.bg}]}>
    <StatusBar barStyle={scheme==='dark'?'light-content':'dark-content'} backgroundColor={colors.bg}/>
    <KeyboardAvoidingView style={s.flex} behavior={Platform.OS==='ios'?'padding':'height'}>
      <View style={[s.page,{backgroundColor:colors.bg}]}>
        {person ? (
          <PersonScreen colors={colors} item={person} onBack={()=>setPerson(null)}/>
        ) : tab==='express' ? (
          <ExpressScreen colors={colors} focused={focused} setFocused={setFocused} onNavigate={navigate}/>
        ) : tab==='echo' ? (
          <EchoScreen colors={colors} onNavigate={navigate} onOpenPerson={setPerson}/>
        ) : (
          <MeScreen colors={colors} onNavigate={navigate}/>
        )}
      </View>
    </KeyboardAvoidingView>
  </SafeAreaView>;
}

const s=StyleSheet.create({
  flex:{flex:1}, safe:{flex:1},
  page:{flex:1,width:'100%',maxWidth:480,alignSelf:'center',position:'relative',overflow:'hidden'},
  top:{paddingHorizontal:T.pageX,paddingTop:T.md,paddingBottom:T.xs,flexDirection:'row',alignItems:'center',justifyContent:'space-between',minHeight:52},
  date:{fontSize:T.meta,fontWeight:'400',letterSpacing:.13},
  topTitle:{position:'absolute',left:60,right:60,textAlign:'center',fontSize:15,fontWeight:'600'},
  backBtn:{width:24,height:24,alignItems:'center',justifyContent:'center'},
  moodDot:{width:7,height:7,borderRadius:3.5,opacity:.55},
  compose:{flex:1,paddingHorizontal:T.pageX,paddingTop:T.lg,paddingBottom:T.md,minHeight:0},
  prompt:{lineHeight:30,letterSpacing:.04},
  inputWrap:{flex:1,borderRadius:T.radiusLg,borderWidth:1,padding:T.md,minHeight:T.inputMin},
  textarea:{width:'100%',flexGrow:1,padding:0,margin:0,fontSize:T.body,fontWeight:'400',lineHeight:27},
  actionBar:{paddingHorizontal:T.pageX,paddingBottom:T.md,alignItems:'flex-end'},
  publishButton:{height:T.buttonH,paddingHorizontal:T.lg,borderRadius:T.pill,justifyContent:'center',alignItems:'center'},
  publishLabel:{fontSize:T.body,fontWeight:'500'},
  tabbar:{height:T.navH,flexDirection:'row',alignItems:'stretch',borderTopWidth:StyleSheet.hairlineWidth},
  tabPress:{flex:1}, tabInner:{flex:1,alignItems:'center',justifyContent:'center',gap:4},
  navLabel:{fontSize:T.nav,fontWeight:'500',letterSpacing:.1},
  toastLayer:{position:'absolute',left:0,right:0,top:T.xl,alignItems:'center'},
  toast:{paddingHorizontal:T.md,paddingVertical:T.xs,borderRadius:T.pill},
  toastText:{fontSize:T.meta,fontWeight:'500'},
  line:{position:'absolute',height:1.6,left:5,borderRadius:1},
  outerRing:{position:'absolute',width:20.4,height:20.4,borderRadius:10.2,borderWidth:1.6,opacity:.28},
  midRing:{position:'absolute',width:12.4,height:12.4,borderRadius:6.2,borderWidth:1.6,opacity:.55},
  dot:{width:4.2,height:4.2,borderRadius:2.1},
  head:{position:'absolute',top:2.5,width:6.6,height:6.6,borderRadius:3.3,borderWidth:1.6},
  shoulders:{position:'absolute',top:14,width:13,height:7,borderTopWidth:1.6,borderLeftWidth:1.6,borderRightWidth:1.6,borderTopLeftRadius:8,borderTopRightRadius:8},
  scrollContent:{paddingHorizontal:T.pageX,paddingTop:T.lg,paddingBottom:T.lg},
  sectionTitle:{fontSize:T.greeting,fontWeight:'600',lineHeight:30,marginBottom:6},
  sectionSub:{fontSize:T.meta,lineHeight:20,marginBottom:T.lg},
  resonanceCard:{borderWidth:1,borderRadius:T.radiusLg,padding:T.md,marginBottom:T.md},
  cardPerson:{fontSize:T.meta,fontWeight:'500',marginBottom:T.sm},
  cardReason:{fontSize:T.body,fontWeight:'600',lineHeight:25},
  cardDivider:{height:StyleSheet.hairlineWidth,marginVertical:T.md},
  quoteBlock:{marginBottom:T.sm},
  quoteLabel:{fontSize:11,fontWeight:'600',marginBottom:5},
  quoteText:{fontSize:15,lineHeight:23},
  viewMore:{fontSize:T.meta,fontWeight:'600',marginTop:4},
  meIntro:{marginBottom:4},
  profileSummary:{borderWidth:1,borderRadius:T.radiusLg,padding:T.md,marginBottom:T.lg},
  profileKicker:{fontSize:11,fontWeight:'600',marginBottom:T.sm},
  profileLine:{fontSize:15,lineHeight:24,marginBottom:4},
  listTitle:{fontSize:T.meta,fontWeight:'500',marginBottom:T.xs},
  expressionRow:{paddingVertical:T.md,borderBottomWidth:StyleSheet.hairlineWidth},
  expressionText:{fontSize:T.body,lineHeight:27},
  personContent:{paddingHorizontal:T.pageX,paddingTop:T.lg,paddingBottom:T.xl},
  personHint:{fontSize:T.meta,lineHeight:20,marginBottom:T.sm},
  reasonPanel:{borderWidth:1,borderRadius:T.radiusLg,padding:T.md,marginBottom:T.lg},
  personExpression:{paddingVertical:T.md,borderBottomWidth:StyleSheet.hairlineWidth},
  personFooter:{fontSize:T.meta,lineHeight:20,textAlign:'center',marginTop:T.lg},
});
