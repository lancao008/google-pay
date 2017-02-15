package com.googlepay;

import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import com.googlewalletlib.util.PayTools;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
    PayTools payTools;
    /**
     * 购买按钮
     * @param view
     *
     * fiscard123 在google创建的商品
     * id123 服务器生成的id
     *
     */
    public void buy(View view){
         payTools = new PayTools(this,
                handlerResult, "fiscard123", "id123");
        payTools.buy();
    }

    Handler handlerResult=new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (payTools != null) {
            payTools.getHandleActivityResultData(requestCode, resultCode, data);
            return;
        }
    }
}
