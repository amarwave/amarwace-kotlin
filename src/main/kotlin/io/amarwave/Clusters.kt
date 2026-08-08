package io.amarwave

internal val CLUSTERS: Map<String, ClusterConfig> = mapOf(
    "default" to ClusterConfig(
        wss = "wss://amarwave.com",
        ws  = "ws://amarwave.com",
        api = "https://amarwave.com",
    ),
    "eu" to ClusterConfig(
        wss = "wss://amarwave.com",
        ws  = "ws://amarwave.com",
        api = "https://amarwave.com",
    ),
    "us" to ClusterConfig(
        wss = "wss://amarwave.com",
        ws  = "ws://amarwave.com",
        api = "https://amarwave.com",
    ),
    "ap1" to ClusterConfig(
        wss = "wss://amarwave.com",
        ws  = "ws://amarwave.com",
        api = "https://amarwave.com",
    ),
    "ap2" to ClusterConfig(
        wss = "wss://amarwave.com",
        ws  = "ws://amarwave.com",
        api = "https://amarwave.com",
    ),
    "local" to ClusterConfig(
        wss = "wss://localhost:3001",
        ws  = "ws://localhost:3001",
        api = "http://localhost:8000",
    ),
)
