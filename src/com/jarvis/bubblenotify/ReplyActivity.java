package com.jarvis.bubblenotify;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/* JADX INFO: loaded from: classes.dex */
public class ReplyActivity extends Activity {
    private EditText editText;
    private PendingIntent replyIntent;
    private String replyKey;

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        PendingIntent pendingIntent;
        super.onCreate(bundle);
        Intent intent = getIntent();
        if (intent == null) {
            pendingIntent = null;
        } else {
            pendingIntent = (PendingIntent) intent.getParcelableExtra(Settings.EXTRA_INTENT);
        }
        this.replyIntent = pendingIntent;
        this.replyKey = intent != null ? intent.getStringExtra(Settings.EXTRA_KEY) : null;
        String stringExtra = intent != null ? intent.getStringExtra(Settings.EXTRA_TITLE) : null;
        if (this.replyIntent == null || this.replyKey == null) {
            finish();
            return;
        }
        FrameLayout frameLayout = new FrameLayout(this);
        frameLayout.setBackgroundColor(Integer.MIN_VALUE);
        frameLayout.setClickable(true);
        frameLayout.setOnClickListener(new View.OnClickListener() { // from class: com.jarvis.bubblenotify.ReplyActivity.1
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                ReplyActivity.this.finish();
            }
        });
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(-419430401);
        gradientDrawable.setCornerRadius(dp(18));
        gradientDrawable.setStroke(dp(1), 1493172224);
        linearLayout.setBackground(gradientDrawable);
        int iDp = dp(16);
        linearLayout.setPadding(iDp, iDp, iDp, iDp);
        linearLayout.setClickable(true);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(-1, -2);
        layoutParams.leftMargin = dp(24);
        layoutParams.rightMargin = dp(24);
        layoutParams.topMargin = dp(120);
        layoutParams.gravity = 49;
        frameLayout.addView(linearLayout, layoutParams);
        TextView textView = new TextView(this);
        textView.setText(!TextUtils.isEmpty(stringExtra) ? "回复: " + stringExtra : "回复");
        textView.setTextColor(-15065826);
        textView.setTextSize(15.0f);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        linearLayout.addView(textView);
        this.editText = new EditText(this);
        this.editText.setHint("回复…");
        this.editText.setHintTextColor(-8748153);
        this.editText.setTextColor(-15065826);
        this.editText.setTextSize(15.0f);
        this.editText.setSingleLine(true);
        this.editText.setInputType(1);
        this.editText.setImeOptions(4);
        GradientDrawable gradientDrawable2 = new GradientDrawable();
        gradientDrawable2.setColor(335544320);
        gradientDrawable2.setCornerRadius(dp(999));
        this.editText.setBackground(gradientDrawable2);
        this.editText.setPadding(dp(12), dp(9), dp(12), dp(9));
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, -2);
        layoutParams2.topMargin = dp(12);
        this.editText.setLayoutParams(layoutParams2);
        this.editText.setOnEditorActionListener(new TextView.OnEditorActionListener() { // from class: com.jarvis.bubblenotify.ReplyActivity.2
            @Override // android.widget.TextView.OnEditorActionListener
            public boolean onEditorAction(TextView textView2, int i, KeyEvent keyEvent) {
                if (i == 4) {
                    ReplyActivity.this.sendReply();
                    return true;
                }
                return false;
            }
        });
        linearLayout.addView(this.editText);
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(0);
        linearLayout2.setGravity(8388613);
        LinearLayout.LayoutParams layoutParams3 = new LinearLayout.LayoutParams(-1, -2);
        layoutParams3.topMargin = dp(12);
        linearLayout2.setLayoutParams(layoutParams3);
        TextView textView2 = new TextView(this);
        textView2.setText("取消");
        textView2.setTextSize(15.0f);
        textView2.setTextColor(-11905960);
        textView2.setPadding(dp(14), dp(8), dp(14), dp(8));
        textView2.setClickable(true);
        textView2.setOnClickListener(new View.OnClickListener() { // from class: com.jarvis.bubblenotify.ReplyActivity.3
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                ReplyActivity.this.finish();
            }
        });
        linearLayout2.addView(textView2);
        TextView textView3 = new TextView(this);
        textView3.setText("发送");
        textView3.setTextSize(15.0f);
        textView3.setTextColor(-1);
        textView3.setTypeface(Typeface.DEFAULT_BOLD);
        textView3.setPadding(dp(16), dp(8), dp(16), dp(8));
        GradientDrawable gradientDrawable3 = new GradientDrawable();
        gradientDrawable3.setColor(-15961500);
        gradientDrawable3.setCornerRadius(dp(999));
        textView3.setBackground(gradientDrawable3);
        textView3.setClickable(true);
        textView3.setOnClickListener(new View.OnClickListener() { // from class: com.jarvis.bubblenotify.ReplyActivity.4
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                ReplyActivity.this.sendReply();
            }
        });
        linearLayout2.addView(textView3);
        linearLayout.addView(linearLayout2);
        setContentView(frameLayout);
        this.editText.requestFocus();
    }

    public void sendReply() {
        String string = (this.editText == null || this.editText.getText() == null) ? "" : this.editText.getText().toString();
        if (string.trim().isEmpty()) {
            return;
        }
        try {
            Bundle bundle = new Bundle();
            bundle.putCharSequence(this.replyKey, string);
            Intent intent = new Intent();
            RemoteInput.addResultsToIntent(new RemoteInput[]{new RemoteInput.Builder(this.replyKey).build()}, intent, bundle);
            this.replyIntent.send(this, 0, intent);
            Toast.makeText(this, "已发送", 0).show();
        } catch (Throwable th) {
            Toast.makeText(this, "发送失败", 0).show();
        }
        finish();
    }

    private int dp(int i) {
        return Math.round(i * getResources().getDisplayMetrics().density);
    }
}
