package home.bluetooth_scanner

import android.view.ViewPropertyAnimator
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView

class ShrinkRemoveItemAnimator : DefaultItemAnimator() {

    companion object {
        private const val REMOVE_ANIMATION_DURATION = 300L // ms
    }

    override fun animateRemove(holder: RecyclerView.ViewHolder): Boolean {
        val view = holder.itemView
        val animation: ViewPropertyAnimator = view.animate()

        // Reset any previous animations on this view to avoid conflicts
        animation.cancel()

        animation
            .setDuration(REMOVE_ANIMATION_DURATION)
            .scaleX(0f)
            .scaleY(0f)
            .alpha(0f)
            .setListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationStart(animator: android.animation.Animator) {
                    dispatchRemoveStarting(holder)
                }

                override fun onAnimationEnd(animator: android.animation.Animator) {
                    animation.setListener(null) // Avoid memory leaks
                    // It's important to reset the view's properties after animation
                    // if it's going to be reused by RecyclerView's view pool.
                    view.scaleX = 1f
                    view.scaleY = 1f
                    view.alpha = 1f
                    dispatchRemoveFinished(holder)
                    // mRemoveAnimations.remove(holder) // From DefaultItemAnimator, ensure this is handled
                    // dispatchFinishedWhenDone() // From DefaultItemAnimator, ensure this is handled
                }
            })
            .start()

        // mRemoveAnimations.add(holder) // From DefaultItemAnimator internal logic
        return true // Indicate that we are handling this animation
    }

    // To keep other animations (add, move, change) default,
    // we don't need to override their respective animateXYZ methods
    // unless DefaultItemAnimator's animateRemove implementation itself
    // relies on internal states that we might also need to manage.
    // For typical ViewPropertyAnimator usage like above, DefaultItemAnimator
    // is designed to be subclassed and have specific animateXYZ methods overridden.
    // We must ensure that `dispatchRemoveFinished(holder)` is called.
    // DefaultItemAnimator manages lists like mPendingRemovals, mRemoveAnimations.
    // Overriding animateRemove means we take responsibility for these.
    // The provided listener structure handles dispatchRemoveStarting and dispatchRemoveFinished.
}
