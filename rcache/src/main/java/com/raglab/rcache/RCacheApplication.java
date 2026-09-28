package com.raglab.rcache;

import com.raglab.rcache.config.RCacheConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;

/**
 * Main entry point for the standalone RCache Datastore server daemon.
 */
public class RCacheApplication {

    public static void main(String[] args) {
        RCacheConfig config = RCacheConfig.defaultConfig();
        System.out.println("Starting RCache server foundation on port " + config.port() + "...");

        try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
            serverChannel.bind(new InetSocketAddress(config.bindAddress(), config.port()));
            System.out.println("RCache foundation TCP listener ready on " + config.bindAddress() + ":" + config.port());

            // Foundation listener loop (listens until shutdown)
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(1000);
            }
        } catch (IOException e) {
            System.err.println("RCache server error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("RCache server shutting down.");
        }
    }
}
