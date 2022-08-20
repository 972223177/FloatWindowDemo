@file:Suppress("unused")

package com.ly.floatwindowtest

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.*
import com.ly.floatwindowtest.databinding.LayoutFloatWindowBinding
import kotlin.math.abs

private typealias FloatVoidCallback = () -> Unit

@Suppress("DEPRECATION", "MemberVisibilityCanBePrivate")
object FloatWindowManager {
    // 支持通过WindowManager.LayoutParams.TYPE_TOAST的最高版本api
    private const val OP_SYSTEM_ALERT_WINDOW = 24

    private const val TAG = "FloatWindow"

    const val WINDOW_MODE_FULL = 1 //全屏

    const val WINDOW_MODE_FLOAT = 2 //悬浮窗


    private val mWindowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val mWindowParam = WindowManager.LayoutParams()

    var floatView: View? = null
        private set

    private var mStartTimestamp = 0L

    private var mWindowMode = WINDOW_MODE_FULL

    private val mBinding: LayoutFloatWindowBinding =
        LayoutFloatWindowBinding.inflate(LayoutInflater.from(appContext))

    private var mOnCloseListener: FloatVoidCallback? = null

    private var mOnWindowClickListener: FloatVoidCallback? = null

    private val mFloatingTouchListener = object : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var x = 0
        private var y = 0

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    x = event.rawX.toInt()
                    y = event.rawY.toInt()
                    startX = x
                    startY = y
                }
                MotionEvent.ACTION_MOVE -> {
                    val nowX = event.rawX.toInt()
                    val nowY = event.rawY.toInt()
                    val disX = nowX - x
                    val disY = nowY - y
                    x = nowX
                    y = nowY
                    mWindowParam.apply {
                        x = x.plus(disX)
                        y = y.plus(disY)
                    }
                    Log.d(TAG, "moveTo(${mWindowParam.x},${mWindowParam.y})")
                    mWindowManager.updateViewLayout(v, mWindowParam)
                }
                MotionEvent.ACTION_UP -> if (abs(x - startX) < 5 && abs(y - startY) < 5) {
                    mOnWindowClickListener?.invoke()
                }
                else -> {}
            }
            return true
        }
    }


    init {
        mBinding.root.setOnTouchListener(mFloatingTouchListener)
        mBinding.btnClose.setOnClickListener {
            closeFloatWindow(false)
        }
        mBinding.btnClose.visibility = View.VISIBLE
        val widthPx = appContext.resources.displayMetrics.widthPixels
        val rect = FloatWindowRect(widthPx - 700, 0, 600, 400)
        mWindowParam.apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
            //FLAG_NOT_FOCUSABLE 当前窗口接收触摸事件，窗口外的由原窗口处理，但不会与输入法交互，另外设置了这个flag，都会启用FLAG_NOT_TOUCH_MODAL
            //FLAG_NOT_TOUCH_MODAL 当前窗口外的view能够响应触摸事件,
            flags =
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            gravity = Gravity.CENTER_VERTICAL
            format = PixelFormat.TRANSLUCENT
            x = rect.x
            y = rect.y
            width = rect.width
            height = rect.height
        }
    }

    fun showFloatWindow(view: View, width: Int = 600, height: Int = 400): Boolean {
        if (!checkOverlayPermission()) {
            toast("请手动打开悬浮窗口权限")
            return false
        }
        return try {
            mWindowParam.apply {
                this.width = width
                this.height = height
            }
            (view.parent as? ViewGroup)?.removeView(view)
            mBinding.rlDisplayContainer.addView(view)
            mWindowManager.addView(mBinding.root, mWindowParam)
            floatView = view
            mWindowMode = WINDOW_MODE_FLOAT
            true
        } catch (e: Exception) {
            toast("打开悬浮窗失败")
            false
        }
    }

    val isFloatMode: Boolean
        get() = mWindowMode == WINDOW_MODE_FLOAT

    fun setStartTimestamp(timestamp: Long) {
        mStartTimestamp = timestamp
        floatView = null
        mWindowMode = WINDOW_MODE_FULL
    }

    val timeCount: Int
        get() = ((System.currentTimeMillis() - mStartTimestamp) / 1000L).toInt()

    fun updateFloatWindowSize(rect: FloatWindowRect) {
        mWindowParam.apply {
            x = rect.x
            y = rect.y
            width = rect.width
            height = rect.height
        }
        mWindowManager.updateViewLayout(mBinding.root, mWindowParam)
    }

    /**
     * 检查浮窗权限
     *
     */
    /**
     * 检查浮窗权限
     * api<18 默认就有浮窗的权限，单无法接收触摸和按键事件
     * api>=19 也是有权限，但可以接收触摸和按键事件
     * api>=23 需要在Manifest中申请[Manifest.permission.SYSTEM_ALERT_WINDOW],注意每次启动浮窗都要申请
     *         因为可以关
     * api>25 禁止用[WindowManager.LayoutParams.TYPE_TOAST]创建悬浮窗
     */
    private fun Context.checkFloatPermission(op: Int): Boolean {
        //23以上
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).also {
                    it.data = Uri.parse("package:$packageName")
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                return false
            }
            return true
        }
        //19-22之间
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val manager = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            return try {
                val method = AppOpsManager::class.java.getDeclaredMethod(
                    "checkOp",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    String::class.java
                )
                val code = method.invoke(manager, op, Binder.getCallingUid(), packageName) as? Int
                AppOpsManager.MODE_ALLOWED == code
            } catch (e: Exception) {
                false
            }
        }
        return true
    }

    fun checkOverlayPermission(): Boolean = appContext.checkFloatPermission(OP_SYSTEM_ALERT_WINDOW)

    fun closeFloatWindow(callEnd: Boolean = false): Boolean {
        if (callEnd) {
            floatView = null
        }
        if (mBinding.root.isAttachedToWindow) {
            mBinding.rlDisplayContainer.removeAllViews()
            mWindowManager.removeView(mBinding.root)
        }
        mOnCloseListener?.invoke()
        return true
    }

    fun setOnCloseListener(block: FloatVoidCallback) {
        mOnCloseListener = block
    }

    fun setOnWindowClickListener(block: FloatVoidCallback) {
        mOnWindowClickListener = block
    }


    data class FloatWindowRect(var x: Int, var y: Int, var width: Int, var height: Int)

}