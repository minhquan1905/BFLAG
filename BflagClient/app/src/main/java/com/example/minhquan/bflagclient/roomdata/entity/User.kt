package com.example.minhquan.bflagclient.roomdata.entity

import android.arch.persistence.room.*
import org.jetbrains.annotations.NotNull

@Entity(tableName = "user")
class User {
    @PrimaryKey()
    @NotNull
    var email: String = ""

    @ColumnInfo(name = "password")
    var password: String? = null

    @ColumnInfo(name = "username")
    var username: String? = null

    @ColumnInfo(name = "first_name")
    var firstname: String? = null

    @ColumnInfo(name = "last_name")
    var lastname: String? = null

    @ColumnInfo(name = "profile_image")
    var profileimage: String? = null
}