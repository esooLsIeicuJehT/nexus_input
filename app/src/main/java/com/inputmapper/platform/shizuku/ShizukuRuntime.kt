package com.inputmapper.platform.shizuku

import android.content.ServiceConnection
import rikka.shizuku.Shizuku

interface ShizukuRuntime {
    fun pingBinder(): Boolean
    fun checkSelfPermission(): Int
    fun bindUserService(args: Shizuku.UserServiceArgs, connection: ServiceConnection)
    fun unbindUserService(args: Shizuku.UserServiceArgs, connection: ServiceConnection, remove: Boolean)
}

object RealShizukuRuntime : ShizukuRuntime {
    override fun pingBinder(): Boolean = Shizuku.pingBinder()
    override fun checkSelfPermission(): Int = Shizuku.checkSelfPermission()
    override fun bindUserService(args: Shizuku.UserServiceArgs, connection: ServiceConnection) =
        Shizuku.bindUserService(args, connection)
    override fun unbindUserService(args: Shizuku.UserServiceArgs, connection: ServiceConnection, remove: Boolean) =
        Shizuku.unbindUserService(args, connection, remove)
}
