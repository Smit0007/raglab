package com.raglab.rcache;

import com.raglab.rcache.command.CommandDispatcher;
import com.raglab.rcache.config.RCacheConfig;
import com.raglab.rcache.server.RCacheServer;
import com.raglab.rcache.storage.RCacheStorageEngine;

import java.io.IOException;

/**
 * Main entry point for the standalone RCache Datastore server daemon.
 */
public class RCacheApplication {

    public static void main(String[] args) {
        RCacheConfig config = RCacheConfig.defaultConfig();
        System.out.println("Starting RCache server daemon on port " + config.port() + "...");

        RCacheStorageEngine storageEngine = new RCacheStorageEngine();
        CommandDispatcher dispatcher = new CommandDispatcher(storageEngine);
        RCacheServer server = new RCacheServer(config.bindAddress(), config.port(), dispatcher);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("RCache server daemon shutting down...");
            server.close();
            storageEngine.close();
        }));

        try {
            server.start();
            System.out.println("RCache server daemon ready on " + config.bindAddress() + ":" + config.port());

            while (server.isRunning() && !Thread.currentThread().isInterrupted()) {
                Thread.sleep(1000);
            }
        } catch (IOException e) {
            System.err.println("RCache server startup error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            server.close();
            storageEngine.close();
            System.out.println("RCache server stopped.");
        }
    }
}
