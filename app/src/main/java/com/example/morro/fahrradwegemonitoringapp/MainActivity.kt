
package com.example.morro.fahrradwegemonitoringapp
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View


class MainActivity : AppCompatActivity() {

    var savedInstanceStateV: Bundle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        savedInstanceStateV = savedInstanceState;
    }

    fun startBildaufnahme(view: View){
        savedInstanceStateV ?: supportFragmentManager.beginTransaction()
                 .replace(R.id.container, Camera2BasicFragment.newInstance())
                 .commit()
    }

}
