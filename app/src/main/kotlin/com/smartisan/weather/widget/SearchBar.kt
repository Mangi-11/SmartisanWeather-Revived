package com.smartisan.weather.widget

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.text.Editable
import android.text.TextWatcher
import android.text.method.TextKeyListener
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.core.content.ContextCompat
import com.smartisan.weather.R
import com.smartisan.weather.util.ThemeUtils

/**
 * 搜索栏，复刻自原版 Smartisan 系统 SearchBar。
 *
 * 一个 RelativeLayout，内部 inflate search_bar.xml，管理「普通 → 搜索」
 * 模式切换并播放平移/淡入淡出动画。
 *
 * 原版私有 XML 属性已改成本项目的标准 declare-styleable，不依赖系统私有命名空间。
 */
class SearchBar : RelativeLayout, View.OnClickListener, TextWatcher {

    private var mAnimDistance: Int = 0
    private var mCancelButton: ImageView? = null
    private var mCancelButtonWidth: Int = 0
    private var mClearTextView: View? = null
    private var mHasSearchRightView: Boolean = false
    private var mIsPlayingAnimation: Boolean = false
    private var mIsSearchMode: Boolean = false
    private var mMarginToParent: Int = 0
    private var mMarginToSearchView: Int = 0
    private var mOnCancelClickListener: OnCancelClickListener? = null
    private var mRightView: ImageView? = null
    private var mRightViewContainer: LinearLayout? = null
    private var mSearchEditLayout: View? = null
    private var mSearchEditor: SearchBarEditText? = null

    /** 动画结束后的收尾 Runnable，复刻原版 AnimationRunnable。 */
    private inner class AnimationRunnable(private val mToSearchMode: Boolean) : Runnable {
        override fun run() {
            finishSearchAnimation(mToSearchMode)
        }
    }

    interface OnCancelClickListener {
        fun onClick(view: View?)
    }

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr) {
        init(context, attrs)
    }

    private fun getOffsetX(z: Boolean): Int {
        return if (mHasSearchRightView) {
            val measuredWidth = mRightViewContainer!!.measuredWidth - mCancelButtonWidth
            if (z) measuredWidth else -measuredWidth
        } else {
            val i = mCancelButtonWidth + mMarginToSearchView
            if (z) -i else i
        }
    }

    private fun init(context: Context, attributeSet: AttributeSet?) {
        LayoutInflater.from(context).inflate(R.layout.search_bar, this, true)
        mMarginToParent = resources.getDimensionPixelOffset(R.dimen.bar_margin_edge)
        mMarginToSearchView = resources.getDimensionPixelOffset(R.dimen.search_bar_margin_search_view)
        mAnimDistance = resources.getDimensionPixelOffset(R.dimen.search_bar_anim_distance)
        val paddingLeft = paddingLeft
        val paddingRight = paddingRight
        val vPad = resources.getDimensionPixelOffset(R.dimen.search_bar_outer_vertical_padding)
        setPadding(paddingLeft, vPad, paddingRight, vPad)
        mSearchEditLayout = findViewById(R.id.search_bar_edit_layout)
        mSearchEditor = findViewById(R.id.search_bar_edit_text)
        mCancelButton = findViewById(R.id.search_bar_cancel_button)
        mClearTextView = findViewById(R.id.search_bar_clear_text)
        mRightViewContainer = findViewById(R.id.search_bar_right_view_container)
        mRightView = findViewById(R.id.search_bar_right_view)
        if (ThemeUtils.isNightMode(context)) {
            /*
             * Keep the original search_field NinePatch so its pill silhouette,
             * edge shading, intrinsic height and padding stay identical to the
             * light theme. MULTIPLY only changes its palette.
             */
            mSearchEditLayout?.background?.mutate()?.colorFilter =
                PorterDuffColorFilter(
                    ContextCompat.getColor(context, R.color.app_surface_raised_color),
                    PorterDuff.Mode.MULTIPLY,
                )
            /*
             * The original magnifier is an alpha-mask PNG (30% black), so a
             * regular tint would remain too dim on a dark surface. Preserve its
             * exact pixels and normalize the original maximum alpha to 100%.
             */
            val searchIconColor =
                ContextCompat.getColor(context, R.color.search_icon_normal)
            findViewById<View>(R.id.search_bar_left_icon)
                .background
                ?.mutate()
                ?.colorFilter =
                ColorMatrixColorFilter(
                    floatArrayOf(
                        0f, 0f, 0f, 0f, Color.red(searchIconColor).toFloat(),
                        0f, 0f, 0f, 0f, Color.green(searchIconColor).toFloat(),
                        0f, 0f, 0f, 0f, Color.blue(searchIconColor).toFloat(),
                        0f, 0f, 0f, SEARCH_ICON_ALPHA_SCALE, 0f,
                    ),
                )
        }
        mSearchEditLayout?.setOnClickListener(this)
        mSearchEditor?.setOnClickListener(this)
        mCancelButton?.setOnClickListener(this)
        mSearchEditor?.addTextChangedListener(this)
        val attributes = context.obtainStyledAttributes(attributeSet, R.styleable.SearchBar)
        try {
            val hasRightIcon = attributes.getBoolean(R.styleable.SearchBar_hasRightIcon, true)
            mHasSearchRightView = hasRightIcon
            if (!hasRightIcon) {
                mRightViewContainer?.visibility = View.GONE
                mRightView?.visibility = View.GONE
            }
            val hintTextRes = attributes.getResourceId(R.styleable.SearchBar_hintText, 0)
            if (hintTextRes != 0) {
                mSearchEditor?.setHint(hintTextRes)
            } else {
                val hintText = attributes.getString(R.styleable.SearchBar_hintText)
                if (hintText != null) {
                    mSearchEditor?.hint = hintText
                }
            }
        } finally {
            attributes.recycle()
        }
        mCancelButtonWidth = resources.getDimensionPixelOffset(R.dimen.standard_icon_size)
        mCancelButton?.visibility = View.GONE
        mClearTextView?.visibility = View.GONE
        resetSearchViewParam(false)
        updateEditorStatus()
    }

    private fun resetSearchViewParam(z: Boolean) {
        val layoutParams = mSearchEditLayout!!.layoutParams as RelativeLayout.LayoutParams
        if (z) {
            // LEFT_OF == 0，与原版 addRule(0, ...) 一致
            layoutParams.addRule(RelativeLayout.LEFT_OF, mCancelButton!!.id)
            layoutParams.rightMargin = 0
        } else if (mHasSearchRightView) {
            layoutParams.addRule(RelativeLayout.LEFT_OF, mRightViewContainer!!.id)
            layoutParams.rightMargin = 0
        } else {
            layoutParams.removeRule(RelativeLayout.LEFT_OF)
            layoutParams.rightMargin = 0
            layoutParams.rightMargin = mMarginToParent
        }
        mSearchEditLayout!!.layoutParams = layoutParams
    }

    private fun updateEditorStatus() {
        mSearchEditor!!.isFocusable = mIsSearchMode
        mSearchEditor!!.isFocusableInTouchMode = mIsSearchMode
        mSearchEditor!!.isCursorVisible = mIsSearchMode
        if (!mIsSearchMode) {
            mSearchEditor!!.clearFocus()
            mClearTextView?.visibility = View.GONE
        } else {
            mSearchEditor!!.requestFocus()
            mClearTextView?.visibility =
                if ((mSearchEditor!!.text?.length ?: 0) > 0) View.VISIBLE else View.GONE
        }
    }

    override fun afterTextChanged(editable: Editable?) {
        if ((editable?.length ?: 0) <= 0 || !mIsSearchMode) {
            mClearTextView?.visibility = View.GONE
        } else {
            mClearTextView?.visibility = View.VISIBLE
        }
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

    fun finishSearchAnimation(z: Boolean) {
        resetSearchViewParam(z)
        updateEditorStatus()
        if (z) {
            mCancelButton!!.visibility = View.VISIBLE
            mCancelButton!!.alpha = 1.0f
            mCancelButton!!.translationX = 0.0f
            showKeyboard()
        } else {
            mCancelButton!!.visibility = View.GONE
            mCancelButton!!.alpha = 0.0f
            mCancelButton!!.translationX = mAnimDistance.toFloat()
            hideKeyboard()
        }
        mIsPlayingAnimation = false
    }

    fun getClearView(): View? = mClearTextView

    fun getSearchEditor(): View? = mSearchEditor

    fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(mSearchEditor?.windowToken, 0)
    }

    fun isSearchMode(): Boolean = mIsSearchMode

    override fun onClick(view: View?) {
        if (view !== mCancelButton) {
            onClickSearchEditor(true)
            return
        }
        mOnCancelClickListener?.onClick(mCancelButton)
        onClickCancelView(true)
    }

    fun onClickCancelView(z: Boolean) {
        if (mIsSearchMode) {
            if (mIsPlayingAnimation && z) return
            mIsSearchMode = false
            TextKeyListener.clear(mSearchEditor!!.text as Editable)
            if (z) startAnimation(false) else finishSearchAnimation(false)
        }
    }

    fun onClickSearchEditor(z: Boolean) {
        if (mIsSearchMode) return
        if (mIsPlayingAnimation && z) return
        mIsSearchMode = true
        if (z) startAnimation(true) else finishSearchAnimation(true)
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    fun setOnCancelClickListener(listener: OnCancelClickListener?) {
        mOnCancelClickListener = listener
    }

    fun showKeyboard() {
        mSearchEditor!!.isFocusable = true
        mSearchEditor!!.isFocusableInTouchMode = true
        mSearchEditor!!.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(mSearchEditor, 0)
    }

    fun startAnimation(z: Boolean) {
        mIsSearchMode = z
        mIsPlayingAnimation = true
        if (mCancelButtonWidth == 0) {
            mCancelButtonWidth = resources.getDimensionPixelOffset(R.dimen.standard_icon_size)
        }
        val offsetX = getOffsetX(z)
        val right = mSearchEditLayout!!.right
        val rightAnim = ObjectAnimator.ofInt(mSearchEditLayout, "right", right, right + offsetX)
        if (z) {
            rightAnim.startDelay = 100L
            rightAnim.duration = 200L
        } else {
            rightAnim.duration = 200L
        }
        mCancelButton!!.visibility = View.VISIBLE
        mCancelButton!!.bringToFront()
        val translationX: FloatArray
        val alphaArr: FloatArray
        if (z) {
            mCancelButton!!.translationX = mAnimDistance.toFloat()
            mCancelButton!!.alpha = 0.0f
            translationX = floatArrayOf(mAnimDistance.toFloat(), 0.0f)
            alphaArr = floatArrayOf(0.0f, 1.0f)
        } else {
            translationX = floatArrayOf(0.0f, (mCancelButtonWidth + mMarginToParent).toFloat())
            alphaArr = floatArrayOf(1.0f, 0.0f)
        }
        val translationAnim = ObjectAnimator.ofFloat(mCancelButton, View.TRANSLATION_X, *translationX)
        val alphaAnim = ObjectAnimator.ofFloat(mCancelButton, View.ALPHA, *alphaArr)
        if (z) {
            translationAnim.startDelay = 100L
            alphaAnim.startDelay = 100L
        }
        translationAnim.duration = 200L
        alphaAnim.duration = 200L
        val interpolator = DecelerateInterpolator(1.5f)
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(rightAnim, translationAnim, alphaAnim)
        animatorSet.interpolator = interpolator
        animatorSet.start()
        postDelayed(AnimationRunnable(z), if (z) 300L else 200L)
    }

    private companion object {
        /** Original normal search icon peaks at alpha 77 out of 255. */
        private const val SEARCH_ICON_ALPHA_SCALE = 255f / 77f
    }
}
