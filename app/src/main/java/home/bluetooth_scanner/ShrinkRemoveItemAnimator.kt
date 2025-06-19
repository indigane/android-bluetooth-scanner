package home.bluetooth_scanner

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.ViewPropertyAnimator
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class ShrinkRemoveItemAnimator : DefaultItemAnimator() {

    companion object {
        private const val REMOVE_ANIMATION_DURATION = 300L // ms
    }

    override fun animateRemove(holder: RecyclerView.ViewHolder): Boolean {
        val view = holder.itemView
        // It's good practice to store original values if they might change or are needed for restoration.
        val originalAlpha = view.alpha
        val originalScaleX = view.scaleX
        val originalScaleY = view.scaleY
        var originalElevation = 0f // Default if not a MaterialCardView or not set

        if (view is MaterialCardView) {
            originalElevation = view.cardElevation
        }

        val animation: ViewPropertyAnimator = view.animate()
        animation.cancel() // Reset any previous animations

        // Set elevation to 0 at the start of the animation
        if (view is MaterialCardView) {
            view.cardElevation = 0f
        }

        animation
            .setDuration(REMOVE_ANIMATION_DURATION)
            .scaleX(0f)
            .scaleY(0f)
            .alpha(0f)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animator: Animator) {
                    dispatchRemoveStarting(holder)
                }

                override fun onAnimationEnd(animator: Animator) {
                    animation.setListener(null) // Avoid memory leaks

                    // Restore properties for view recycling
                    view.alpha = originalAlpha // Restore alpha
                    view.scaleX = originalScaleX // Restore scaleX
                    view.scaleY = originalScaleY // Restore scaleY
                    if (view is MaterialCardView) {
                        view.cardElevation = originalElevation // Restore elevation
                    }

                    dispatchRemoveFinished(holder)
                }
            })
            .start()

        return true // Indicate that we are handling this animation
    }
}
