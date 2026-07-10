package com.smartisan.weather.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.EditText

/**
 * 搜索框输入控件，复刻自原版 Smartisan 系统 SearchBarEditText。
 * 仅是 EditText 的简单子类，保留与原版一致的构造器签名以便在
 * search_bar.xml 中以全限定名引用。
 */
class SearchBarEditText : EditText {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr)
}
