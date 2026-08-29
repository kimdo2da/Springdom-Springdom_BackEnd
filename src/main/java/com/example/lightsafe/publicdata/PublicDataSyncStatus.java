package com.example.lightsafe.publicdata;

public enum PublicDataSyncStatus {

    RUNNING,

    SUCCESS,

    /**
     * 일부만 받아 왔거나 지오코딩 대기열이 남은 상태.
     */
    PARTIAL,

    FAILED
}
