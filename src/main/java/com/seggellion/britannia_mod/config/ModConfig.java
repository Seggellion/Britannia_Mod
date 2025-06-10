// ModConfig.java
package com.seggellion.britannia_mod.config;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class ModConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CONFIG_FILE_PATH = "config/britannia_mod.properties";
    private static Properties properties = new Properties();
   // public static final String API_BASE_URL = "http://127.0.0.1:3000/api/";
    public static final String API_BASE_URL = "https://ultimacraft-c079bdcd2cd0.herokuapp.com/api/";
 public static final String SHARD_NAME = "Britannia";
    public static int exampleValue = 10;

    public static void loadConfig() {
        try (FileInputStream input = new FileInputStream(CONFIG_FILE_PATH)) {
            properties.load(input);
            exampleValue = Integer.parseInt(properties.getProperty("exampleValue", "10"));
        } catch (IOException e) {
            LOGGER.warn("Could not load configuration file, using defaults.", e);
        }
    }

    public static void saveConfig() {
        try (FileOutputStream output = new FileOutputStream(CONFIG_FILE_PATH)) {
            properties.setProperty("exampleValue", Integer.toString(exampleValue));
            properties.store(output, "Britannia Mod Configuration");
        } catch (IOException e) {
            LOGGER.error("Could not save configuration file.", e);
        }
    }
}
