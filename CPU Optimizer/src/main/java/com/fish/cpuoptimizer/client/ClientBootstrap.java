package com.fish.cpuoptimizer.client;

public final class ClientBootstrap {
    private ClientBootstrap() {
    }

    public static void register() {
        ClientEventListener.register();
    }
}
