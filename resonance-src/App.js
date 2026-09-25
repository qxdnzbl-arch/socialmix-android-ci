import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Animated,
  Easing,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  SafeAreaView,
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
  radiusLg: 24, pill: 999,
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
export default function App(){
  const scheme=useColorScheme();
  const colors=scheme==='dark'?DARK:LIGHT;
  const {height}=useWindowDimensions();
  const [text,setText]=useState('');
  const [focused,setFocused]=useState(false);
  const [tab,setTab]=useState('express');
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
  return <SafeAreaView style={[s.safe,{backgroundColor:colors.bg}]}>
    <StatusBar barStyle={scheme==='dark'?'light-content':'dark-content'} backgroundColor={colors.bg}/>
    <KeyboardAvoidingView style={s.flex} behavior={Platform.OS==='ios'?'padding':'height'}>
      <View style={[s.page,{backgroundColor:colors.bg}]}>
        <View style={s.top}>
          <Text style={[s.date,{color:colors.secondary}]}>{dateText()}</Text>
          <View style={[s.moodDot,{backgroundColor:colors.accent}]}/>
        </View>

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

        <Animated.View pointerEvents={focused?'none':'auto'} style={[s.tabbar,{borderTopColor:colors.divider,backgroundColor:colors.bg,opacity:tabs,transform:[{translateY:tabsY}]}]}>
          <Tab active={tab==='express'} label="表达" colors={colors} icon={ExpressIcon} onPress={()=>setTab('express')}/>
          <Tab active={tab==='echo'} label="共鸣" colors={colors} icon={EchoIcon} onPress={()=>setTab('echo')}/>
          <Tab active={tab==='me'} label="我" colors={colors} icon={MeIcon} onPress={()=>setTab('me')}/>
        </Animated.View>

        <Animated.View pointerEvents="none" style={[s.toastLayer,{opacity:toast,transform:[{translateY:toastY}]}]}>
          <View style={[s.toast,{backgroundColor:colors.ink}]}>
            <Text style={[s.toastText,{color:colors.bg}]}>已表达</Text>
          </View>
        </Animated.View>
      </View>
    </KeyboardAvoidingView>
  </SafeAreaView>;
}
const s=StyleSheet.create({
  flex:{flex:1}, safe:{flex:1},
  page:{flex:1,width:'100%',maxWidth:480,alignSelf:'center',position:'relative',overflow:'hidden'},
  top:{paddingHorizontal:T.pageX,paddingTop:T.md,paddingBottom:T.xs,flexDirection:'row',alignItems:'center',justifyContent:'space-between'},
  date:{fontSize:T.meta,fontWeight:'400',letterSpacing:.13},
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
});
