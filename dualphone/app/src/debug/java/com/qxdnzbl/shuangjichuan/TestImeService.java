package com.qxdnzbl.shuangjichuan;

import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class TestImeService extends InputMethodService {
  @Override public View onCreateInputView(){
    TextView v=new TextView(this);
    v.setText("双机传测试键盘");
    v.setTextSize(18);
    v.setTextColor(Color.DKGRAY);
    v.setGravity(Gravity.CENTER);
    v.setBackgroundColor(Color.rgb(236,238,243));
    v.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(320)));
    return v;
  }
  private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
}