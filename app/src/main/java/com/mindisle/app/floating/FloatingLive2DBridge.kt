package com.mindisle.app.floating

object FloatingLive2DBridge {
    @Volatile
    private var controller: FloatingLive2DController? = null

    fun register(nextController: FloatingLive2DController) {
        controller = nextController
    }

    fun unregister(nextController: FloatingLive2DController?) {
        if (controller === nextController) {
            controller = null
        }
    }

    fun current(): FloatingLive2DController? = controller
}
