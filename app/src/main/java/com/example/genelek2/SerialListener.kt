package com.example.genelek2

import java.util.ArrayDeque

interface SerialListener {
    fun onSerialConnected()
    fun onSerialConnectionError(e: Exception?)
    fun onSerialDataRead(data: ByteArray?) // socket -> service
    fun onSerialDatasRead(datas: ArrayDeque<ByteArray?>?) // service -> UI thread
    fun onSerialIOError(e: Exception?)
}